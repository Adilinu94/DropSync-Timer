package com.dropsync.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Einstellungen (Schritt 12.2/12.3): Markerimport-Status und
 * Datenschutzueberblick. Der Dateiimport selbst folgt im UI-Ausbau;
 * nicht zugeordnete Marker sind bereits sichtbar (6.4).
 */
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val unmatched by viewModel.unmatchedMarkers.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_markers_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        item {
            Text(
                text =
                    pluralStringResource(
                        R.plurals.settings_unmatched_markers,
                        unmatched.size,
                        unmatched.size,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
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
            )
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
}
