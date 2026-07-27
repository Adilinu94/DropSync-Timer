package com.dropsync.feature.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dropsync.domain.timer.CancelReason
import com.dropsync.domain.timer.TimerEngine
import com.dropsync.domain.timer.TimerMode
import com.dropsync.domain.timer.TimerState
import com.dropsync.domain.timer.TimerStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Timer-UI-Zustand (Schritt 12.3: Timer -> Modus -> Start ->
 * Pause/Abbruch). `evaluate()` ist idempotent; der UI-Tick ist nie die
 * Abschlussquelle (7.1).
 */
@HiltViewModel
class TimerViewModel
    @Inject
    constructor(
        private val timerEngine: TimerEngine,
    ) : ViewModel() {
        val state: StateFlow<TimerState> = timerEngine.state

        init {
            viewModelScope.launch {
                while (isActive) {
                    timerEngine.evaluate()
                    delay(TICK_MS)
                }
            }
        }

        /** Startet einen Resttimer mit fester Dauer (TimerPreset-Domaene). */
        fun startRest(durationMs: Long) {
            if (state.value.status != TimerStatus.IDLE) return
            timerEngine.start(TimerMode.REST, durationMs)
        }

        fun pause() {
            timerEngine.pause()
        }

        fun resume() {
            timerEngine.resume()
        }

        fun cancel() {
            timerEngine.cancel(CancelReason.USER)
            timerEngine.reset()
        }

        /** Endzustand bestaetigen: zurueck zu IDLE (7.2). */
        fun acknowledgeFinished() {
            timerEngine.reset()
        }

        private companion object {
            const val TICK_MS = 250L
        }
    }
