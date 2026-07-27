package com.dropsync.data.workout

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dropsync.core.database.DropSyncDatabase
import com.dropsync.core.database.RoomTransactionRunner
import com.dropsync.core.database.entity.ExerciseEntity
import com.dropsync.core.database.entity.SetRoleEntity
import com.dropsync.core.database.entity.SongEntity
import com.dropsync.core.model.PrType
import com.dropsync.core.model.SessionStatus
import com.dropsync.core.model.SetRole
import com.dropsync.core.testing.FakeClock
import com.dropsync.core.testing.TestDispatcherProvider
import com.dropsync.domain.workout.SegmentInput
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Repository-Tests gegen eine echte In-Memory-Room-DB: prueft damit
 * auch die SQL der qualifizierten Historie (Bauplan 5.4, Schritt 10).
 */
@RunWith(AndroidJUnit4::class)
class WorkoutRepositoryImplTest {
    private lateinit var db: DropSyncDatabase
    private lateinit var repository: WorkoutRepositoryImpl
    private val clock = FakeClock(initialEpochMillis = 1_700_000_000_000)

    private var exerciseId: Long = 0

    @Before
    fun setUp() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            db =
                Room
                    .inMemoryDatabaseBuilder(context, DropSyncDatabase::class.java)
                    .build()
            repository =
                WorkoutRepositoryImpl(
                    workoutDao = db.workoutDao(),
                    routineDao = db.routineDao(),
                    transactionRunner = RoomTransactionRunner(db),
                    clock = clock,
                    dispatchers = TestDispatcherProvider(),
                )
            // Lookup-Tabelle fuellen, die produktiv der ExerciseSeeder liefert
            // (FK set_clusters.set_role -> set_roles.id, RESTRICT).
            db.exerciseDao().insertSetRolesIgnoring(SetRole.entries.map { SetRoleEntity(it.name) })
            exerciseId =
                db.exerciseDao().insertExerciseIgnoring(
                    ExerciseEntity(
                        canonicalName = "barbell_back_squat",
                        kind = "STRENGTH",
                        equipment = "BARBELL",
                        isCustom = false,
                        isArchived = false,
                    ),
                )
        }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun startSessionWithExercise(): Pair<Long, Long> {
        val sessionId = (repository.startSession(null, null) as com.dropsync.core.common.AppResult.Success).value
        val sessionExerciseId =
            (repository.addExercise(sessionId, exerciseId, null) as com.dropsync.core.common.AppResult.Success).value
        return sessionId to sessionExerciseId
    }

    @Test
    fun `satzabschluss speichert segmente cluster und prs atomar`() =
        runTest {
            val (sessionId, sessionExerciseId) = startSessionWithExercise()

            // Abnahme Schritt 10: 30 kg pro Hand, 10 Wdh, Multiplikator 2.
            val result =
                repository.completeCluster(
                    sessionExerciseId,
                    SetRole.WORKING,
                    listOf(SegmentInput(30_000, 2, 10)),
                    note = null,
                )
            assertTrue(result is com.dropsync.core.common.AppResult.Success)

            val records = db.workoutDao().getPersonalRecordsForExercise(exerciseId)
            val volumePr = records.single { it.type == PrType.HIGHEST_SESSION_VOLUME.name }
            assertEquals(600_000, volumePr.valueLong) // 600 kg
            assertEquals(sessionId, volumePr.achievedSessionId)
            val loadPr = records.single { it.type == PrType.HIGHEST_LOAD.name }
            assertEquals(60_000, loadPr.valueLong) // 2 x 30 kg effektiv
        }

    @Test
    fun `dropset ist ein arbeitsset mit summiertem volumen`() =
        runTest {
            val (_, sessionExerciseId) = startSessionWithExercise()

            repository.completeCluster(
                sessionExerciseId,
                SetRole.WORKING,
                listOf(
                    SegmentInput(80_000, 1, 8),
                    SegmentInput(60_000, 1, 6),
                    SegmentInput(40_000, 1, 10),
                ),
                note = null,
            )

            val clusters = db.workoutDao().getClustersForSessionExercise(sessionExerciseId)
            assertEquals(1, clusters.size) // genau ein Arbeitsset
            assertEquals(3, db.workoutDao().countSegments(clusters.single().id))

            val volumePr =
                db
                    .workoutDao()
                    .getPersonalRecordsForExercise(exerciseId)
                    .single { it.type == PrType.HIGHEST_SESSION_VOLUME.name }
            assertEquals(80_000L * 8 + 60_000L * 6 + 40_000L * 10, volumePr.valueLong)
        }

    @Test
    fun `gleichstand in spaeterer session erzeugt keine neue pr`() =
        runTest {
            val (firstSessionId, firstExercise) = startSessionWithExercise()
            repository.completeCluster(
                firstExercise,
                SetRole.WORKING,
                listOf(SegmentInput(100_000, 1, 5)),
                null,
            )
            repository.completeSession(firstSessionId)
            clock.advanceBy(86_400_000) // naechster Tag

            val (_, secondExercise) = startSessionWithExercise()
            repository.completeCluster(
                secondExercise,
                SetRole.WORKING,
                listOf(SegmentInput(100_000, 1, 5)),
                null,
            )

            val loadPr =
                db
                    .workoutDao()
                    .getPersonalRecordsForExercise(exerciseId)
                    .single { it.type == PrType.HIGHEST_LOAD.name }
            // Rekord bleibt bei der ersten Session (Gleichstand).
            assertEquals(firstSessionId, loadPr.achievedSessionId)
        }

    @Test
    fun `warmup qualifiziert nie fuer volumen oder prs`() =
        runTest {
            val (_, sessionExerciseId) = startSessionWithExercise()
            repository.completeCluster(
                sessionExerciseId,
                SetRole.WARMUP,
                listOf(SegmentInput(200_000, 1, 10)),
                null,
            )

            assertTrue(db.workoutDao().getPersonalRecordsForExercise(exerciseId).isEmpty())
        }

    @Test
    fun `discard zaehlt nicht mehr fuer prs loescht aber nichts`() =
        runTest {
            val (sessionId, sessionExerciseId) = startSessionWithExercise()
            repository.completeCluster(
                sessionExerciseId,
                SetRole.WORKING,
                listOf(SegmentInput(120_000, 1, 5)),
                null,
            )
            repository.discardSession(sessionId)
            // Neuberechnung nach Korrektur (10.4).
            repository.recomputeRecords(exerciseId)

            assertTrue(db.workoutDao().getPersonalRecordsForExercise(exerciseId).isEmpty())
            // Daten existieren weiter; nur der Status ist DISCARDED (9.8).
            assertEquals(
                SessionStatus.DISCARDED.name,
                db.workoutDao().getSession(sessionId)!!.status,
            )
            assertEquals(1, db.workoutDao().getClustersForSessionExercise(sessionExerciseId).size)
        }

    @Test
    fun `prefill liefert die werte des letzten abgeschlossenen clusters`() =
        runTest {
            val (_, sessionExerciseId) = startSessionWithExercise()
            repository.completeCluster(
                sessionExerciseId,
                SetRole.WORKING,
                listOf(SegmentInput(80_000, 1, 8)),
                null,
            )
            clock.advanceBy(60_000)
            repository.completeCluster(
                sessionExerciseId,
                SetRole.WORKING,
                listOf(SegmentInput(85_000, 1, 6)),
                null,
            )

            val prefill =
                (
                    repository.lastCompletedClusterPrefill(exerciseId)
                        as com.dropsync.core.common.AppResult.Success
                ).value
            assertEquals(listOf(SegmentInput(85_000, 1, 6)), prefill)
        }

    @Test
    fun `ungueltiger multiplikator wird vor der transaktion abgelehnt`() =
        runTest {
            val (_, sessionExerciseId) = startSessionWithExercise()
            val result =
                repository.completeCluster(
                    sessionExerciseId,
                    SetRole.WORKING,
                    listOf(SegmentInput(80_000, 3, 8)),
                    null,
                )
            assertTrue(result is com.dropsync.core.common.AppResult.Failure)
            assertTrue(db.workoutDao().getClustersForSessionExercise(sessionExerciseId).isEmpty())
        }

    private suspend fun insertSong(mediaStoreId: Long) {
        db.songDao().upsertAll(
            listOf(
                SongEntity(
                    mediaStoreId = mediaStoreId,
                    contentUri = "content://media/external/audio/media/$mediaStoreId",
                    displayName = "track_$mediaStoreId.mp3",
                    relativePath = "Music/",
                    durationMs = 240_000,
                    sizeBytes = 1_000_000,
                    dateModifiedSeconds = 1_700_000_000,
                    title = "Track $mediaStoreId",
                    artist = null,
                    album = null,
                    isAvailable = true,
                ),
            ),
        )
    }

    @Test
    fun `session referenziert den damals laufenden song per snapshot`() =
        runTest {
            // Abnahme Schritt 11: abgeschlossener Satz kann optional den
            // damals laufenden Song referenzieren (11.1, append-only).
            val (sessionId, sessionExerciseId) = startSessionWithExercise()
            insertSong(mediaStoreId = 42)
            repository.completeCluster(
                sessionExerciseId,
                SetRole.WORKING,
                listOf(SegmentInput(80_000, 1, 8)),
                null,
            )

            val id =
                (
                    repository.recordPlaybackSnapshot(
                        sessionId = sessionId,
                        songId = 42,
                        markerId = null,
                        positionMs = 93_000,
                    ) as com.dropsync.core.common.AppResult.Success
                ).value
            assertTrue(id > 0)

            val snapshots =
                (
                    repository.getPlaybackSnapshots(sessionId)
                        as com.dropsync.core.common.AppResult.Success
                ).value
            assertEquals(1, snapshots.size)
            assertEquals(42L, snapshots.single().songId)
            assertEquals(93_000L, snapshots.single().positionMs)
            assertEquals(null, snapshots.single().markerId)
        }

    @Test
    fun `snapshot ohne bekannte session oder song schlaegt fehl`() =
        runTest {
            val (sessionId, _) = startSessionWithExercise()
            // Unbekannte Session.
            assertTrue(
                repository.recordPlaybackSnapshot(9_999, 42, null, 0)
                    is com.dropsync.core.common.AppResult.Failure,
            )
            // Unbekannter Song verletzt den Fremdschluessel (11.1).
            assertTrue(
                repository.recordPlaybackSnapshot(sessionId, 4_242, null, 0)
                    is com.dropsync.core.common.AppResult.Failure,
            )
            val snapshots =
                (
                    repository.getPlaybackSnapshots(sessionId)
                        as com.dropsync.core.common.AppResult.Success
                ).value
            assertTrue(snapshots.isEmpty())
        }
}
