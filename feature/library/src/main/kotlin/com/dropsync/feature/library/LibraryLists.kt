package com.dropsync.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dropsync.core.model.Song
import kotlinx.coroutines.launch

/** Anzeigetitel eines Songs (Titel-Tag, sonst Dateiname). */
internal fun songTitle(song: Song): String = song.title ?: song.displayName

/**
 * Titelliste mit Favoriten-Toggle und optionalem Alphabet-Schnellscroller
 * (Plan Phase 6, Punkt 4). Der Scroller springt zum ersten Titel mit dem
 * gewaehlten Anfangsbuchstaben.
 */
@Composable
internal fun SongColumn(
    songs: List<Song>,
    favoriteIds: Set<Long>,
    contentPadding: PaddingValues,
    onPlay: (Int) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onDetectDrops: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit = {},
    modifier: Modifier = Modifier,
    showFastScroller: Boolean = true,
) {
    if (songs.isEmpty()) {
        EmptyHint(contentPadding = contentPadding, modifier = modifier)
        return
    }
    val listState = rememberLazyListState()
    Row(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = contentPadding,
        ) {
            itemsIndexed(songs, key = { _, song -> song.mediaStoreId }) { index, song ->
                SongRow(
                    song = song,
                    isFavorite = song.mediaStoreId in favoriteIds,
                    onPlay = { onPlay(index) },
                    onToggleFavorite = { onToggleFavorite(song.mediaStoreId) },
                    onPlayNext = { onPlayNext(song) },
                    onAddToQueue = { onAddToQueue(song) },
                    onDetectDrops = { onDetectDrops(song) },
                    onAddToPlaylist = { onAddToPlaylist(song) },
                )
            }
        }
        if (showFastScroller && songs.size >= FAST_SCROLLER_MIN_ITEMS) {
            AlphabetScroller(
                songs = songs,
                listState = listState,
            )
        }
    }
}

/** Eine Titelzeile mit Favoriten-Toggle und Ueberlaufmenue (als Naechstes/Queue/Drops). */
@Composable
private fun SongRow(
    song: Song,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onDetectDrops: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    val playLabel = stringResource(R.string.library_play_song, songTitle(song))
    val favLabel =
        stringResource(
            if (isFavorite) R.string.library_unfavorite else R.string.library_favorite,
        )
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(songTitle(song), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(song.artist ?: stringResource(R.string.library_unknown_artist))
        },
        trailingContent = {
            Row {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector =
                            if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = favLabel,
                    )
                }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.library_more_actions),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_play_next)) },
                        onClick = {
                            onPlayNext()
                            menuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_add_to_queue)) },
                        onClick = {
                            onAddToQueue()
                            menuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_detect_drops)) },
                        onClick = {
                            onDetectDrops()
                            menuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_add_to_playlist)) },
                        onClick = {
                            onAddToPlaylist()
                            menuOpen = false
                        },
                    )
                }
            }
        },
        modifier =
            Modifier
                .clickable(onClickLabel = playLabel) { onPlay() },
    )
}

/** Vertikaler A–Z-Index; tippen springt zum ersten passenden Titel. */
@Composable
private fun AlphabetScroller(
    songs: List<Song>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // Erster Listenindex je Anfangsbuchstabe (Grossbuchstabe, sonst '#').
    val letterIndex =
        remember(songs) {
            val map = linkedMapOf<Char, Int>()
            songs.forEachIndexed { index, song ->
                val first = songTitle(song).trim().firstOrNull()?.uppercaseChar() ?: '#'
                val bucket = if (first.isLetter()) first else '#'
                map.putIfAbsent(bucket, index)
            }
            map
        }
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .width(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        for ((letter, index) in letterIndex) {
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .clickable { scope.launch { listState.scrollToItem(index) } }
                        .padding(vertical = 1.dp),
            )
        }
    }
}

/** Zeile einer Sammlung (Album/Kuenstler/Genre/Ordner) mit Titelzahl. */
@Composable
internal fun BucketColumn(
    labels: List<BucketItem>,
    contentPadding: PaddingValues,
    onOpen: (BucketItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (labels.isEmpty()) {
        EmptyHint(contentPadding = contentPadding, modifier = modifier)
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        items(labels, key = { it.key }) { item ->
            ListItem(
                headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = item.subtitle?.let { { Text(it) } },
                trailingContent = {
                    Text(
                        stringResource(R.string.library_track_count, item.trackCount),
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                modifier = Modifier.clickable { onOpen(item) },
            )
        }
    }
}

/** Anzeigeeintrag einer Sammlung; [key] ist der Filterschluessel. */
data class BucketItem(
    val key: String,
    val title: String,
    val subtitle: String?,
    val trackCount: Int,
)

@Composable
private fun EmptyHint(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.library_empty),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private const val FAST_SCROLLER_MIN_ITEMS = 20
