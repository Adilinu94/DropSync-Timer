package com.dropsync.core.database.dao

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
}

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

    @Query("SELECT COUNT(*) FROM set_segments WHERE cluster_id = :clusterId")
    suspend fun countSegments(clusterId: Long): Int

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
