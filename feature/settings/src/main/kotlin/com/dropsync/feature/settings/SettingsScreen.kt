package com.dropsync.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropsync.core.model.RestMusicBehavior
import com.dropsync.core.model.Song
import com.dropsync.core.model.SongMarker

/**
 * Einstellungen (Schritt 12.2/12.3): Markerimport ueber den
 * SAF-Dateiwaehler (6.1), Bericht mit allen vier Zaehlern (6.4),
 * manuelle Zuordnung nicht zugeordneter Marker (6.6) und
 * Datenschutzueberblick.
 */
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onOpenAudioSettings: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val unmatched by viewModel.unmatchedMarkers.collectAsStateWithLifecycle()
    val pendingCandidates by viewModel.pendingAutoDetectedMarkers.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val restMusicBehavior by viewModel.restMusicBehavior.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    var markerToLink by remember { mutableStateOf<SongMarker?>(null) }

    // SAF-Dateiwaehler (6.1); JSON-Dateien kommen je nach Quelle auch als
    // text/plain oder application/octet-stream an.
    val openDocument =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(viewModel::importFrom)
        }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_audio_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_audio_entry)) },
                supportingContent = { Text(stringResource(R.string.settings_audio_entry_desc)) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(onClick = onOpenAudioSettings),
            )
        }
        item { HorizontalDivider(Modifier.padding(vertical = 12.dp)) }
        item {
            Text(
                text = stringResource(R.string.settings_rest_music_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        item {
            Text(
                text = stringResource(R.string.settings_rest_music_desc),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        items(RestMusicBehavior.entries, key = { it.name }) { option ->
            RestMusicOption(
                option = option,
                selected = option == restMusicBehavior,
                onSelect = { viewModel.setRestMusicBehavior(option) },
            )
        }
        item { HorizontalDivider(Modifier.padding(vertical = 12.dp)) }
        item {
            Text(
                text = stringResource(R.string.settings_markers_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        item {
            Button(
                onClick = {
                    openDocument.launch(
                        arrayOf("application/json", "text/plain", "application/octet-stream"),
                    )
                },
                enabled = importState != ImportUiState.InProgress,
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp)
                        .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.settings_import_button))
            }
        }
        item { ImportResultText(importState) }
        item {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.settings_unmatched_markers,
                        unmatched.size,
                        unmatched.size,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(unmatched, key = { it.id }) { marker ->
            ListItem(
                headlineContent = { Text(marker.label) },
                supportingContent = {
                    Text(
                        stringResource(
                            R.string.settings_marker_position,
                            marker.positionMs / 1000,
                        ),
                    )
                },
                trailingContent = {
                    TextButton(
                        onClick = { markerToLink = marker },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.settings_link_marker))
                    }
                },
            )
        }
        // Review-Liste der Onset-Kandidaten (Marker/Waveform-Plan Phase 5):
        // AUTO_DETECTED + isEnabled = false; Bestaetigen aktiviert,
        // Verwerfen loescht — Kandidaten werden nie automatisch aktiv.
        if (pendingCandidates.isNotEmpty()) {
            item {
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.settings_pending_candidates,
                            pendingCandidates.size,
                            pendingCandidates.size,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(pendingCandidates, key = { "pending-${it.id}" }) { marker ->
                PendingCandidateItem(
                    marker = marker,
                    songs = songs,
                    onConfirm = { viewModel.confirmMarker(marker.id) },
                    onDiscard = { viewModel.discardMarker(marker.id) },
                )
            }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 12.dp)) }
        item {
            Text(
                text = stringResource(R.string.settings_privacy_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        item {
            Text(
                text = stringResource(R.string.settings_privacy_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }

    markerToLink?.let { marker ->
        LinkMarkerDialog(
            marker = marker,
            songs = songs,
            onConfirm = { songId ->
                viewModel.linkMarker(marker.id, songId)
                markerToLink = null
            },
            onDismiss = { markerToLink = null },
        )
    }
}

/**
 * Ein unbestaetigter Onset-Kandidat (Phase 5) mit Songtitel, Position
 * und den beiden einzigen Aktionen: Bestaetigen oder Verwerfen.
 */
@Composable
private fun PendingCandidateItem(
    marker: SongMarker,
    songs: List<Song>,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
) {
    val songName =
        songs.firstOrNull { it.mediaStoreId == marker.linkedSongId }?.displayName
            ?: stringResource(R.string.settings_candidate_unknown_song)
    ListItem(
        headlineContent = { Text("${marker.label} — $songName") },
        supportingContent = {
            Text(
                stringResource(
                    R.string.settings_marker_position,
                    marker.positionMs / 1000,
                ),
            )
        },
        trailingContent = {
            Row {
                TextButton(
                    onClick = onConfirm,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.settings_candidate_confirm))
                }
                TextButton(
                    onClick = onDiscard,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.settings_candidate_discard))
                }
            }
        },
    )
}

/** Klartextbericht des letzten Imports (6.4); Fehler nennen den Grund. */
@Composable
private fun ImportResultText(state: ImportUiState) {
    val text =
        when (state) {
            ImportUiState.Idle -> {
                return
            }

            ImportUiState.InProgress -> {
                stringResource(R.string.settings_import_running)
            }

            is ImportUiState.Done -> {
                if (state.report.wasRejected) {
                    stringResource(
                        R.string.settings_import_rejected,
                        state.report.rejectedViolations.size,
                    )
                } else {
                    stringResource(
                        R.string.settings_import_report,
                        state.report.added,
                        state.report.updated,
                        state.report.unmatched,
                    )
                }
            }

            is ImportUiState.Failed -> {
                when (state.reason) {
                    ImportFailReason.FILE_TOO_LARGE -> {
                        stringResource(R.string.settings_import_too_large)
                    }

                    ImportFailReason.UNREADABLE -> {
                        stringResource(R.string.settings_import_unreadable)
                    }

                    ImportFailReason.MALFORMED -> {
                        stringResource(R.string.settings_import_malformed)
                    }

                    ImportFailReason.STORE_FAILED -> {
                        stringResource(R.string.settings_import_store_failed)
                    }
                }
            }
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * Manuelle Zuordnung (6.6): Der Nutzer waehlt den Zielsong explizit;
 * die App raetselt nie selbst (5.1).
 */
@Composable
private fun LinkMarkerDialog(
    marker: SongMarker,
    songs: List<Song>,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_link_dialog_title, marker.label)) },
        text = {
            if (songs.isEmpty()) {
                Text(stringResource(R.string.settings_link_no_songs))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(songs, key = { it.mediaStoreId }) { song ->
                        ListItem(
                            headlineContent = { Text(song.displayName) },
                            supportingContent = { song.artist?.let { Text(it) } },
                            trailingContent = {
                                TextButton(
                                    onClick = { onConfirm(song.mediaStoreId) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Text(stringResource(R.string.settings_link_confirm))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_link_cancel))
            }
        },
    )
}

/**
 * Eine Auswahl des Pausen-Musik-Verhaltens (Musik-Workout-Plan Phase 3):
 * Radio-Knopf, Titel und Erklaertext. NORMAL = Aus (Shuffle laeuft weiter).
 */
@Composable
private fun RestMusicOption(
    option: RestMusicBehavior,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(option.titleRes())) },
        supportingContent = { Text(stringResource(option.descRes())) },
        leadingContent = { RadioButton(selected = selected, onClick = onSelect) },
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onSelect),
    )
}

private fun RestMusicBehavior.titleRes(): Int =
    when (this) {
        RestMusicBehavior.NORMAL -> R.string.settings_rest_music_normal
        RestMusicBehavior.REST_PLAYLIST -> R.string.settings_rest_music_rest_playlist
        RestMusicBehavior.DROP_LANDING -> R.string.settings_rest_music_drop_landing
    }

private fun RestMusicBehavior.descRes(): Int =
    when (this) {
        RestMusicBehavior.NORMAL -> R.string.settings_rest_music_normal_desc
        RestMusicBehavior.REST_PLAYLIST -> R.string.settings_rest_music_rest_playlist_desc
        RestMusicBehavior.DROP_LANDING -> R.string.settings_rest_music_drop_landing_desc
    }
