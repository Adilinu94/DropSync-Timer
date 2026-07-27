package com.dropsync.feature.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropsync.domain.timer.TimerStatus
import java.util.Locale

/**
 * Resttimer-Karte im Trainingskontext (Schritt 12.3). Der Timerstatus
 * nutzt `stateDescription`; der sekuendliche Countdown erzeugt keine
 * ununterbrochenen TalkBack-Ansagen (12.4), weil nur der Status, nicht
 * der Zahlenwert als Zustandsbeschreibung gemeldet wird.
 */
@Composable
fun TimerSection(
    modifier: Modifier = Modifier,
    viewModel: TimerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val statusText =
        when (state.status) {
            TimerStatus.IDLE -> {
                stringResource(R.string.timer_state_idle)
            }

            TimerStatus.PREPARING -> {
                stringResource(R.string.timer_state_preparing)
            }

            TimerStatus.RUNNING -> {
                stringResource(R.string.timer_state_running)
            }

            TimerStatus.PAUSED -> {
                stringResource(R.string.timer_state_paused)
            }

            TimerStatus.COMPLETED -> {
                stringResource(R.string.timer_state_completed)
            }

            TimerStatus.CANCELLED, TimerStatus.FAILED -> {
                stringResource(R.string.timer_state_cancelled)
            }
        }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp)
                .semantics { stateDescription = statusText },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state.status) {
                TimerStatus.IDLE -> {
                    Text(
                        text = stringResource(R.string.timer_rest_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        REST_PRESETS_SECONDS.forEach { seconds ->
                            OutlinedButton(
                                onClick = { viewModel.startRest(seconds * 1_000L) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(R.string.timer_preset_seconds, seconds))
                            }
                        }
                    }
                }

                TimerStatus.RUNNING, TimerStatus.PAUSED, TimerStatus.PREPARING -> {
                    Text(
                        text = formatRemaining(state.remainingMs),
                        style = MaterialTheme.typography.displayMedium,
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (state.status == TimerStatus.RUNNING) {
                            OutlinedButton(
                                onClick = viewModel::pause,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(R.string.timer_pause))
                            }
                        } else if (state.status == TimerStatus.PAUSED) {
                            Button(
                                onClick = viewModel::resume,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(R.string.timer_resume))
                            }
                        }
                        OutlinedButton(
                            onClick = viewModel::cancel,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.timer_cancel))
                        }
                    }
                }

                TimerStatus.COMPLETED, TimerStatus.CANCELLED, TimerStatus.FAILED -> {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Button(
                        onClick = viewModel::acknowledgeFinished,
                        modifier =
                            Modifier
                                .padding(top = 8.dp)
                                .heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.timer_ok))
                    }
                }
            }
        }
    }
}

/** Feste Rest-Presets in Sekunden (TimerPreset nur NORMAL/REST). */
private val REST_PRESETS_SECONDS = listOf(60, 90, 120, 180)

private fun formatRemaining(remainingMs: Long): String {
    val totalSeconds = (remainingMs + 999) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}
