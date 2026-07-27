package com.dropsync.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dropsync.domain.workout.WorkoutRepository
import com.dropsync.domain.workout.WorkoutSessionInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkoutViewModel
    @Inject
    constructor(
        private val workoutRepository: WorkoutRepository,
    ) : ViewModel() {
        /** Hoechstens eine aktive Session (Bauplan 9.8). */
        val activeSession: StateFlow<WorkoutSessionInfo?> =
            workoutRepository.activeSession.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                null,
            )

        fun startSession() {
            viewModelScope.launch {
                workoutRepository.startSession(title = null, fromRoutineId = null)
            }
        }

        fun completeSession() {
            val session = activeSession.value ?: return
            viewModelScope.launch { workoutRepository.completeSession(session.id) }
        }

        /** Setzt nur status = DISCARDED; loescht keine Daten (9.8). */
        fun discardSession() {
            val session = activeSession.value ?: return
            viewModelScope.launch { workoutRepository.discardSession(session.id) }
        }
    }
