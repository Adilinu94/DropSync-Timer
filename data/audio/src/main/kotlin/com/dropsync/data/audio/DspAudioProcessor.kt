package com.dropsync.data.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.dropsync.domain.audio.PcmCodec
import com.dropsync.domain.audio.PcmEncoding
import java.nio.ByteBuffer

/**
 * Basisklasse aller DSP-Stufen (ADR-0005): akzeptiert lineares PCM in
 * 16/24/32 Bit oder Float, rechnet intern in 64-Bit-Double und gibt
 * 32-Bit-Float aus. Laufzeitparameter (z. B. Gain) sind volatil und
 * wirken ohne Flush; Aktiv-/Inaktivwechsel passieren nur ueber neutrale
 * Parameter, nie ueber Reconfigure waehrend der Wiedergabe.
 */
abstract class DspAudioProcessor : BaseAudioProcessor() {
    private var inputEncoding: PcmEncoding = PcmEncoding.PCM_16
    private var samples: DoubleArray = DoubleArray(0)

    final override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        inputEncoding =
            when (inputAudioFormat.encoding) {
                C.ENCODING_PCM_16BIT -> PcmEncoding.PCM_16
                C.ENCODING_PCM_24BIT -> PcmEncoding.PCM_24
                C.ENCODING_PCM_32BIT -> PcmEncoding.PCM_32
                C.ENCODING_PCM_FLOAT -> PcmEncoding.FLOAT
                else -> throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
            }
        return AudioProcessor.AudioFormat(
            inputAudioFormat.sampleRate,
            inputAudioFormat.channelCount,
            C.ENCODING_PCM_FLOAT,
        )
    }

    final override fun queueInput(inputBuffer: ByteBuffer) {
        val available = PcmCodec.sampleCount(inputBuffer, inputEncoding)
        if (available == 0) return
        if (samples.size < available) {
            samples = DoubleArray(available)
        }
        val count = PcmCodec.decode(inputBuffer, inputEncoding, samples)
        processSamples(
            samples = samples,
            count = count,
            sampleRateHz = outputAudioFormat.sampleRate,
            channelCount = outputAudioFormat.channelCount,
        )
        val output = replaceOutputBuffer(count * Float.SIZE_BYTES)
        PcmCodec.encodeFloat(samples, count, output)
        output.flip()
    }

    /** Bearbeitet [count] interleaved Samples in-place (64-Bit-Double). */
    protected abstract fun processSamples(
        samples: DoubleArray,
        count: Int,
        sampleRateHz: Int,
        channelCount: Int,
    )
}
