package com.dropsync.domain.playback

import com.dropsync.core.common.AppResult
import com.dropsync.core.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * Einziger App-Zugang zur Wiedergabe (Bauplan 3.3, ADR-0004).
 *
 * Implementierung in :data:playback ueber MediaController; kein Feature
 * ruft je ExoPlayer.Builder auf (Schritt 5.3). Alle Kommandos wirken auf
 * genau eine Player-Instanz im PlaybackService.
 */
interface PlaybackRepository {
    /** Beobachtbarer Zustand derselben Wiedergabeinstanz fuer alle Screens. */
    val state: Flow<PlaybackState>

    /**
     * Ersetzt die Queue durch [songs], startet bei [startIndex].
     * MediaUnavailable, wenn die Liste leer ist oder der Index nicht passt.
     */
    suspend fun setQueue(
        songs: List<Song>,
        startIndex: Int,
        playWhenReady: Boolean,
    ): AppResult<Unit>

    suspend fun play(): AppResult<Unit>

    suspend fun pause(): AppResult<Unit>

    suspend fun seekTo(positionMs: Long): AppResult<Unit>

    suspend fun skipToNext(): AppResult<Unit>

    suspend fun skipToPrevious(): AppResult<Unit>

    suspend fun setShuffle(enabled: Boolean): AppResult<Unit>

    suspend fun setRepeatMode(mode: RepeatMode): AppResult<Unit>

    /** Zuletzt gespeicherter Wiederherstellungszustand (Schritt 5.5). */
    suspend fun lastPersistedState(): PersistedPlayerState?
}
