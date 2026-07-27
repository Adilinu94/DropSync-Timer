package com.dropsync.data.audio.di

import com.dropsync.data.audio.AudioEngineRepositoryImpl
import com.dropsync.data.audio.AudioPipeline
import com.dropsync.data.audio.DspSettingsStore
import com.dropsync.domain.audio.AudioEngineRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt-Anbindung der Audio-Engine (ADR-0005). */
@Module
@InstallIn(SingletonComponent::class)
object AudioDataModule {
    @Provides
    @Singleton
    fun provideAudioEngineRepository(
        settingsStore: DspSettingsStore,
        pipeline: AudioPipeline,
    ): AudioEngineRepository = AudioEngineRepositoryImpl(settingsStore, pipeline)
}
