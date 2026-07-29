package com.dropsync.feature.player

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropsync.core.designsystem.chart.Waveform
import com.dropsync.core.designsystem.chart.WaveformPlaceholder
import com.dropsync.core.designsystem.component.CoverImage
import com.dropsync.core.designsystem.icon.BrandIcons
import com.dropsync.core.model.SongMarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

/** Tick-Intervall des Positions-Tickers; laeuft nur bei sichtbarem Screen. */
private const val POSITION_TICK_MS = 200L

/**
 * Now-Playing-Screen (Marker/Waveform-Plan Phase 1): Cover, Titel,
 * Interpret, Fortschritt mit Scrubbing-Vorstufe und Transportsteuerung.
 * Grosse Touch-Ziele (12.5), lokalisierte Beschreibungen (12.4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val livePosition by viewModel.livePositionMs.collectAsStateWithLifecycle()
    val waveformState by viewModel.waveform.collectAsStateWithLifecycle()
    val markers by viewModel.nowPlayingMarkers.collectAsStateWithLifecycle()

    // Cache-Miss stoesst die aufschiebbare Analyse an (Plan Phase 2/3).
    LaunchedEffect(state.songId) {
        viewModel.requestAnalysis(state.songId)
    }

    // Ticker nur, solange dieser Screen in der Composition ist: `state`
    // aktualisiert die Position nur bei Player-Ereignissen (Plan Phase 1).
    LaunchedEffect(state.isVisible) {
        while (state.isVisible) {
            viewModel.refreshPosition()
            delay(POSITION_TICK_MS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.now_playing_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(BrandIcons.Back),
                            contentDescription = stringResource(R.string.now_playing_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(bottom = contentPadding.calculateBottomPadding())
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (!state.isVisible) {
                Text(
                    text = stringResource(R.string.now_playing_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                return@Column
            }

            CoverArt(contentUri = state.contentUri)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = state.title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            state.artist?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            ProgressSection(
                positionMs = livePosition ?: state.positionMs,
                durationMs = state.durationMs,
                waveformState = waveformState,
                markers = markers,
                onSeek = viewModel::seekTo,
                onCreateMarker = viewModel::createMarker,
                onDeleteMarker = viewModel::deleteMarker,
            )

            Spacer(modifier = Modifier.height(16.dp))

            TransportControls(
                isPlaying = state.isPlaying,
                onPrevious = viewModel::skipToPrevious,
                onTogglePlayPause = viewModel::togglePlayPause,
                onNext = viewModel::skipToNext,
            )
        }
    }
}

/**
 * Fortschrittsbereich (Plan Phase 3): die Waveform ersetzt die klassische
 * Zeitleiste als Bedienflaeche, sobald die Analyse vorliegt. Waehrend der
 * Analyse pulsiert ein Platzhalter ueber der weiterhin bedienbaren
 * Zeitleiste; schlaegt die Analyse fehl, bleibt dauerhaft die Zeitleiste.
 * Drag zeigt die Zielposition live an; der Sprung erfolgt beim Loslassen.
 * Phase 4: Long-Press auf freier Flaeche setzt einen Marker (nach
 * Bestaetigung), Long-Press nahe einem Tick loescht ihn nach Bestaetigung;
 * Tap bleibt der Sprung (Phase 3).
 */
@Composable
private fun ProgressSection(
    positionMs: Long,
    durationMs: Long,
    waveformState: WaveformUiState,
    markers: List<SongMarker>,
    onSeek: (Long) -> Unit,
    onCreateMarker: (String, Long) -> Unit,
    onDeleteMarker: (Long) -> Unit,
) {
    var scrubPositionMs by remember { mutableStateOf<Long?>(null) }
    var createMarkerAtMs by remember { mutableStateOf<Long?>(null) }
    var markerToDelete by remember { mutableStateOf<SongMarker?>(null) }
    val shownPositionMs = scrubPositionMs ?: positionMs
    val safeDuration = durationMs.coerceAtLeast(1L)

    Column(modifier = Modifier.fillMaxWidth()) {
        when (waveformState) {
            is WaveformUiState.Ready -> {
                Waveform(
                    buckets = waveformState.buckets,
                    progressFraction = (shownPositionMs.toFloat() / safeDuration).coerceIn(0f, 1f),
                    onSeek = { fraction -> onSeek((fraction * safeDuration).toLong()) },
                    onScrubPreview = { fraction ->
                        scrubPositionMs = fraction?.let { (it * safeDuration).toLong() }
                    },
                    markerFractions =
                        markers.map { (it.positionMs.toFloat() / safeDuration).coerceIn(0f, 1f) },
                    onLongPress = { fraction ->
                        val pressedMs = (fraction * safeDuration).toLong()
                        val threshold = (safeDuration / 50L).coerceAtLeast(1_500L)
                        val nearest = markers.minByOrNull { abs(it.positionMs - pressedMs) }
                        if (nearest != null && abs(nearest.positionMs - pressedMs) <= threshold) {
                            markerToDelete = nearest
                        } else {
                            createMarkerAtMs = pressedMs
                        }
                    },
                    contentDescription = stringResource(R.string.now_playing_waveform),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                )
            }

            WaveformUiState.Loading -> {
                WaveformPlaceholder(
                    contentDescription = stringResource(R.string.now_playing_waveform_loading),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                )
                SeekSlider(
                    shownPositionMs = shownPositionMs,
                    safeDuration = safeDuration,
                    onScrub = { scrubPositionMs = it },
                    onSeek = {
                        scrubPositionMs?.let(onSeek)
                        scrubPositionMs = null
                    },
                )
            }

            WaveformUiState.Unavailable, WaveformUiState.Hidden -> {
                SeekSlider(
                    shownPositionMs = shownPositionMs,
                    safeDuration = safeDuration,
                    onScrub = { scrubPositionMs = it },
                    onSeek = {
                        scrubPositionMs?.let(onSeek)
                        scrubPositionMs = null
                    },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTimeMs(shownPositionMs),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = formatTimeMs(durationMs),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }

    createMarkerAtMs?.let { markerPositionMs ->
        CreateMarkerDialog(
            positionMs = markerPositionMs,
            onConfirm = { label ->
                onCreateMarker(label, markerPositionMs)
                createMarkerAtMs = null
            },
            onDismiss = { createMarkerAtMs = null },
        )
    }

    markerToDelete?.let { marker ->
        DeleteMarkerDialog(
            marker = marker,
            onConfirm = {
                onDeleteMarker(marker.id)
                markerToDelete = null
            },
            onDismiss = { markerToDelete = null },
        )
    }
}

/**
 * Bestaetigungsdialog fuer einen neuen Marker (Phase 4): ein
 * versehentlicher Long-Press legt nie ungefragt einen Marker an.
 */
@Composable
private fun CreateMarkerDialog(
    positionMs: Long,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.now_playing_marker_add_title)) },
        text = {
            Column {
                Text(
                    text =
                        stringResource(
                            R.string.now_playing_marker_add_position,
                            formatTimeMs(positionMs),
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.now_playing_marker_label)) },
                    placeholder = { Text(stringResource(R.string.now_playing_marker_default_label)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(label) }) {
                Text(stringResource(R.string.now_playing_marker_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.now_playing_marker_cancel))
            }
        },
    )
}

/** Loeschen nur nach Bestaetigung (Phase 4, Long-Press auf Tick). */
@Composable
private fun DeleteMarkerDialog(
    marker: SongMarker,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.now_playing_marker_delete_title)) },
        text = {
            Text(
                stringResource(
                    R.string.now_playing_marker_delete_message,
                    marker.label,
                    formatTimeMs(marker.positionMs),
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.now_playing_marker_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.now_playing_marker_cancel))
            }
        },
    )
}

/** Klassische Zeitleiste als Fallback und waehrend der Analyse. */
@Composable
private fun SeekSlider(
    shownPositionMs: Long,
    safeDuration: Long,
    onScrub: (Long) -> Unit,
    onSeek: () -> Unit,
) {
    Slider(
        value = (shownPositionMs.toFloat() / safeDuration).coerceIn(0f, 1f),
        onValueChange = { fraction -> onScrub((fraction * safeDuration).toLong()) },
        onValueChangeFinished = onSeek,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TransportControls(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
            Icon(
                painterResource(BrandIcons.SkipPrevious),
                contentDescription = stringResource(R.string.player_previous),
            )
        }
        FilledIconButton(onClick = onTogglePlayPause, modifier = Modifier.size(72.dp)) {
            if (isPlaying) {
                Icon(
                    painterResource(BrandIcons.Pause),
                    contentDescription = stringResource(R.string.player_pause),
                )
            } else {
                Icon(
                    painterResource(BrandIcons.Play),
                    contentDescription = stringResource(R.string.player_play),
                )
            }
        }
        IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
            Icon(
                painterResource(BrandIcons.SkipNext),
                contentDescription = stringResource(R.string.player_next),
            )
        }
    }
}

/**
 * Cover aus dem eingebetteten Bild der Datei ueber den gemeinsamen
 * CoverArtLoader (LRU-Cache in :core:designsystem); grosse Aufloesung,
 * weil das Cover hier fast bildschirmbreit gezeigt wird.
 */
@Composable
private fun CoverArt(contentUri: String?) {
    CoverImage(
        contentUri = contentUri,
        contentDescription = stringResource(R.string.now_playing_cover),
        maxDimPx = NOW_PLAYING_COVER_DIM_PX,
        modifier =
            Modifier
                .fillMaxWidth(0.8f)
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Icon(
            painterResource(BrandIcons.NavMusic),
            contentDescription = stringResource(R.string.now_playing_cover),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(0.4f),
        )
    }
}

private const val NOW_PLAYING_COVER_DIM_PX = 1024

/** mm:ss bzw. h:mm:ss bei Ueberlaenge; stabile Locale-unabhaengige Ziffern. */
private fun formatTimeMs(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L)) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
