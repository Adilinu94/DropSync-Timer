package com.dropsync.data.audio

import com.dropsync.domain.audio.AudioEngineRepository
import com.dropsync.domain.audio.AudioInfo
import com.dropsync.domain.audio.DspConfig
import kotlinx.coroutines.flow.Flow

/**
 * Einziger App-Zugang zur Audio-Engine (Modulregel 3.2). Aenderungen
 * gehen ausschliesslich ueber den Store; die [AudioPipeline] beobachtet
 * ihn und wendet Werte sofort auf die Prozessoren an.
 */
class AudioEngineRepositoryImpl(
    private val settingsStore: DspSettingsStore,
    pipeline: AudioPipeline,
) : AudioEngineRepository {
    override val dspConfig: Flow<DspConfig> = settingsStore.config

    override val audioInfo: Flow<AudioInfo?> = pipeline.audioInfo

    override suspend fun updateDspConfig(config: DspConfig) {
        settingsStore.save(DspConfig.sanitized(config))
    }
}
