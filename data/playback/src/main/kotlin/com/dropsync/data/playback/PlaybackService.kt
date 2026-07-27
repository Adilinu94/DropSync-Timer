package com.dropsync.data.playback

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.dropsync.data.audio.AudioPipeline
import com.dropsync.data.audio.DspRenderersFactory
import com.dropsync.data.audio.OutputFormatInfo
import com.dropsync.data.audio.SourceFormatInfo
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Einziger Wiedergabedienst der App (Bauplan 3.3, Schritt 5).
 *
 * - Genau ein sessionfuehrender ExoPlayer und genau eine MediaSession
 *   leben in diesem Service; beide werden in onDestroy genau einmal
 *   freigegeben (Praezisierung durch ADR-0007).
 * - Media3 verwaltet Audio Focus fuer Musik selbst (Schritt 5.6);
 *   eigene doppelte Fokusverwaltung ist verboten.
 * - Der Player laeuft ueber die eigene Audio-Pipeline (ADR-0005):
 *   Float-Output plus DSP-Kette aus :data:audio.
 * - Der Service ist ausschliesslich fuer Musikwiedergabe da und darf nie
 *   als allgemeiner Timer- oder Workoutdienst missbraucht werden
 *   (Bauplan Abschnitt 4).
 */
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {
    @Inject
    lateinit var audioPipeline: AudioPipeline

    private var player: ExoPlayer? = null
    private var session: MediaLibrarySession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val exoPlayer =
            ExoPlayer
                .Builder(this, DspRenderersFactory(this, audioPipeline.audioProcessors()))
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
        exoPlayer.addAnalyticsListener(AudioInfoListener(audioPipeline))
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
        audioPipeline.onPlaybackReleased()
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

    /**
     * Meldet Quellformat und Audiotrack-Konfiguration an die Pipeline;
     * Grundlage der Audioinformationen (Plan Phase 1).
     */
    @OptIn(UnstableApi::class)
    private class AudioInfoListener(
        private val pipeline: AudioPipeline,
    ) : AnalyticsListener {
        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?,
        ) {
            pipeline.onSourceFormatChanged(
                SourceFormatInfo(
                    codecMimeType = format.sampleMimeType,
                    bitrateBps = format.bitrate.takeIf { it != Format.NO_VALUE },
                    sampleRateHz = format.sampleRate.takeIf { it != Format.NO_VALUE },
                    channelCount = format.channelCount.takeIf { it != Format.NO_VALUE },
                    bitDepth = bitDepthOf(format.pcmEncoding),
                ),
            )
        }

        override fun onAudioTrackInitialized(
            eventTime: AnalyticsListener.EventTime,
            audioTrackConfig: AudioSink.AudioTrackConfig,
        ) {
            pipeline.onAudioTrackInitialized(
                OutputFormatInfo(
                    sampleRateHz = audioTrackConfig.sampleRate,
                    encodingName = encodingName(audioTrackConfig.encoding),
                    isFloat = audioTrackConfig.encoding == C.ENCODING_PCM_FLOAT,
                ),
            )
        }

        private fun bitDepthOf(pcmEncoding: Int): Int? =
            when (pcmEncoding) {
                C.ENCODING_PCM_8BIT -> 8
                C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
                C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
                C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN -> 32
                C.ENCODING_PCM_FLOAT -> 32
                else -> null
            }

        private fun encodingName(encoding: Int): String =
            when (encoding) {
                C.ENCODING_PCM_FLOAT -> "32-Bit Float"
                C.ENCODING_PCM_32BIT -> "32-Bit PCM"
                C.ENCODING_PCM_24BIT -> "24-Bit PCM"
                C.ENCODING_PCM_16BIT -> "16-Bit PCM"
                C.ENCODING_PCM_8BIT -> "8-Bit PCM"
                else -> "Encoding $encoding"
            }
    }
}
