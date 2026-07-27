package com.dropsync.domain.audio

import kotlinx.coroutines.flow.Flow

/**
 * Konfiguration der DSP-Kette (ADR-0005, Phase 1). Weitere Stufen (EQ,
 * Stereo Expansion, Reverb, Dither, Resampler) ergaenzen dieses Modell in
 * Phase 2; jede Stufe bleibt einzeln bypassbar.
 */
data class DspConfig(
    /** Master-Schalter: aus = alle Stufen Bypass (auch fuer Bit-Perfect). */
    val enabled: Boolean = true,
    /** Vorverstaerker in dB, begrenzt auf [PREAMP_MIN_DB]..[PREAMP_MAX_DB]. */
    val preampDb: Double = 0.0,
    /** Soft-Limiter hinter dem Preamp (Clipping-Schutz). */
    val limiterEnabled: Boolean = true,
) {
    companion object {
        const val PREAMP_MIN_DB: Double = -12.0
        const val PREAMP_MAX_DB: Double = 12.0

        /** Erzwingt gueltige Wertebereiche (UI und Persistenz teilen sie). */
        fun sanitized(config: DspConfig): DspConfig =
            config.copy(
                preampDb = config.preampDb.coerceIn(PREAMP_MIN_DB, PREAMP_MAX_DB),
            )
    }
}

/** Grobklasse des aktiven Ausgabegeraets (fuer Anzeige und Profile). */
enum class OutputDeviceKind {
    SPEAKER,
    WIRED,
    BLUETOOTH_A2DP,
    USB,
    OTHER,
}

/**
 * Detaillierte Audioinformationen der laufenden Wiedergabe (Plan Phase 1).
 * Quelle: Decoder-Eingangsformat; Ausgabe: konfigurierter Audiotrack.
 */
data class AudioInfo(
    /** MIME-Typ des Quellcodecs, z. B. audio/flac. */
    val codecMimeType: String?,
    /** Bitrate der Quelle in Bit/s, falls bekannt. */
    val bitrateBps: Int?,
    val sourceSampleRateHz: Int?,
    val sourceChannelCount: Int?,
    /** Bittiefe der dekodierten Quelle, falls PCM-Kodierung bekannt. */
    val sourceBitDepth: Int?,
    val outputSampleRateHz: Int?,
    /** Klartextname der Ausgabekodierung, z. B. "32-Bit Float". */
    val outputEncoding: String?,
    /** true, wenn der Sink im Hi-Res-Float-Pfad arbeitet. */
    val floatOutput: Boolean,
    /** true, wenn mindestens eine DSP-Stufe aktiv eingreift. */
    val dspActive: Boolean,
    val outputDevice: OutputDeviceKind?,
    /** Anzeigename des Ausgabegeraets, falls verfuegbar. */
    val outputDeviceName: String?,
)

/**
 * App-Zugang zur DSP-Konfiguration und den Audioinformationen; einzige
 * Schnittstelle fuer Features (Modulregel 3.2). Implementierung in
 * `:data:audio`.
 */
interface AudioEngineRepository {
    /** Aktuelle DSP-Konfiguration; Aenderungen wirken sofort. */
    val dspConfig: Flow<DspConfig>

    /** Live-Audioinformationen; null solange nichts spielt. */
    val audioInfo: Flow<AudioInfo?>

    /** Persistiert und aktiviert [config] (bereichsgeprueft). */
    suspend fun updateDspConfig(config: DspConfig)
}
