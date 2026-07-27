package com.dropsync.data.audio

import com.dropsync.domain.audio.AudioMath

/**
 * Vorverstaerker mit Soft-Limiter (Plan Phase 1). Der Gain ist volatil
 * und aenderbar ohne Pipeline-Flush; 1.0 ist bitneutral im Double-Pfad.
 * Cue-Ducking (Schritt 8) bleibt bewusst auf `player.volume`; erst DVC
 * (Phase 2) zieht die Lautstaerkeregelung in diese Stufe.
 */
class PreampProcessor : DspAudioProcessor() {
    @Volatile
    var gainLinear: Double = 1.0

    @Volatile
    var limiterEnabled: Boolean = true

    override fun processSamples(
        samples: DoubleArray,
        count: Int,
        sampleRateHz: Int,
        channelCount: Int,
    ) {
        val gain = gainLinear
        val limit = limiterEnabled
        if (gain == 1.0 && !limit) return
        for (i in 0 until count) {
            val amplified = samples[i] * gain
            samples[i] = if (limit) AudioMath.softClip(amplified) else amplified
        }
    }
}
