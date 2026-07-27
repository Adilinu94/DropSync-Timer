package com.dropsync.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropsync.domain.library.SongSort

/**
 * Bibliotheksinhalt nach erteilter Berechtigung (Plan Phase 6):
 * Ansichtswahl (Titel/Kuenstler/Alben/Genres/Ordner/Favoriten/zuletzt/
 * meistgespielt), Volltextsuche, Sortierung und Hi-Res-Filter fuer die
 * Titelliste sowie Drill-down in Sammlungen.
 */
@Composable
internal fun LibraryContent(
    viewModel: LibraryViewModel,
    contentPadding: PaddingValues,
    scanFailed: Boolean,
    modifier: Modifier = Modifier,
) {
    val selectedView by viewModel.selectedView.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        if (scanFailed) {
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    text = stringResource(R.string.library_scan_failed),
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        SearchField(
            query = searchQuery,
            onQueryChange = viewModel::setSearchQuery,
        )

        when {
            searchQuery.isNotBlank() -> {
                val results by viewModel.searchResults.collectAsStateWithLifecycle()
                SongColumn(
                    songs = results,
                    favoriteIds = favoriteIds,
                    contentPadding = contentPadding,
                    onPlay = { index -> viewModel.play(results, index) },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onPlayNext = viewModel::playNext,
                    onAddToQueue = viewModel::addToQueue,
                    showFastScroller = false,
                )
            }

            detail != null -> {
                val bucket = detail!!
                val detailSongs by viewModel.detailSongs.collectAsStateWithLifecycle()
                DetailHeader(title = bucket.label, onBack = viewModel::closeDetail)
                SongColumn(
                    songs = detailSongs,
                    favoriteIds = favoriteIds,
                    contentPadding = contentPadding,
                    onPlay = { index -> viewModel.play(detailSongs, index) },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onPlayNext = viewModel::playNext,
                    onAddToQueue = viewModel::addToQueue,
                    showFastScroller = false,
                )
            }

            else -> {
                ViewChips(selected = selectedView, onSelect = viewModel::selectView)
                LibraryViewBody(
                    viewModel = viewModel,
                    view = selectedView,
                    favoriteIds = favoriteIds,
                    contentPadding = contentPadding,
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.library_search_clear),
                    )
                }
            }
        },
        placeholder = { Text(stringResource(R.string.library_search_hint)) },
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun ViewChips(
    selected: LibraryView,
    onSelect: (LibraryView) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibraryView.entries.forEach { view ->
            FilterChip(
                selected = view == selected,
                onClick = { onSelect(view) },
                label = { Text(stringResource(view.labelRes())) },
            )
        }
    }
}

@Composable
private fun DetailHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.library_back),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LibraryViewBody(
    viewModel: LibraryViewModel,
    view: LibraryView,
    favoriteIds: Set<Long>,
    contentPadding: PaddingValues,
) {
    when (view) {
        LibraryView.SONGS -> {
            val songs by viewModel.songs.collectAsStateWithLifecycle()
            Column(modifier = Modifier.fillMaxSize()) {
                SortFilterBar(viewModel)
                SongColumn(
                    songs = songs,
                    favoriteIds = favoriteIds,
                    contentPadding = contentPadding,
                    onPlay = { index -> viewModel.play(songs, index) },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onPlayNext = viewModel::playNext,
                    onAddToQueue = viewModel::addToQueue,
                )
            }
        }

        LibraryView.ARTISTS -> {
            val artists by viewModel.artists.collectAsStateWithLifecycle()
            BucketColumn(
                labels =
                    artists.map {
                        BucketItem(it.name, it.name, null, it.trackCount)
                    },
                contentPadding = contentPadding,
                onOpen = { viewModel.openBucket(LibraryView.ARTISTS, it.key, it.title) },
            )
        }

        LibraryView.ALBUMS -> {
            val albums by viewModel.albums.collectAsStateWithLifecycle()
            BucketColumn(
                labels = albums.map { BucketItem(it.title, it.title, it.artist, it.trackCount) },
                contentPadding = contentPadding,
                onOpen = { viewModel.openBucket(LibraryView.ALBUMS, it.key, it.title) },
            )
        }

        LibraryView.GENRES -> {
            val genres by viewModel.genres.collectAsStateWithLifecycle()
            BucketColumn(
                labels = genres.map { BucketItem(it.name, it.name, null, it.trackCount) },
                contentPadding = contentPadding,
                onOpen = { viewModel.openBucket(LibraryView.GENRES, it.key, it.title) },
            )
        }

        LibraryView.FOLDERS -> {
            val folders by viewModel.folders.collectAsStateWithLifecycle()
            BucketColumn(
                labels =
                    folders.map {
                        BucketItem(it.relativePath, it.relativePath, null, it.trackCount)
                    },
                contentPadding = contentPadding,
                onOpen = { viewModel.openBucket(LibraryView.FOLDERS, it.key, it.title) },
            )
        }

        LibraryView.FAVORITES -> {
            val favorites by viewModel.favorites.collectAsStateWithLifecycle()
            SongColumn(
                songs = favorites,
                favoriteIds = favoriteIds,
                contentPadding = contentPadding,
                onPlay = { index -> viewModel.play(favorites, index) },
                onToggleFavorite = viewModel::toggleFavorite,
                onPlayNext = viewModel::playNext,
                onAddToQueue = viewModel::addToQueue,
            )
        }

        LibraryView.RECENTLY_ADDED -> {
            val recent by viewModel.recentlyAdded.collectAsStateWithLifecycle()
            SongColumn(
                songs = recent,
                favoriteIds = favoriteIds,
                contentPadding = contentPadding,
                onPlay = { index -> viewModel.play(recent, index) },
                onToggleFavorite = viewModel::toggleFavorite,
                onPlayNext = viewModel::playNext,
                onAddToQueue = viewModel::addToQueue,
                showFastScroller = false,
            )
        }

        LibraryView.MOST_PLAYED -> {
            val most by viewModel.mostPlayed.collectAsStateWithLifecycle()
            SongColumn(
                songs = most,
                favoriteIds = favoriteIds,
                contentPadding = contentPadding,
                onPlay = { index -> viewModel.play(most, index) },
                onToggleFavorite = viewModel::toggleFavorite,
                onPlayNext = viewModel::playNext,
                onAddToQueue = viewModel::addToQueue,
                showFastScroller = false,
            )
        }
    }
}

@Composable
private fun SortFilterBar(viewModel: LibraryViewModel) {
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val hiResOnly by viewModel.hiResOnly.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = { menuOpen = true },
            label = { Text(stringResource(sort.labelRes())) },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            SongSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes())) },
                    onClick = {
                        viewModel.setSort(option)
                        menuOpen = false
                    },
                )
            }
        }
        FilterChip(
            selected = hiResOnly,
            onClick = { viewModel.toggleHiResOnly() },
            label = { Text(stringResource(R.string.library_filter_hires)) },
        )
    }
}

private fun LibraryView.labelRes(): Int =
    when (this) {
        LibraryView.SONGS -> R.string.library_view_songs
        LibraryView.ARTISTS -> R.string.library_view_artists
        LibraryView.ALBUMS -> R.string.library_view_albums
        LibraryView.GENRES -> R.string.library_view_genres
        LibraryView.FOLDERS -> R.string.library_view_folders
        LibraryView.FAVORITES -> R.string.library_view_favorites
        LibraryView.RECENTLY_ADDED -> R.string.library_view_recently_added
        LibraryView.MOST_PLAYED -> R.string.library_view_most_played
    }

private fun SongSort.labelRes(): Int =
    when (this) {
        SongSort.TITLE -> R.string.library_sort_title
        SongSort.ARTIST -> R.string.library_sort_artist
        SongSort.ALBUM -> R.string.library_sort_album
        SongSort.DURATION -> R.string.library_sort_duration
        SongSort.DATE_ADDED -> R.string.library_sort_date_added
    }
