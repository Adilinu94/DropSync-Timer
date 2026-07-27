package com.dropsync.core.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.dropsync.core.database.entity.ExerciseEntity
import com.dropsync.core.database.entity.ExerciseMuscleEntity
import com.dropsync.core.database.entity.ExerciseNameEntity
import com.dropsync.core.database.entity.MuscleGroupEntity
import com.dropsync.core.database.entity.PersonalRecordEntity
import com.dropsync.core.database.entity.PlaybackSnapshotEntity
import com.dropsync.core.database.entity.RoutineEntity
import com.dropsync.core.database.entity.RoutineExerciseEntity
import com.dropsync.core.database.entity.SessionExerciseEntity
import com.dropsync.core.database.entity.SetClusterEntity
import com.dropsync.core.database.entity.SetRoleEntity
import com.dropsync.core.database.entity.SetSegmentEntity
import com.dropsync.core.database.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    /** Seed ist idempotent; Benutzerdaten werden nie ueberschrieben (3.6). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExerciseIgnoring(exercise: ExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNamesIgnoring(names: List<ExerciseNameEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMusclesIgnoring(muscles: List<ExerciseMuscleEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMuscleGroupsIgnoring(groups: List<MuscleGroupEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSetRolesIgnoring(roles: List<SetRoleEntity>)

    @Query("SELECT * FROM exercises WHERE canonical_name = :canonicalName")
    suspend fun getByCanonicalName(canonicalName: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE is_archived = 0 ORDER BY canonical_name")
    fun observeActive(): Flow<List<ExerciseEntity>>

    @Query("SELECT COUNT(*) FROM exercises WHERE is_custom = 0")
    suspend fun countStandardExercises(): Int

    @Query(
        "SELECT display_name FROM exercise_names WHERE exercise_id = :exerciseId AND locale = :locale",
    )
    suspend fun getDisplayName(
        exerciseId: Long,
        locale: String,
    ): String?

    /**
     * Aktive Uebungen mit lokalisiertem Namen (Schritt 9.1); ohne
     * Uebersetzung faellt die UI auf den sprachneutralen Slug zurueck.
     */
    @Query(
        "SELECT e.id AS id, e.canonical_name AS slug, n.display_name AS display_name " +
            "FROM exercises e " +
            "LEFT JOIN exercise_names n ON n.exercise_id = e.id AND n.locale = :locale " +
            "WHERE e.is_archived = 0 " +
            "ORDER BY COALESCE(n.display_name, e.canonical_name) COLLATE NOCASE",
    )
    fun observeActiveWithNames(locale: String): Flow<List<ExerciseNameRow>>
}

/** Zeile der Uebungsauswahl (Query in [ExerciseDao]). */
data class ExerciseNameRow(
    @ColumnInfo(name = "id")
    val id: Long,
    @ColumnInfo(name = "slug")
    val slug: String,
    @ColumnInfo(name = "display_name")
    val displayName: String?,
)

@Dao
interface RoutineDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Insert
    suspend fun insertRoutineExercises(entries: List<RoutineExerciseEntity>)

    @Query("SELECT * FROM routines WHERE is_archived = 0 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routine_exercises WHERE routine_id = :routineId ORDER BY order_index")
    suspend fun getExercisesForRoutine(routineId: Long): List<RoutineExerciseEntity>
}

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSession(id: Long): WorkoutSessionEntity?

    @Query("SELECT * FROM session_exercises WHERE id = :id")
    suspend fun getSessionExercise(id: Long): SessionExerciseEntity?

    /** Uebungen der Session in stabiler Reihenfolge (9.3), mit Namen. */
    @Query(
        "SELECT se.id AS id, se.exercise_id AS exercise_id, se.order_index AS order_index, " +
            "e.canonical_name AS slug, n.display_name AS display_name " +
            "FROM session_exercises se " +
            "INNER JOIN exercises e ON e.id = se.exercise_id " +
            "LEFT JOIN exercise_names n ON n.exercise_id = e.id AND n.locale = :locale " +
            "WHERE se.session_id = :sessionId ORDER BY se.order_index",
    )
    fun observeSessionExercises(
        sessionId: Long,
        locale: String,
    ): Flow<List<SessionExerciseRow>>

    @Query("SELECT COALESCE(MAX(order_index) + 1, 0) FROM session_exercises WHERE session_id = :sessionId")
    suspend fun nextExerciseOrderIndex(sessionId: Long): Int

    @Query(
        "SELECT COALESCE(MAX(order_index) + 1, 0) FROM set_clusters WHERE session_exercise_id = :sessionExerciseId",
    )
    suspend fun nextClusterOrderIndex(sessionExerciseId: Long): Int

    @Query("SELECT * FROM workout_sessions WHERE status = 'ACTIVE' LIMIT 1")
    fun observeActiveSession(): Flow<WorkoutSessionEntity?>

    @Query("UPDATE workout_sessions SET status = :status, ended_at_epoch_ms = :endedAtEpochMs WHERE id = :id")
    suspend fun updateSessionStatus(
        id: Long,
        status: String,
        endedAtEpochMs: Long?,
    )

    @Insert
    suspend fun insertSessionExercise(entry: SessionExerciseEntity): Long

    @Insert
    suspend fun insertCluster(cluster: SetClusterEntity): Long

    @Insert
    suspend fun insertSegments(segments: List<SetSegmentEntity>)

    @Query(
        "UPDATE set_clusters SET is_completed = 1, completed_at_epoch_ms = :completedAtEpochMs WHERE id = :clusterId",
    )
    suspend fun markClusterCompleted(
        clusterId: Long,
        completedAtEpochMs: Long,
    )

    @Insert
    suspend fun insertPersonalRecords(records: List<PersonalRecordEntity>)

    @Query("DELETE FROM personal_records WHERE exercise_id = :exerciseId")
    suspend fun deletePersonalRecordsForExercise(exerciseId: Long)

    @Query("SELECT * FROM set_clusters WHERE session_exercise_id = :sessionExerciseId ORDER BY order_index")
    suspend fun getClustersForSessionExercise(sessionExerciseId: Long): List<SetClusterEntity>

    @Query("SELECT * FROM set_segments WHERE cluster_id = :clusterId ORDER BY segment_index")
    suspend fun getSegmentsForCluster(clusterId: Long): List<SetSegmentEntity>

    @Query("SELECT * FROM personal_records WHERE exercise_id = :exerciseId")
    suspend fun getPersonalRecordsForExercise(exerciseId: Long): List<PersonalRecordEntity>

    @Query("SELECT * FROM set_clusters WHERE id = :clusterId")
    suspend fun getCluster(clusterId: Long): SetClusterEntity?

    /** Segmente haengen per CASCADE am Cluster; explizit fuer Klarheit. */
    @Query("DELETE FROM set_segments WHERE cluster_id = :clusterId")
    suspend fun deleteSegmentsForCluster(clusterId: Long)

    @Query("DELETE FROM set_clusters WHERE id = :clusterId")
    suspend fun deleteCluster(clusterId: Long)

    @Query("SELECT COUNT(*) FROM set_segments WHERE cluster_id = :clusterId")
    suspend fun countSegments(clusterId: Long): Int

    /**
     * Append-only-Musikreferenz einer Session (Schritt 11.1); es gibt
     * bewusst kein Update und keine Queuekopie.
     */
    @Insert
    suspend fun insertPlaybackSnapshot(snapshot: PlaybackSnapshotEntity): Long

    @Query(
        "SELECT * FROM playback_snapshots WHERE session_id = :sessionId " +
            "ORDER BY captured_at_epoch_ms, id",
    )
    suspend fun getPlaybackSnapshotsForSession(sessionId: Long): List<PlaybackSnapshotEntity>

    /**
     * Qualifizierte Historie einer Uebung (Bauplan 5.4/1): WORKING oder
     * FAILURE, abgeschlossen, STRENGTH, reps > 0, Last >= 0; DISCARDED
     * zaehlt nie. Grundlage jeder vollstaendigen PR-Neuberechnung.
     */
    @Query(
        "SELECT ws.id AS session_id, ws.started_at_epoch_ms AS session_started_at, " +
            "c.id AS cluster_id, c.completed_at_epoch_ms AS completed_at, " +
            "s.external_load_milli_kg_per_implement AS load_milli_kg, " +
            "s.load_multiplier AS load_multiplier, s.reps AS reps " +
            "FROM set_segments s " +
            "INNER JOIN set_clusters c ON c.id = s.cluster_id " +
            "INNER JOIN session_exercises se ON se.id = c.session_exercise_id " +
            "INNER JOIN workout_sessions ws ON ws.id = se.session_id " +
            "INNER JOIN exercises e ON e.id = se.exercise_id " +
            "WHERE se.exercise_id = :exerciseId AND c.is_completed = 1 " +
            "AND c.set_role IN ('WORKING','FAILURE') AND e.kind = 'STRENGTH' " +
            "AND s.reps > 0 AND s.external_load_milli_kg_per_implement >= 0 " +
            "AND ws.status != 'DISCARDED' AND c.completed_at_epoch_ms IS NOT NULL",
    )
    suspend fun getQualifiedSegments(exerciseId: Long): List<QualifiedSegmentRow>

    /** Segmente des zuletzt abgeschlossenen Clusters der Uebung (9.4). */
    @Query(
        "SELECT * FROM set_segments WHERE cluster_id = (" +
            "SELECT c.id FROM set_clusters c " +
            "INNER JOIN session_exercises se ON se.id = c.session_exercise_id " +
            "WHERE se.exercise_id = :exerciseId AND c.is_completed = 1 " +
            "ORDER BY c.completed_at_epoch_ms DESC LIMIT 1) " +
            "ORDER BY segment_index",
    )
    suspend fun getSegmentsOfLastCompletedCluster(exerciseId: Long): List<SetSegmentEntity>

    /**
     * Satzabschluss als eine Transaktion (Schritt 3.4): Segmente,
     * Clusterstatus und neue PRs werden zusammen gespeichert oder gar
     * nicht. Wirft eine Ausnahme, wenn ein Teil scheitert; Room rollt
     * dann alles zurueck.
     */
    @Transaction
    suspend fun completeClusterTransaction(
        clusterId: Long,
        completedAtEpochMs: Long,
        segments: List<SetSegmentEntity>,
        newRecords: List<PersonalRecordEntity>,
    ) {
        insertSegments(segments)
        markClusterCompleted(clusterId, completedAtEpochMs)
        if (newRecords.isNotEmpty()) {
            insertPersonalRecords(newRecords)
        }
    }
}

/** Zeile der Uebungen einer Session (Query in [WorkoutDao]). */
data class SessionExerciseRow(
    @ColumnInfo(name = "id")
    val id: Long,
    @ColumnInfo(name = "exercise_id")
    val exerciseId: Long,
    @ColumnInfo(name = "order_index")
    val orderIndex: Int,
    @ColumnInfo(name = "slug")
    val slug: String,
    @ColumnInfo(name = "display_name")
    val displayName: String?,
)

/** Zeile der qualifizierten Historie (Query in [WorkoutDao]). */
data class QualifiedSegmentRow(
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    @ColumnInfo(name = "session_started_at")
    val sessionStartedAtEpochMs: Long,
    @ColumnInfo(name = "cluster_id")
    val clusterId: Long,
    @ColumnInfo(name = "completed_at")
    val completedAtEpochMs: Long,
    @ColumnInfo(name = "load_milli_kg")
    val loadMilliKg: Long,
    @ColumnInfo(name = "load_multiplier")
    val loadMultiplier: Int,
    @ColumnInfo(name = "reps")
    val reps: Int,
)
