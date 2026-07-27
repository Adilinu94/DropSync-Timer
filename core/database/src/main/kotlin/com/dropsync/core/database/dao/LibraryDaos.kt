package com.dropsync.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.dropsync.core.database.entity.MarkerSongLinkEntity
import com.dropsync.core.database.entity.SongEntity
import com.dropsync.core.database.entity.SongMarkerEntity
import com.dropsync.core.database.entity.TimerPresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    /** Erneuter Scan aktualisiert vorhandene Zeilen ueber mediaStoreId (5.1). */
    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("SELECT * FROM songs WHERE media_store_id = :mediaStoreId")
    suspend fun getById(mediaStoreId: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE is_available = 1 ORDER BY title COLLATE NOCASE")
    fun observeAvailable(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<SongEntity>>

    /**
     * Nicht mehr im MediaStore vorhandene Songs bleiben mit
     * isAvailable = false erhalten (Schritt 4.4); Historie und Marker
     * werden nie geloescht.
     */
    @Query("UPDATE songs SET is_available = 0 WHERE media_store_id NOT IN (:presentIds)")
    suspend fun markMissingAsUnavailable(presentIds: List<Long>)

    @Query("UPDATE songs SET is_available = :isAvailable WHERE media_store_id = :mediaStoreId")
    suspend fun setAvailability(
        mediaStoreId: Long,
        isAvailable: Boolean,
    )
}

@Dao
interface MarkerDao {
    @Insert
    suspend fun insert(marker: SongMarkerEntity): Long

    @Query("SELECT * FROM song_markers WHERE id = :id")
    suspend fun getById(id: Long): SongMarkerEntity?

    @Query("SELECT * FROM song_markers WHERE source_fingerprint = :fingerprint")
    suspend fun getByFingerprint(fingerprint: String): List<SongMarkerEntity>

    @Query(
        "UPDATE song_markers SET label = :label, position_ms = :positionMs, is_enabled = :isEnabled WHERE id = :id",
    )
    suspend fun update(
        id: Long,
        label: String,
        positionMs: Long,
        isEnabled: Boolean,
    )

    /** Marker ohne Linkzeile sind "nicht zugeordnet" (5.1). */
    @Query(
        "SELECT m.* FROM song_markers m LEFT JOIN marker_song_links l ON l.marker_id = m.id WHERE l.id IS NULL",
    )
    fun observeUnmatched(): Flow<List<SongMarkerEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLink(link: MarkerSongLinkEntity): Long

    @Query("SELECT * FROM marker_song_links WHERE marker_id = :markerId")
    suspend fun getLinkForMarker(markerId: Long): MarkerSongLinkEntity?

    @Query(
        "SELECT m.* FROM song_markers m INNER JOIN marker_song_links l ON l.marker_id = m.id " +
            "WHERE l.song_id = :songId AND m.is_enabled = 1 ORDER BY m.position_ms",
    )
    suspend fun getEnabledMarkersForSong(songId: Long): List<SongMarkerEntity>
}

@Dao
interface TimerPresetDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(preset: TimerPresetEntity): Long

    @Query("SELECT * FROM timer_presets ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<TimerPresetEntity>>

    @Query("DELETE FROM timer_presets WHERE id = :id")
    suspend fun delete(id: Long)
}
