package com.dropsync.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dropsync.core.model.Song
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
    val openPlaylist by viewModel.openPlaylist.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    var songForPlaylist by remember { mutableStateOf<Song?>(null) }

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
                    onDetectDrops = viewModel::detectDrops,
                    onAddToPlaylist = { songForPlaylist = it },
                    showFastScroller = false,
                )
            }

            openPlaylist != null -> {
                val pl = openPlaylist!!
                val playlistSongs by viewModel.playlistSongs.collectAsStateWithLifecycle()
                PlaylistDetail(
                    playlist = pl,
                    songs = playlistSongs,
                    contentPadding = contentPadding,
                    onBack = viewModel::closePlaylist,
                    onPlay = { index -> viewModel.play(playlistSongs, index) },
                    onRemove = { position -> viewModel.removeFromPlaylist(pl.id, position) },
                    onMove = { from, to -> viewModel.moveInPlaylist(pl.id, from, to) },
                    onSetLabel = { label -> viewModel.setPlaylistLabel(pl.id, label) },
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
                    onDetectDrops = viewModel::detectDrops,
                    onAddToPlaylist = { songForPlaylist = it },
                    showFastScroller = false,
                )
            }

            else -> {
                val visibleViews by viewModel.visibleViews.collectAsStateWithLifecycle()
                LibraryViewsPager(
                    viewModel = viewModel,
                    visibleViews = visibleViews,
                    selectedView = selectedView,
                    favoriteIds = favoriteIds,
                    contentPadding = contentPadding,
                    onAddToPlaylist = { songForPlaylist = it },
                )
            }
        }
    }

    val song = songForPlaylist
    if (song != null) {
        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { songForPlaylist = null },
            onPick = { id ->
                viewModel.addSongToPlaylist(id, song)
                songForPlaylist = null
            },
            onCreateNew = { name ->
                viewModel.createPlaylistWithSong(name, song)
                songForPlaylist = null
            },
        )
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
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Outlined.Clear,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryViewsPager(
    viewModel: LibraryViewModel,
    visibleViews: List<LibraryView>,
    selectedView: LibraryView,
    favoriteIds: Set<Long>,
    contentPadding: PaddingValues,
    onAddToPlaylist: (Song) -> Unit,
) {
    if (visibleViews.isEmpty()) return
    var showConfig by remember { mutableStateOf(false) }
    val startIndex = visibleViews.indexOf(selectedView).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = startIndex) { visibleViews.size }

    // Chip-Tipp -> Seite animieren; Wischen -> ausgewaehlte Ansicht nachfuehren.
    LaunchedEffect(selectedView, visibleViews) {
        val target = visibleViews.indexOf(selectedView)
        if (target >= 0 && target != pagerState.currentPage) {
            pagerState.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pagerState.currentPage, visibleViews) {
        visibleViews.getOrNull(pagerState.currentPage)?.let { view ->
            if (view != selectedView) viewModel.selectView(view)
        }
    }

    ViewChips(
        views = visibleViews,
        selected = selectedView,
        onSelect = viewModel::selectView,
        onOpenConfig = { showConfig = true },
    )
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        LibraryViewBody(
            viewModel = viewModel,
            view = visibleViews[page],
            favoriteIds = favoriteIds,
            contentPadding = contentPadding,
            onAddToPlaylist = onAddToPlaylist,
        )
    }

    if (showConfig) {
        ViewConfigDialog(viewModel = viewModel, onDismiss = { showConfig = false })
    }
}

@Composable
private fun ViewChips(
    views: List<LibraryView>,
    selected: LibraryView,
    onSelect: (LibraryView) -> Unit,
    onOpenConfig: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            views.forEach { view ->
                FilterChip(
                    selected = view == selected,
                    onClick = { onSelect(view) },
                    label = { Text(stringResource(view.labelRes())) },
                    leadingIcon = {
                        Box(
                            modifier =
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(viewColor(view)),
                        )
                    },
                )
            }
        }
        IconButton(onClick = onOpenConfig) {
            Icon(
                Icons.Outlined.Tune,
                contentDescription = stringResource(R.string.library_views_configure),
            )
        }
    }
}

@Composable
private fun ViewConfigDialog(
    viewModel: LibraryViewModel,
    onDismiss: () -> Unit,
) {
    val ordered by viewModel.orderedViews.collectAsStateWithLifecycle()
    val hidden by viewModel.hiddenViews.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_views_configure)) },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                ordered.forEachIndexed { index, view ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = view !in hidden,
                            onCheckedChange = { viewModel.toggleViewHidden(view) },
                        )
                        Text(
                            text = stringResource(view.labelRes()),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { viewModel.moveView(view, up = true) },
                            enabled = index > 0,
                        ) {
                            Icon(
                                Icons.Outlined.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.library_views_move_up),
                            )
                        }
                        IconButton(
                            onClick = { viewModel.moveView(view, up = false) },
                            enabled = index < ordered.lastIndex,
                        ) {
                            Icon(
                                Icons.Outlined.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.library_views_move_down),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.library_views_done))
            }
        },
    )
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
                Icons.AutoMirrored.Outlined.ArrowBack,
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

/**
 * Zufallswiedergabe der aktuellen Titelliste (Musik-Workout-Plan A5).
 * Ist das intelligente Shuffle in den Einstellungen aktiv, gewichtet
 * [LibraryViewModel.shufflePlay] ueber play_stats/Favoriten.
 */
@Composable
private fun ShuffleBar(
    enabled: Boolean,
    onShuffle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
    ) {
        TextButton(
            onClick = onShuffle,
            enabled = enabled,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Icon(
                Icons.Outlined.Shuffle,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.library_shuffle))
        }
    }
}

@Composable
private fun LibraryViewBody(
    viewModel: LibraryViewModel,
    view: LibraryView,
    favoriteIds: Set<Long>,
    contentPadding: PaddingValues,
    onAddToPlaylist: (Song) -> Unit,
) {
    when (view) {
        LibraryView.SONGS -> {
            val songs by viewModel.songs.collectAsStateWithLifecycle()
            Column(modifier = Modifier.fillMaxSize()) {
                SortFilterBar(viewModel)
                ShuffleBar(enabled = songs.isNotEmpty(), onShuffle = { viewModel.shufflePlay(songs) })
                SongColumn(
                    songs = songs,
                    favoriteIds = favoriteIds,
                    contentPadding = contentPadding,
                    onPlay = { index -> viewModel.play(songs, index) },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onPlayNext = viewModel::playNext,
                    onAddToQueue = viewModel::addToQueue,
                    onDetectDrops = viewModel::detectDrops,
                    onAddToPlaylist = onAddToPlaylist,
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
                onDetectDrops = viewModel::detectDrops,
                onAddToPlaylist = onAddToPlaylist,
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
                onDetectDrops = viewModel::detectDrops,
                onAddToPlaylist = onAddToPlaylist,
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
                onDetectDrops = viewModel::detectDrops,
                onAddToPlaylist = onAddToPlaylist,
                showFastScroller = false,
            )
        }

        LibraryView.PLAYLISTS -> {
            val playlists by viewModel.playlists.collectAsStateWithLifecycle()
            PlaylistList(
                playlists = playlists,
                contentPadding = contentPadding,
                onOpen = viewModel::openPlaylist,
                onCreate = viewModel::createPlaylist,
                onRename = viewModel::renamePlaylist,
                onDelete = viewModel::deletePlaylist,
            )
        }
    }
}

@Composable
private fun SortFilterBar(viewModel: LibraryViewModel) {
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val hiResOnly by viewModel.hiResOnly.collectAsStateWithLifecycle()
    val formatFilter by viewModel.formatFilter.collectAsStateWithLifecycle()
    val durationFilter by viewModel.durationFilter.collectAsStateWithLifecycle()
    val availableFormats by viewModel.availableFormats.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var formatMenuOpen by remember { mutableStateOf(false) }
    var durationMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
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
        // Filter nach Format (Plan Phase 6.2); nur tatsaechlich vorhandene Formate.
        FilterChip(
            selected = formatFilter != null,
            onClick = { formatMenuOpen = true },
            label = {
                Text(formatFilter?.displayName ?: stringResource(R.string.library_filter_format))
            },
        )
        DropdownMenu(expanded = formatMenuOpen, onDismissRequest = { formatMenuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_filter_format_all)) },
                onClick = {
                    viewModel.setFormatFilter(null)
                    formatMenuOpen = false
                },
            )
            availableFormats.forEach { format ->
                DropdownMenuItem(
                    text = { Text(format.displayName) },
                    onClick = {
                        viewModel.setFormatFilter(format)
                        formatMenuOpen = false
                    },
                )
            }
        }
        // Filter nach Dauer (Plan Phase 6.2).
        FilterChip(
            selected = durationFilter != DurationFilter.ALL,
            onClick = { durationMenuOpen = true },
            label = { Text(stringResource(durationFilter.labelRes())) },
        )
        DropdownMenu(expanded = durationMenuOpen, onDismissRequest = { durationMenuOpen = false }) {
            DurationFilter.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes())) },
                    onClick = {
                        viewModel.setDurationFilter(option)
                        durationMenuOpen = false
                    },
                )
            }
        }
    }
}

private fun viewColor(view: LibraryView): Color =
    when (view) {
        LibraryView.SONGS -> Color(0xFFDFFF2F)
        LibraryView.ARTISTS -> Color(0xFF9BAA5A)
        LibraryView.ALBUMS -> Color(0xFF4F7BEA)
        LibraryView.GENRES -> Color(0xFF9B6BEA)
        LibraryView.FOLDERS -> Color(0xFF3FBF6B)
        LibraryView.FAVORITES -> Color(0xFFEA5A9B)
        LibraryView.RECENTLY_ADDED -> Color(0xFFEA8A3F)
        LibraryView.MOST_PLAYED -> Color(0xFFEACB3F)
        LibraryView.PLAYLISTS -> Color(0xFF3FB5BF)
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
        LibraryView.PLAYLISTS -> R.string.library_view_playlists
    }

private fun SongSort.labelRes(): Int =
    when (this) {
        SongSort.TITLE -> R.string.library_sort_title
        SongSort.ARTIST -> R.string.library_sort_artist
        SongSort.ALBUM -> R.string.library_sort_album
        SongSort.DURATION -> R.string.library_sort_duration
        SongSort.DATE_ADDED -> R.string.library_sort_date_added
    }

private fun DurationFilter.labelRes(): Int =
    when (this) {
        DurationFilter.ALL -> R.string.library_filter_duration_all
        DurationFilter.UNDER_4_MIN -> R.string.library_filter_duration_short
        DurationFilter.FROM_4_TO_10_MIN -> R.string.library_filter_duration_medium
        DurationFilter.OVER_10_MIN -> R.string.library_filter_duration_long
    }
