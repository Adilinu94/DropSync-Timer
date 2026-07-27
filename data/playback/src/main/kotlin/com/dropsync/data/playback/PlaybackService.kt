package com.dropsync.data.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Einziger Wiedergabedienst der App (Bauplan 3.3, Schritt 5).
 *
 * - Genau ein ExoPlayer und genau eine MediaSession leben in diesem
 *   Service; beide werden in onDestroy genau einmal freigegeben.
 * - Media3 verwaltet Audio Focus fuer Musik selbst (Schritt 5.6);
 *   eigene doppelte Fokusverwaltung ist verboten.
 * - Der Service ist ausschliesslich fuer Musikwiedergabe da und darf nie
 *   als allgemeiner Timer- oder Workoutdienst missbraucht werden
 *   (Bauplan Abschnitt 4).
 */
class PlaybackService : MediaLibraryService() {
    private var player: ExoPlayer? = null
    private var session: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        val exoPlayer =
            ExoPlayer
                .Builder(this)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    // Media3 uebernimmt den Audio Focus (Schritt 5.6).
                    true,
                ).setHandleAudioBecomingNoisy(true)
                .build()
        player = exoPlayer
        session =
            MediaLibrarySession
                .Builder(this, exoPlayer, LibrarySessionCallback())
                .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val current = player
        // Ohne aktive Wiedergabe gibt es keinen Grund weiterzulaufen.
        if (current == null || !current.playWhenReady || current.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Genau einmal freigeben (Abnahme Schritt 5).
        session?.release()
        session = null
        player?.release()
        player = null
        super.onDestroy()
    }

    /**
     * V1 bietet kein externes Browsing an; externe Controller erhalten nur
     * die Standard-Playersteuerung, keine Custom Commands (Schritt 5.7).
     */
    private class LibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> =
            Futures.immediateFuture(mediaItems.map(MediaItemFactory::resolveForPlayback))
    }
}
