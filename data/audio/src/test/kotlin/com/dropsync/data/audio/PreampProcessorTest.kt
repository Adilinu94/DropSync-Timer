package com.dropsync.data.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** DSP-Basisklasse und Preamp (Plan Phase 1, ADR-0005). */
class PreampProcessorTest {
    private fun configured(
        processor: PreampProcessor,
        encoding: Int = C.ENCODING_PCM_16BIT,
    ): PreampProcessor {
        processor.configure(AudioProcessor.AudioFormat(48_000, 2, encoding))
        processor.flush()
        return processor
    }

    private fun pcm16Buffer(vararg values: Short): ByteBuffer {
        val buffer =
            ByteBuffer
                .allocateDirect(values.size * 2)
                .order(ByteOrder.LITTLE_ENDIAN)
        values.forEach(buffer::putShort)
        buffer.flip()
        return buffer
    }

    private fun floats(buffer: ByteBuffer): FloatArray {
        val ordered = buffer.order(ByteOrder.LITTLE_ENDIAN)
        val result = FloatArray(ordered.remaining() / 4)
        for (i in result.indices) {
            result[i] = ordered.float
        }
        return result
    }

    @Test
    fun `ausgabeformat ist immer 32 bit float`() {
        val processor = PreampProcessor()
        val output = processor.configure(AudioProcessor.AudioFormat(96_000, 2, C.ENCODING_PCM_24BIT))
        assertEquals(C.ENCODING_PCM_FLOAT, output.encoding)
        assertEquals(96_000, output.sampleRate)
        assertEquals(2, output.channelCount)
        assertTrue(processor.isActive)
    }

    @Test
    fun `gain verdoppelt pcm16 samples im floatpfad`() {
        val processor = configured(PreampProcessor())
        processor.gainLinear = 2.0
        processor.limiterEnabled = false

        processor.queueInput(pcm16Buffer(8192, -8192, 0)) // 0.25, -0.25, 0
        val output = floats(processor.output)
        assertEquals(3, output.size)
        assertEquals(0.5f, output[0], 1e-6f)
        assertEquals(-0.5f, output[1], 1e-6f)
        assertEquals(0.0f, output[2], 0.0f)
    }

    @Test
    fun `neutraler gain reicht samples unveraendert durch`() {
        val processor = configured(PreampProcessor())
        processor.gainLinear = 1.0
        processor.limiterEnabled = false

        processor.queueInput(pcm16Buffer(16384, -32768))
        val output = floats(processor.output)
        assertEquals(0.5f, output[0], 1e-9f)
        assertEquals(-1.0f, output[1], 0.0f)
    }

    @Test
    fun `limiter verhindert clipping bei hohem gain`() {
        val processor = configured(PreampProcessor())
        processor.gainLinear = 4.0 // +12 dB.
        processor.limiterEnabled = true

        processor.queueInput(pcm16Buffer(29491)) // ~0.9 -> 3.6 ohne Limiter.
        val output = floats(processor.output)
        assertTrue("Limiter muss unter 1.0 bleiben", output[0] <= 1.0f)
        assertTrue("Signal darf nicht verschwinden", output[0] > 0.89f)
    }

    @Test
    fun `unbekannte kodierung wird abgelehnt`() {
        val processor = PreampProcessor()
        var rejected = false
        try {
            processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_INVALID))
        } catch (e: AudioProcessor.UnhandledAudioFormatException) {
            rejected = true
        }
        assertTrue(rejected)
    }
}
