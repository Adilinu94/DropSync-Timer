package com.dropsync.data.timer.di

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.dropsync.core.common.Clock
import com.dropsync.core.common.DispatcherProvider
import com.dropsync.data.timer.AndroidCueOutput
import com.dropsync.data.timer.CompletionTonePlayer
import com.dropsync.data.timer.DataStoreMonotonicStateStore
import com.dropsync.data.timer.DuckingController
import com.dropsync.data.timer.HapticsAdapter
import com.dropsync.data.timer.MonotonicStateStore
import com.dropsync.data.timer.SpeechTextFormatter
import com.dropsync.data.timer.TtsSpeaker
import com.dropsync.domain.playback.PlayerVolumeGate
import com.dropsync.domain.timer.CueOutput
import com.dropsync.domain.timer.TimerEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Singleton

/**
 * Verdrahtet Timerkern und Cue-Ausgabe (Bauplan Schritt 7/8):
 * genau eine TimerEngine, ein CueOutput, ein DuckingController.
 */
@Module
@InstallIn(SingletonComponent::class)
object TimerDataModule {
    @Provides
    @Singleton
    fun provideDuckingController(volumeGate: PlayerVolumeGate): DuckingController = DuckingController(volumeGate)

    @Provides
    @Singleton
    fun provideCueOutput(
        @ApplicationContext context: Context,
        ducking: DuckingController,
        dispatchers: DispatcherProvider,
    ): CueOutput {
        val scope = CoroutineScope(SupervisorJob() + dispatchers.default)
        val speaker =
            TtsSpeaker(context) { cueSessionId ->
                // TTS fertig/fehlgeschlagen: Ducking genau dieser Session
                // zuruecknehmen; fremde Sessions bleiben unberuehrt (8.3).
                scope.launch { ducking.endCue(cueSessionId) }
            }
        speaker.initialize(Locale.getDefault())
        return AndroidCueOutput(
            tts = speaker,
            haptics = HapticsAdapter(context),
            tonePlayer = CompletionTonePlayer(),
            ducking = ducking,
            formatter = SpeechTextFormatter(Locale.getDefault()),
            scope = scope,
        )
    }

    @Provides
    @Singleton
    fun provideTimerEngine(
        clock: Clock,
        cueOutput: CueOutput,
    ): TimerEngine = TimerEngine(clock, cueOutput)

    @Provides
    @Singleton
    fun provideMonotonicStateStore(
        @ApplicationContext context: Context,
    ): MonotonicStateStore =
        DataStoreMonotonicStateStore(
            PreferenceDataStoreFactory.create {
                context.preferencesDataStoreFile(DataStoreMonotonicStateStore.DATA_STORE_NAME)
            },
        )
}
