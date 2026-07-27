package com.dropsync.domain.playback

/** Wiederholmodus ohne Media3-Typen (ADR-0004); stabile Namen. */
enum class RepeatMode { OFF, ONE, ALL }

/**
 * Beobachtbarer Wiedergabezustand fuer Features (Bauplan 3.3).
 * Song-Identitaet ist immer die MediaStore-ID (5.1).
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentSongId: Long? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val queueSongIds: List<Long> = emptyList(),
)

/**
 * Persistierter Wiederherstellungszustand (Schritt 5.5): Queue, Shuffle,
 * Repeat, letzter Song und Position werden nach jeder relevanten
 * Aenderung gespeichert. Automatisches Playback-Resume ueber den
 * Media3-Callback bleibt in Version 1 deaktiviert, bis es separat
 * implementiert und getestet ist.
 */
data class PersistedPlayerState(
    val queueSongIds: List<Long>,
    val currentSongId: Long?,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: RepeatMode,
)
