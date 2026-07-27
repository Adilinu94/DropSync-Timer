package com.dropsync.data.audio.di

import com.dropsync.core.common.DispatcherProvider
import com.dropsync.core.database.TransactionRunner
import com.dropsync.core.database.dao.EqPresetDao
import com.dropsync.data.audio.AudioEngineRepositoryImpl
import com.dropsync.data.audio.AudioPipeline
import com.dropsync.data.audio.DspSettingsStore
import com.dropsync.domain.audio.AudioEngineRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt-Anbindung der Audio-Engine (ADR-0005, Plan Phase 2). */
@Module
@InstallIn(SingletonComponent::class)
object AudioDataModule {
    @Provides
    @Singleton
    fun provideAudioEngineRepository(
        settingsStore: DspSettingsStore,
        pipeline: AudioPipeline,
        eqPresetDao: EqPresetDao,
        transactionRunner: TransactionRunner,
        dispatchers: DispatcherProvider,
    ): AudioEngineRepository =
        AudioEngineRepositoryImpl(
            settingsStore = settingsStore,
            pipeline = pipeline,
            eqPresetDao = eqPresetDao,
            transactionRunner = transactionRunner,
            dispatchers = dispatchers,
        )
}
