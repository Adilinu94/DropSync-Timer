package com.dropsync.data.audio

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dropsync.core.common.AppResult
import com.dropsync.core.common.Clock
import com.dropsync.core.database.dao.SongDao
import com.dropsync.core.database.dao.TrackAnalysisDao
import com.dropsync.core.database.entity.SongEntity
import com.dropsync.core.database.entity.TrackAnalysisEntity
import com.dropsync.core.model.Song
import com.dropsync.domain.audio.TrackAnalysis
import com.dropsync.domain.audio.TrackAnalysisRepository
import com.dropsync.domain.audio.TrackAnalyzer
import com.dropsync.domain.audio.WaveformCodec
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Cache-Zugang zur Track-Analyse (Marker/Waveform-Plan Phase 2): liest
 * `track_analysis` und stoesst bei Cache-Miss einen aufschiebbaren,
 * ueber den Work-Namen `track_analysis_<songId>` deduplizierten
 * OneTimeWorkRequest an (WorkManager nur fuer aufschiebbare Aufgaben,
 * nie Timer).
 */
class TrackAnalysisRepositoryImpl(
    private val context: Context,
    private val trackAnalysisDao: TrackAnalysisDao,
) : TrackAnalysisRepository {
    override fun observeAnalysis(songId: Long): Flow<TrackAnalysis?> =
        trackAnalysisDao.observeBySongId(songId).map { entity ->
            entity
                ?.takeIf { it.analyzerVersion == WaveformCodec.ANALYZER_VERSION }
                ?.let {
                    TrackAnalysis(
                        waveformBuckets = WaveformCodec.unpack(it.waveformData),
                        onsetCandidatesMs = emptyList(),
                    )
                }
        }

    override suspend fun requestAnalysis(song: Song) {
        val cached = trackAnalysisDao.getBySongId(song.mediaStoreId)
        if (cached != null && cached.analyzerVersion == WaveformCodec.ANALYZER_VERSION) return
        val request =
            OneTimeWorkRequestBuilder<TrackAnalysisWorker>()
                .setInputData(workDataOf(TrackAnalysisWorker.KEY_SONG_ID to song.mediaStoreId))
                .build()
        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(
                "track_analysis_${song.mediaStoreId}",
                ExistingWorkPolicy.KEEP,
                request,
            )
    }
}

/**
 * Fuehrt einen Analysedurchgang fuer genau einen Song aus und schreibt
 * das Ergebnis in den Cache. Abhaengigkeiten kommen ueber einen
 * Hilt-EntryPoint, damit kein zusaetzliches hilt-work-Artefakt noetig ist.
 */
class TrackAnalysisWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun trackAnalyzer(): TrackAnalyzer

        fun trackAnalysisDao(): TrackAnalysisDao

        fun songDao(): SongDao

        fun clock(): Clock
    }

    override suspend fun doWork(): Result {
        val songId = inputData.getLong(KEY_SONG_ID, -1L)
        if (songId <= 0L) return Result.failure()
        val deps = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
        val entity = deps.songDao().getById(songId) ?: return Result.failure()

        return when (val result = deps.trackAnalyzer().analyze(entity.toSong())) {
            is AppResult.Success -> {
                val buckets = result.value.waveformBuckets
                deps.trackAnalysisDao().upsert(
                    TrackAnalysisEntity(
                        songId = songId,
                        waveformData = WaveformCodec.pack(buckets),
                        bucketCount = buckets.size,
                        analyzerVersion = WaveformCodec.ANALYZER_VERSION,
                        analyzedAtEpochMs = deps.clock().epochMillis(),
                    ),
                )
                Result.success()
            }
            // Kein Retry: ein Format ohne Plattformdecoder scheitert auch
            // beim naechsten Versuch; die UI faellt auf die Zeitleiste zurueck.
            is AppResult.Failure -> Result.failure()
        }
    }

    companion object {
        const val KEY_SONG_ID = "song_id"
    }
}

private fun SongEntity.toSong(): Song =
    Song(
        mediaStoreId = mediaStoreId,
        contentUri = contentUri,
        displayName = displayName,
        relativePath = relativePath,
        durationMs = durationMs,
        sizeBytes = sizeBytes,
        dateModifiedSeconds = dateModifiedSeconds,
        title = title,
        artist = artist,
        album = album,
        genre = genre,
        isAvailable = isAvailable,
    )
