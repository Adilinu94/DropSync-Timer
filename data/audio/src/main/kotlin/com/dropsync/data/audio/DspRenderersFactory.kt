package com.dropsync.data.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * RenderersFactory der eigenen Audio-Pipeline (ADR-0005):
 * - Float-Output aktiv, damit Hi-Res-Quellen (24/32 Bit) ohne
 *   16-Bit-Zwischenschritt zum System gelangen;
 * - die DSP-Kette laeuft laut DefaultAudioSink.configure vor der
 *   Float-/Int16-Konvertierung und damit in beiden Pfaden.
 */
@OptIn(UnstableApi::class)
class DspRenderersFactory(
    context: Context,
    private val audioProcessors: Array<AudioProcessor>,
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink =
        DefaultAudioSink
            .Builder(context)
            .setEnableFloatOutput(true)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(audioProcessors)
            .build()
}
