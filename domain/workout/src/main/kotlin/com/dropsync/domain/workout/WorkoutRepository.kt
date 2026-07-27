package com.dropsync.domain.workout

import com.dropsync.core.common.AppResult
import com.dropsync.core.model.SessionStatus
import com.dropsync.core.model.SetRole
import kotlinx.coroutines.flow.Flow

/** Sichtbarer Sessionzustand fuer Features. */
data class WorkoutSessionInfo(
    val id: Long,
    val startedAtEpochMs: Long,
    val status: SessionStatus,
    val title: String?,
)

/**
 * Vertrag des Trainingslogs (Bauplan Schritt 9/10).
 * Implementierung in :data:workout; Satzabschluss und PR-Bestimmung
 * laufen in EINER Transaktion (10.3).
 */
interface WorkoutRepository {
    /** Es gibt hoechstens eine aktive Session (9.8). */
    val activeSession: Flow<WorkoutSessionInfo?>

    suspend fun startSession(
        title: String?,
        fromRoutineId: Long?,
    ): AppResult<Long>

    suspend fun completeSession(sessionId: Long): AppResult<Unit>

    /** Setzt nur status = DISCARDED; loescht keine Daten (9.8). */
    suspend fun discardSession(sessionId: Long): AppResult<Unit>

    suspend fun addExercise(
        sessionId: Long,
        exerciseId: Long,
        supersetGroupId: Long?,
    ): AppResult<Long>

    /**
     * Schliesst einen Satzcluster ab: Segmente, Clusterstatus und die
     * vollstaendig neu berechneten PRs der Uebung in einer Transaktion.
     */
    suspend fun completeCluster(
        sessionExerciseId: Long,
        setRole: SetRole,
        segments: List<SegmentInput>,
        note: String?,
    ): AppResult<Long>

    /**
     * Vollstaendige Neuberechnung nach Korrektur/Loeschung (10.4);
     * entfernt falsche alte PRs.
     */
    suspend fun recomputeRecords(exerciseId: Long): AppResult<Unit>

    /** Werte des letzten abgeschlossenen Clusters derselben Uebung (9.4). */
    suspend fun lastCompletedClusterPrefill(exerciseId: Long): AppResult<List<SegmentInput>>
}
