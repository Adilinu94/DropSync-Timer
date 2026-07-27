package com.dropsync.data.workout

import com.dropsync.core.common.AppError
import com.dropsync.core.common.AppResult
import com.dropsync.core.common.Clock
import com.dropsync.core.common.DispatcherProvider
import com.dropsync.core.database.TransactionRunner
import com.dropsync.core.database.dao.ExerciseDao
import com.dropsync.core.database.dao.RoutineDao
import com.dropsync.core.database.dao.WorkoutDao
import com.dropsync.core.database.entity.PersonalRecordEntity
import com.dropsync.core.database.entity.PlaybackSnapshotEntity
import com.dropsync.core.database.entity.SessionExerciseEntity
import com.dropsync.core.database.entity.SetClusterEntity
import com.dropsync.core.database.entity.SetSegmentEntity
import com.dropsync.core.database.entity.WorkoutSessionEntity
import com.dropsync.core.model.SessionStatus
import com.dropsync.core.model.SetRole
import com.dropsync.domain.workout.ExerciseInfo
import com.dropsync.domain.workout.PlaybackSnapshotInfo
import com.dropsync.domain.workout.PrCalculator
import com.dropsync.domain.workout.QualifiedSegment
import com.dropsync.domain.workout.RoutineEntry
import com.dropsync.domain.workout.RoutineExpander
import com.dropsync.domain.workout.SegmentInput
import com.dropsync.domain.workout.SessionExerciseInfo
import com.dropsync.domain.workout.WorkoutMath
import com.dropsync.domain.workout.WorkoutRepository
import com.dropsync.domain.workout.WorkoutSessionInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Trainingslog (Bauplan Schritt 9/10).
 *
 * - Satzabschluss + PR-Bestimmung sind EINE Transaktion (10.3).
 * - PRs entstehen immer durch vollstaendige Neuberechnung der Uebung
 *   (10.4); Gleichstand erzeugt nie eine neue PR, weil das frueheste
 *   Segment den Rekord haelt.
 * - Verwerfen setzt nur status = DISCARDED und loescht nichts (9.8).
 */
class WorkoutRepositoryImpl(
    private val workoutDao: WorkoutDao,
    private val routineDao: RoutineDao,
    private val exerciseDao: ExerciseDao,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
) : WorkoutRepository {
    override val activeSession: Flow<WorkoutSessionInfo?> =
        workoutDao.observeActiveSession().map { entity ->
            entity?.let {
                WorkoutSessionInfo(
                    id = it.id,
                    startedAtEpochMs = it.startedAtEpochMs,
                    status = SessionStatus.valueOf(it.status),
                    title = it.title,
                )
            }
        }

    override fun observeExercises(locale: String): Flow<List<ExerciseInfo>> =
        exerciseDao.observeActiveWithNames(locale).map { rows ->
            rows.map { row ->
                ExerciseInfo(
                    id = row.id,
                    slug = row.slug,
                    // Fallback auf den sprachneutralen Slug (Abschnitt 2).
                    displayName = row.displayName ?: row.slug,
                )
            }
        }

    override fun observeSessionExercises(
        sessionId: Long,
        locale: String,
    ): Flow<List<SessionExerciseInfo>> =
        workoutDao.observeSessionExercises(sessionId, locale).map { rows ->
            rows.map { row ->
                SessionExerciseInfo(
                    id = row.id,
                    exerciseId = row.exerciseId,
                    orderIndex = row.orderIndex,
                    displayName = row.displayName ?: row.slug,
                )
            }
        }

    override suspend fun startSession(
        title: String?,
        fromRoutineId: Long?,
    ): AppResult<Long> =
        withContext(dispatchers.io) {
            try {
                transactionRunner {
                    val sessionId =
                        workoutDao.insertSession(
                            WorkoutSessionEntity(
                                startedAtEpochMs = clock.epochMillis(),
                                endedAtEpochMs = null,
                                zoneIdAtStart = ZoneId.systemDefault().id,
                                status = SessionStatus.ACTIVE.name,
                                title = title,
                                notes = null,
                            ),
                        )
                    if (fromRoutineId != null) {
                        // Routinen-Expansion (9.7): Reihenfolge und
                        // Supersetgruppen, keine historischen Gewichte.
                        val entries =
                            routineDao.getExercisesForRoutine(fromRoutineId).map {
                                RoutineEntry(
                                    exerciseId = it.exerciseId,
                                    orderIndex = it.orderIndex,
                                    supersetGroupId = it.supersetGroupId,
                                    targetSets = it.targetSets,
                                    targetRepsMin = it.targetRepsMin,
                                    targetRepsMax = it.targetRepsMax,
                                    restSeconds = it.restSeconds,
                                )
                            }
                        for (planned in RoutineExpander.expand(entries)) {
                            workoutDao.insertSessionExercise(
                                SessionExerciseEntity(
                                    sessionId = sessionId,
                                    exerciseId = planned.exerciseId,
                                    orderIndex = planned.orderIndex,
                                    supersetGroupId = planned.supersetGroupId,
                                ),
                            )
                        }
                    }
                    AppResult.success(sessionId)
                }
            } catch (e: Exception) {
                AppResult.failure(AppError.DatabaseFailure("startSession"))
            }
        }

    override suspend fun completeSession(sessionId: Long): AppResult<Unit> =
        setStatus(sessionId, SessionStatus.COMPLETED)

    override suspend fun discardSession(sessionId: Long): AppResult<Unit> =
        setStatus(sessionId, SessionStatus.DISCARDED)

    private suspend fun setStatus(
        sessionId: Long,
        status: SessionStatus,
    ): AppResult<Unit> =
        withContext(dispatchers.io) {
            try {
                workoutDao.getSession(sessionId)
                    ?: return@withContext AppResult.failure(
                        AppError.DatabaseFailure("Session $sessionId fehlt"),
                    )
                workoutDao.updateSessionStatus(sessionId, status.name, clock.epochMillis())
                AppResult.success(Unit)
            } catch (e: Exception) {
                AppResult.failure(AppError.DatabaseFailure("setStatus"))
            }
        }

    override suspend fun addExercise(
        sessionId: Long,
        exerciseId: Long,
        supersetGroupId: Long?,
    ): AppResult<Long> =
        withContext(dispatchers.io) {
            try {
                val orderIndex = workoutDao.nextExerciseOrderIndex(sessionId)
                val id =
                    workoutDao.insertSessionExercise(
                        SessionExerciseEntity(
                            sessionId = sessionId,
                            exerciseId = exerciseId,
                            orderIndex = orderIndex,
                            supersetGroupId = supersetGroupId,
                        ),
                    )
                AppResult.success(id)
            } catch (e: Exception) {
                AppResult.failure(AppError.DatabaseFailure("addExercise"))
            }
        }

    override suspend fun completeCluster(
        sessionExerciseId: Long,
        setRole: SetRole,
        segments: List<SegmentInput>,
        note: String?,
    ): AppResult<Long> =
        withContext(dispatchers.io) {
            if (segments.isEmpty()) {
                return@withContext AppResult.failure(
                    AppError.Unknown("Cluster ohne Segmente"),
                )
            }
            if (segments.any { !WorkoutMath.isValidLoadMultiplier(it.loadMultiplier) }) {
                return@withContext AppResult.failure(
                    AppError.Unknown("loadMultiplier muss 1 oder 2 sein (5.4)"),
                )
            }
            try {
                transactionRunner {
                    val sessionExercise =
                        workoutDao.getSessionExercise(sessionExerciseId)
                            ?: throw IllegalStateException("SessionExercise fehlt")
                    val clusterId =
                        workoutDao.insertCluster(
                            SetClusterEntity(
                                sessionExerciseId = sessionExerciseId,
                                orderIndex = workoutDao.nextClusterOrderIndex(sessionExerciseId),
                                setRole = setRole.name,
                                isCompleted = false,
                                note = note,
                                completedAtEpochMs = null,
                            ),
                        )
                    workoutDao.insertSegments(
                        segments.mapIndexed { index, segment ->
                            SetSegmentEntity(
                                clusterId = clusterId,
                                segmentIndex = index,
                                externalLoadMilliKgPerImplement = segment.externalLoadMilliKgPerImplement,
                                loadMultiplier = segment.loadMultiplier,
                                reps = segment.reps,
                                durationMs = segment.durationMs,
                                distanceM = segment.distanceM,
                            )
                        },
                    )
                    workoutDao.markClusterCompleted(clusterId, clock.epochMillis())
                    // PR-Bestimmung in derselben Transaktion (10.3) als
                    // vollstaendige Neuberechnung (10.4).
                    recomputeInTransaction(sessionExercise.exerciseId)
                    AppResult.success(clusterId)
                }
            } catch (e: Exception) {
                AppResult.failure(AppError.DatabaseFailure("completeCluster"))
            }
        }

    override suspend fun undoCompleteCluster(clusterId: Long): AppResult<Unit> =
        withContext(dispatchers.io) {
            try {
                transactionRunner {
                    val cluster =
                        workoutDao.getCluster(clusterId)
                            ?: throw IllegalStateException("Cluster $clusterId fehlt")
                    val sessionExercise =
                        workoutDao.getSessionExercise(cluster.sessionExerciseId)
                            ?: throw IllegalStateException("SessionExercise fehlt")
                    workoutDao.deleteSegmentsForCluster(clusterId)
                    workoutDao.deleteCluster(clusterId)
                    // Loeschung erzwingt vollstaendige PR-Neuberechnung (10.4).
                    recomputeInTransaction(sessionExercise.exerciseId)
                }
                AppResult.success(Unit)
            } catch (e: Exception) {
                AppResult.failure(AppError.DatabaseFailure("undoCompleteCluster"))
            }
        }

    override suspend fun recomputeRecords(exerciseId: Long): AppResult<Unit> =
        withContext(dispatchers.io) {
            try {
                transactionRunner { recomputeInTransaction(exerciseId) }
                AppResult.success(Unit)
            } catch (e: Exception) {
                AppResult.failure(AppError.DatabaseFailure("recomputeRecords"))
            }
        }

    override suspend fun lastCompletedClusterPrefill(exerciseId: Long): AppResult<List<SegmentInput>> =
        withContext(dispatchers.io) {
            try {
                val segments =
                    workoutDao.getSegmentsOfLastCompletedCluster(exerciseId).map {
                        SegmentInput(
                            externalLoadMilliKgPerImplement = it.externalLoadMilliKgPerImplement,
                            loadMultiplier = it.loadMultiplier,
                            reps = it.reps,
                            durationMs = it.durationMs,
                            distanceM = it.distanceM,
                        )
                    }
                AppResult.success(segments)
            } catch (e: Exception) {
                AppResult.failure(AppError.DatabaseFailure("lastCompletedClusterPrefill"))
            }
        }

    override suspend fun recordPlaybackSnapshot(
        sessionId: Long,
        songId: Long,
        markerId: Long?,
        positionMs: Long,
    ): AppResult<Long> =
        withContext(dispatchers.io) {
            try {
                // 11.1: append-only; nur Referenzen und Position, nie eine
                // Queuekopie. Fremdschluessel sichern Song/Session-Existenz.
                workoutDao.getSession(sessionId)
                    ?: return@withContext AppResult.failure(
                        AppError.DatabaseFailure("Session $sessionId fehlt fuer Snapshot"),
                    )
                val id =
                    workoutDao.insertPlaybackSnapshot(
                        PlaybackSnapshotEntity(
                            sessionId = sessionId,
                            songId = songId,
                            markerId = markerId,
                            positionMs = positionMs,
                            capturedAtEpochMs = clock.epochMillis(),
                        ),
                    )
                AppResult.success(id)
            } catch (e: Exception) {
                AppResult.failure(AppError.DatabaseFailure("recordPlaybackSnapshot"))
            }
        }

    override suspend fun getPlaybackSnapshots(sessionId: Long): AppResult<List<PlaybackSnapshotInfo>> =
        withContext(dispatchers.io) {
            try {
                val snapshots =
                    workoutDao.getPlaybackSnapshotsForSession(sessionId).map {
                        PlaybackSnapshotInfo(
                            id = it.id,
                            sessionId = it.sessionId,
                            songId = it.songId,
                            markerId = it.markerId,
                            positionMs = it.positionMs,
                            capturedAtEpochMs = it.capturedAtEpochMs,
                        )
                    }
                AppResult.success(snapshots)
            } catch (e: Exception) {
                AppResult.failure(AppError.DatabaseFailure("getPlaybackSnapshots"))
            }
        }

    /** Muss innerhalb einer laufenden Transaktion aufgerufen werden. */
    private suspend fun recomputeInTransaction(exerciseId: Long) {
        val history =
            workoutDao.getQualifiedSegments(exerciseId).map {
                QualifiedSegment(
                    sessionId = it.sessionId,
                    sessionStartedAtEpochMs = it.sessionStartedAtEpochMs,
                    clusterId = it.clusterId,
                    completedAtEpochMs = it.completedAtEpochMs,
                    loadMilliKg = it.loadMilliKg,
                    loadMultiplier = it.loadMultiplier,
                    reps = it.reps,
                )
            }
        val records = PrCalculator.computeAll(history)
        workoutDao.deletePersonalRecordsForExercise(exerciseId)
        if (records.isNotEmpty()) {
            workoutDao.insertPersonalRecords(
                records.map {
                    PersonalRecordEntity(
                        exerciseId = exerciseId,
                        type = it.type.name,
                        achievedSessionId = it.achievedSessionId,
                        achievedClusterId = it.achievedClusterId,
                        valueLong = it.valueLong,
                        valueUnit = it.valueUnit.name,
                        comparableLoadMilliKg = it.comparableLoadMilliKg,
                        achievedAtEpochMs = it.achievedAtEpochMs,
                    )
                },
            )
        }
    }
}
