package com.dropsync.feature.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

/**
 * Trainings-Tab: Kernflow Training -> freie Session -> Abschluss
 * (Schritt 12.3). Satz-Logging folgt im UI-Ausbau; Session-Lebenszyklus
 * ist bereits vollstaendig bedienbar.
 */
@Composable
fun WorkoutScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val session by viewModel.activeSession.collectAsStateWithLifecycle()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val active = session
        if (active == null) {
            Text(
                text = stringResource(R.string.workout_no_session),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = viewModel::startSession,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.workout_start_session))
            }
        } else {
            Text(
                text = stringResource(R.string.workout_active_session),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            // Datum folgt Locale-Format; intern bleibt UTC-Epoch (12.7).
            Text(
                text =
                    stringResource(
                        R.string.workout_started_at,
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(active.startedAtEpochMs)),
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = viewModel::discardSession,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.workout_discard_session))
                }
                Button(
                    onClick = viewModel::completeSession,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.workout_complete_session))
                }
            }
        }
    }
}
