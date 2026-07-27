package com.dropsync.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dropsync.core.database.dao.ExerciseDao
import com.dropsync.core.database.dao.MarkerDao
import com.dropsync.core.database.dao.RoutineDao
import com.dropsync.core.database.dao.SongDao
import com.dropsync.core.database.dao.TimerPresetDao
import com.dropsync.core.database.dao.WorkoutDao
import com.dropsync.core.database.entity.ExerciseEntity
import com.dropsync.core.database.entity.ExerciseMuscleEntity
import com.dropsync.core.database.entity.ExerciseNameEntity
import com.dropsync.core.database.entity.MarkerSongLinkEntity
import com.dropsync.core.database.entity.MuscleGroupEntity
import com.dropsync.core.database.entity.PersonalRecordEntity
import com.dropsync.core.database.entity.PlaybackSnapshotEntity
import com.dropsync.core.database.entity.RoutineEntity
import com.dropsync.core.database.entity.RoutineExerciseEntity
import com.dropsync.core.database.entity.SessionExerciseEntity
import com.dropsync.core.database.entity.SetClusterEntity
import com.dropsync.core.database.entity.SetRoleEntity
import com.dropsync.core.database.entity.SetSegmentEntity
import com.dropsync.core.database.entity.SongEntity
import com.dropsync.core.database.entity.SongMarkerEntity
import com.dropsync.core.database.entity.TimerPresetEntity
import com.dropsync.core.database.entity.WorkoutSessionEntity

/**
 * Lokale Datenbank gemaess Bauplan Abschnitt 6.
 *
 * - Alle Zeitpunkte: UTC-Epoch-Millis (Long).
 * - Alle Gewichte/Volumen: ganze Millikilogramm (Long); Double/BigDecimal
 *   sind in Entities und Abfragen verboten (Schritt 3.2).
 * - Enums werden als stabile Strings gespeichert; keine Ordinals, keine
 *   JSON-Listen in Entities.
 * - Jede Schemaversion wird exportiert (schemas/) und per Migrationstest
 *   geprueft; destruktive Migration ist in Release verboten (Schritt 3.5).
 */
@Database(
    entities = [
        SongEntity::class,
        SongMarkerEntity::class,
        MarkerSongLinkEntity::class,
        TimerPresetEntity::class,
        WorkoutSessionEntity::class,
        ExerciseEntity::class,
        ExerciseNameEntity::class,
        MuscleGroupEntity::class,
        SetRoleEntity::class,
        ExerciseMuscleEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        SessionExerciseEntity::class,
        SetClusterEntity::class,
        SetSegmentEntity::class,
        PersonalRecordEntity::class,
        PlaybackSnapshotEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class DropSyncDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    abstract fun markerDao(): MarkerDao

    abstract fun timerPresetDao(): TimerPresetDao

    abstract fun exerciseDao(): ExerciseDao

    abstract fun routineDao(): RoutineDao

    abstract fun workoutDao(): WorkoutDao

    companion object {
        const val NAME = "dropsync.db"
    }
}
