package com.dropsync.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropsync.domain.workout.ExerciseInfo
import com.dropsync.domain.workout.SessionExerciseInfo
import java.text.DateFormat
import java.util.Date

/**
 * Trainings-Tab (Schritt 12.3): Training -> freie Session -> Uebung
 * hinzufuegen -> Satz abschliessen. Der Satzabschluss zeigt eine klar
 * sichtbare Rueckgaengig-Aktion fuer die letzten 10 Sekunden (12.5);
 * SnackbarDuration.Long entspricht 10 s.
 */
@Composable
fun WorkoutScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val session by viewModel.activeSession.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val sessionExercises by viewModel.sessionExercises.collectAsStateWithLifecycle()
    val lastCompleted by viewModel.lastCompleted.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.workout_undo)
    val completedText = stringResource(R.string.workout_set_saved)

    LaunchedEffect(lastCompleted) {
        val completed = lastCompleted ?: return@LaunchedEffect
        val result =
            snackbarHostState.showSnackbar(
                message = "$completedText ${completed.summary}",
                actionLabel = undoLabel,
                duration = androidx.compose.material3.SnackbarDuration.Long,
            )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoLastCompleted()
        } else {
            viewModel.clearLastCompleted()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        val active = session
        if (active == null) {
            EmptySessionContent(
                contentPadding = contentPadding,
                onStart = viewModel::startSession,
                modifier = Modifier.weight(1f),
            )
        } else {
            ActiveSessionContent(
                startedAtEpochMs = active.startedAtEpochMs,
                exercises = exercises,
                sessionExercises = sessionExercises,
                contentPadding = contentPadding,
                onAddExercise = viewModel::addExercise,
                onCompleteSet = viewModel::completeSet,
                onCompleteSession = viewModel::completeSession,
                onDiscardSession = viewModel::discardSession,
                modifier = Modifier.weight(1f),
            )
        }
        SnackbarHost(hostState = snackbarHostState)
    }
}

@Composable
private fun EmptySessionContent(
    contentPadding: PaddingValues,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.workout_no_session),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onStart, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.workout_start_session))
        }
    }
}

@Composable
private fun ActiveSessionContent(
    startedAtEpochMs: Long,
    exercises: List<ExerciseInfo>,
    sessionExercises: List<SessionExerciseInfo>,
    contentPadding: PaddingValues,
    onAddExercise: (Long) -> Unit,
    onCompleteSet: (Long, String, String, Boolean, String) -> Unit,
    onCompleteSession: () -> Unit,
    onDiscardSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.workout_active_session),
                    style = MaterialTheme.typography.titleLarge,
                )
                // Datum folgt Locale-Format; intern bleibt UTC-Epoch (12.7).
                Text(
                    text =
                        stringResource(
                            R.string.workout_started_at,
                            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(startedAtEpochMs)),
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            ExercisePicker(
                exercises = exercises,
                onAddExercise = onAddExercise,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        items(sessionExercises, key = { it.id }) { sessionExercise ->
            SetEntryCard(
                sessionExercise = sessionExercise,
                onCompleteSet = onCompleteSet,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedButton(
                    onClick = onDiscardSession,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.workout_discard_session))
                }
                Button(
                    onClick = onCompleteSession,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.workout_complete_session))
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePicker(
    exercises: List<ExerciseInfo>,
    onAddExercise: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ExerciseInfo?>(null) }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = selected?.displayName ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.workout_pick_exercise)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier =
                    Modifier
                        .menuAnchor(
                            androidx.compose.material3.MenuAnchorType.PrimaryNotEditable,
                        ).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                exercises.forEach { exercise ->
                    DropdownMenuItem(
                        text = { Text(exercise.displayName) },
                        onClick = {
                            selected = exercise
                            expanded = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(0.dp))
        Button(
            onClick = { selected?.let { onAddExercise(it.id) } },
            enabled = selected != null,
            modifier =
                Modifier
                    .padding(start = 8.dp)
                    .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.workout_add_exercise))
        }
    }
}

@Composable
private fun SetEntryCard(
    sessionExercise: SessionExerciseInfo,
    onCompleteSet: (Long, String, String, Boolean, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var weight by rememberSaveable(sessionExercise.id) { mutableStateOf("") }
    var reps by rememberSaveable(sessionExercise.id) { mutableStateOf("") }
    var perHand by rememberSaveable(sessionExercise.id) { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = sessionExercise.displayName,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text(stringResource(R.string.workout_weight_kg)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it },
                    label = { Text(stringResource(R.string.workout_reps)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = perHand, onCheckedChange = { perHand = it })
                // Keine Funktion nur ueber Farbe; expliziter Text (12.1).
                Text(stringResource(R.string.workout_per_hand))
            }
            val summary = "${sessionExercise.displayName}: $weight kg x $reps"
            Button(
                onClick = {
                    onCompleteSet(sessionExercise.id, weight, reps, perHand, summary)
                    weight = ""
                    reps = ""
                },
                enabled = weight.isNotBlank() && reps.isNotBlank(),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.workout_complete_set))
            }
        }
    }
}
