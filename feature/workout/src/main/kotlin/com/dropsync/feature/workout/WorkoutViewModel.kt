package com.dropsync.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dropsync.core.common.AppResult
import com.dropsync.core.model.SetRole
import com.dropsync.domain.workout.ExerciseInfo
import com.dropsync.domain.workout.SegmentInput
import com.dropsync.domain.workout.SessionExerciseInfo
import com.dropsync.domain.workout.WorkoutMath
import com.dropsync.domain.workout.WorkoutRepository
import com.dropsync.domain.workout.WorkoutSessionInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/** Ergebnis eines Satzabschlusses fuer die Undo-Snackbar (12.5). */
data class CompletedClusterUi(
    val clusterId: Long,
    val summary: String,
)

@HiltViewModel
class WorkoutViewModel
    @Inject
    constructor(
        private val workoutRepository: WorkoutRepository,
    ) : ViewModel() {
        private val locale: String = Locale.getDefault().language

        /** Hoechstens eine aktive Session (Bauplan 9.8). */
        val activeSession: StateFlow<WorkoutSessionInfo?> =
            workoutRepository.activeSession.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                null,
            )

        /** Uebungsauswahl: Slug-basiert, lokalisiert (9.1). */
        val exercises: StateFlow<List<ExerciseInfo>> =
            workoutRepository.observeExercises(locale).stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyList(),
            )

        /** Uebungen der aktiven Session in stabiler Reihenfolge (9.3). */
        @OptIn(ExperimentalCoroutinesApi::class)
        val sessionExercises: StateFlow<List<SessionExerciseInfo>> =
            workoutRepository.activeSession
                .flatMapLatest { session ->
                    if (session == null) {
                        flowOf(emptyList())
                    } else {
                        workoutRepository.observeSessionExercises(session.id, locale)
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        private val _lastCompleted = MutableStateFlow<CompletedClusterUi?>(null)

        /** Letzter Abschluss; UI bietet 10 s lang Rueckgaengig an (12.5). */
        val lastCompleted: StateFlow<CompletedClusterUi?> = _lastCompleted.asStateFlow()

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

        fun addExercise(exerciseId: Long) {
            val session = activeSession.value ?: return
            viewModelScope.launch {
                workoutRepository.addExercise(session.id, exerciseId, supersetGroupId = null)
            }
        }

        /**
         * Schliesst einen einfachen Arbeitssatz ab. Gewichtseingabe in kg
         * (Komma oder Punkt); [perHand] setzt loadMultiplier = 2 fuer zwei
         * gleich schwere Implementseiten (5.4).
         */
        fun completeSet(
            sessionExerciseId: Long,
            weightInput: String,
            repsInput: String,
            perHand: Boolean,
            summary: String,
        ) {
            val loadMilliKg =
                runCatching { WorkoutMath.roundKgInputToMilliKg(weightInput) }.getOrNull()
                    ?: return
            if (loadMilliKg < 0) return
            val reps = repsInput.trim().toIntOrNull() ?: return
            if (reps <= 0) return
            viewModelScope.launch {
                val result =
                    workoutRepository.completeCluster(
                        sessionExerciseId = sessionExerciseId,
                        setRole = SetRole.WORKING,
                        segments =
                            listOf(
                                SegmentInput(
                                    externalLoadMilliKgPerImplement = loadMilliKg,
                                    loadMultiplier = if (perHand) 2 else 1,
                                    reps = reps,
                                ),
                            ),
                        note = null,
                    )
                if (result is AppResult.Success) {
                    _lastCompleted.value = CompletedClusterUi(result.value, summary)
                }
            }
        }

        /** Rueckgaengig innerhalb des Snackbar-Fensters (12.5). */
        fun undoLastCompleted() {
            val completed = _lastCompleted.value ?: return
            viewModelScope.launch {
                workoutRepository.undoCompleteCluster(completed.clusterId)
                _lastCompleted.value = null
            }
        }

        fun clearLastCompleted() {
            _lastCompleted.value = null
        }
    }
