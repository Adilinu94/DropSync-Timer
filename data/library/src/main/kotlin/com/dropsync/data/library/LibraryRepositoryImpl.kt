package com.dropsync.data.library

import com.dropsync.core.common.AppError
import com.dropsync.core.common.AppResult
import com.dropsync.core.common.DispatcherProvider
import com.dropsync.core.database.TransactionRunner
import com.dropsync.core.database.dao.SongDao
import com.dropsync.core.model.Song
import com.dropsync.domain.library.LibraryRepository
import com.dropsync.domain.library.LibraryScanResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Bibliotheksabgleich gegen MediaStore (Bauplan Schritt 4).
 *
 * - Identitaet ist immer die MediaStore-ID (5.1).
 * - Ohne Aenderung des MediaStore-Stands laeuft kein Vollscan (4.3).
 * - Verschwundene Songs werden nur als nicht verfuegbar markiert,
 *   nie geloescht (4.4); Marker und Historie bleiben erhalten.
 * - Der extern importierte SHA-256 bleibt beim Rescan erhalten.
 */
class LibraryRepositoryImpl(
    private val gateway: MediaStoreGateway,
    private val songDao: SongDao,
    private val scanStateStore: ScanStateStore,
    private val transactionRunner: TransactionRunner,
    private val dispatchers: DispatcherProvider,
) : LibraryRepository {
    override val songs: Flow<List<Song>> =
        songDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override val availableSongs: Flow<List<Song>> =
        songDao.observeAvailable().map { entities -> entities.map { it.toDomain() } }

    override suspend fun refreshLibrary(force: Boolean): AppResult<LibraryScanResult> =
        withContext(dispatchers.io) {
            if (!gateway.hasAudioPermission()) {
                // Kein stiller leerer Screen: Fehler ist explizit (Schritt 4.2).
                return@withContext AppResult.failure(
                    AppError.PermissionDenied(gateway.requiredPermission()),
                )
            }
            try {
                val generation = gateway.currentGeneration()
                if (!force && generation == scanStateStore.lastGeneration()) {
                    val total = songDao.getAllOnce().size
                    return@withContext AppResult.success(
                        LibraryScanResult(
                            skippedBecauseUnchanged = true,
                            totalSongs = total,
                            newOrUpdatedSongs = 0,
                            markedUnavailable = 0,
                        ),
                    )
                }

                val scanned = gateway.queryAudio()
                val existing = songDao.getAllOnce().associateBy { it.mediaStoreId }
                // Extern gelieferte Hashes ueberleben jeden Rescan.
                val entities =
                    scanned.map { song ->
                        song.toEntity(knownSha256 = existing[song.mediaStoreId]?.knownSha256)
                    }
                val changed = entities.count { existing[it.mediaStoreId] != it }
                val presentIds = entities.map { it.mediaStoreId }
                val presentIdSet = presentIds.toSet()
                val toUnavailable =
                    existing.values.count { it.isAvailable && it.mediaStoreId !in presentIdSet }

                transactionRunner {
                    songDao.upsertAll(entities)
                    songDao.markMissingAsUnavailable(presentIds)
                }
                scanStateStore.setLastGeneration(generation)

                AppResult.success(
                    LibraryScanResult(
                        skippedBecauseUnchanged = false,
                        totalSongs = entities.size,
                        newOrUpdatedSongs = changed,
                        markedUnavailable = toUnavailable,
                    ),
                )
            } catch (e: SecurityException) {
                AppResult.failure(AppError.PermissionDenied(gateway.requiredPermission()))
            } catch (e: Exception) {
                AppResult.failure(AppError.DatabaseFailure("refreshLibrary"))
            }
        }

    override suspend fun getSong(mediaStoreId: Long): AppResult<Song> =
        withContext(dispatchers.io) {
            val entity = songDao.getById(mediaStoreId)
            if (entity == null) {
                AppResult.failure(AppError.MediaUnavailable(mediaStoreId))
            } else {
                AppResult.success(entity.toDomain())
            }
        }

    override suspend fun markUnavailable(mediaStoreId: Long): AppResult<Unit> =
        withContext(dispatchers.io) {
            try {
                songDao.setAvailability(mediaStoreId, isAvailable = false)
                AppResult.success(Unit)
            } catch (e: Exception) {
                AppResult.failure(AppError.DatabaseFailure("markUnavailable"))
            }
        }
}
