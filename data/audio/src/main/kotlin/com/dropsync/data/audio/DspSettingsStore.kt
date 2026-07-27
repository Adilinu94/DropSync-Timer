package com.dropsync.data.audio

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.dropsync.domain.audio.DspConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dspDataStore by preferencesDataStore(name = "audio_dsp")

/**
 * Persistenz der DSP-Konfiguration (Plan Phase 1). Ab Phase 5 werden die
 * Schluessel um ein Geraeteprofil-Praefix erweitert (ADR-0008).
 */
@Singleton
class DspSettingsStore
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val enabledKey = booleanPreferencesKey("dsp_enabled")
        private val preampDbKey = doublePreferencesKey("preamp_db")
        private val limiterKey = booleanPreferencesKey("limiter_enabled")

        val config: Flow<DspConfig> =
            context.dspDataStore.data.map { prefs ->
                DspConfig.sanitized(
                    DspConfig(
                        enabled = prefs[enabledKey] ?: true,
                        preampDb = prefs[preampDbKey] ?: 0.0,
                        limiterEnabled = prefs[limiterKey] ?: true,
                    ),
                )
            }

        suspend fun save(config: DspConfig) {
            val sanitized = DspConfig.sanitized(config)
            context.dspDataStore.edit { prefs ->
                prefs[enabledKey] = sanitized.enabled
                prefs[preampDbKey] = sanitized.preampDb
                prefs[limiterKey] = sanitized.limiterEnabled
            }
        }
    }
