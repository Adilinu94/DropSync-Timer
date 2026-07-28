package com.dropsync.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dropsync.core.common.AppError
import com.dropsync.core.common.onFailure
import com.dropsync.core.common.onSuccess
import com.dropsync.core.model.Song
import com.dropsync.domain.audio.TrackAnalysisRepository
import com.dropsync.domain.library.Album
import com.dropsync.domain.library.Artist
import com.dropsync.domain.library.AudioFileFormat
import com.dropsync.domain.library.Genre
import com.dropsync.domain.library.LibraryBrowseRepository
import com.dropsync.domain.library.LibraryFolder
import com.dropsync.domain.library.LibraryRepository
import com.dropsync.domain.library.LibraryViewConfig
import com.dropsync.domain.library.LibraryViewPreferencesRepository
import com.dropsync.domain.library.SongSort
import com.dropsync.domain.playback.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Sichtbarer Zustand der Bibliothek (Schritt 12.3: Berechtigung -> Bibliothek -> Play). */
enum class LibraryError { NONE, PERMISSION_MISSING, SCAN_FAILED }

/** Auswaehlbare Bibliotheksansichten (Plan Phase 6, Punkt 2). */
enum class LibraryView {
    SONGS,
    ARTISTS,
    ALBUMS,
    GENRES,
    FOLDERS,
    FAVORITES,
    RECENTLY_ADDED,
    MOST_PLAYED,
}

/** Aufgeklappte Detailliste einer Sammlung (Album/Kuenstler/Genre/Ordner). */
data class BucketDetail(
    val view: LibraryView,
    val key: String,
    val label: String,
)

/** Dauer-Filter der Titelliste (Plan Phase 6.2: Filter nach Dauer). */
enum class DurationFilter(
    val range: LongRange,
) {
    ALL(0L..Long.MAX_VALUE),
    UNDER_4_MIN(0L until 4L * 60_000),
    FROM_4_TO_10_MIN(4L * 60_000..10L * 60_000),
    OVER_10_MIN(10L * 60_000 + 1..Long.MAX_VALUE),
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        private val libraryRepository: LibraryRepository,
        private val browseRepository: LibraryBrowseRepository,
        private val playbackRepository: PlaybackRepository,
        private val viewPreferences: LibraryViewPreferencesRepository,
        private val trackAnalysisRepository: TrackAnalysisRepository,
    ) : ViewModel() {
        private val _error = MutableStateFlow(LibraryError.NONE)
        val error: StateFlow<LibraryError> = _error.asStateFlow()

        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

        private val _selectedView = MutableStateFlow(LibraryView.SONGS)
        val selectedView: StateFlow<LibraryView> = _selectedView.asStateFlow()

        // Konfigurierbare Ansichten (Plan Phase 6.4): Reihenfolge + Sichtbarkeit,
        // persistiert ueber den Domain-Vertrag; null = Standard (alle, Enum-Reihenfolge).
        private val viewConfig: StateFlow<LibraryViewConfig?> =
            viewPreferences.config.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                null,
            )

        /** Alle Ansichten in konfigurierter Reihenfolge (inkl. ausgeblendeter). */
        val orderedViews: StateFlow<List<LibraryView>> =
            viewConfig
                .map { reconcileOrder(it?.orderedKeys) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_ORDER)

        /** Ausgeblendete Ansichten (nur bekannte Schluessel). */
        val hiddenViews: StateFlow<Set<LibraryView>> =
            viewConfig
                .map { cfg ->
                    cfg
                        ?.hiddenKeys
                        .orEmpty()
                        .mapNotNull(::keyToView)
                        .toSet()
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

        /** Sichtbare Ansichten in Reihenfolge; nie leer (Fallback: alle). */
        val visibleViews: StateFlow<List<LibraryView>> =
            combine(orderedViews, hiddenViews) { order, hidden ->
                order.filterNot { it in hidden }.ifEmpty { order }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_ORDER)

        private val _detail = MutableStateFlow<BucketDetail?>(null)
        val detail: StateFlow<BucketDetail?> = _detail.asStateFlow()

        private val _sort = MutableStateFlow(SongSort.TITLE)
        val sort: StateFlow<SongSort> = _sort.asStateFlow()

        private val _hiResOnly = MutableStateFlow(false)
        val hiResOnly: StateFlow<Boolean> = _hiResOnly.asStateFlow()

        // Filter nach Format und Dauer (Plan Phase 6.2); null/ALL = kein Filter.
        private val _formatFilter = MutableStateFlow<AudioFileFormat?>(null)
        val formatFilter: StateFlow<AudioFileFormat?> = _formatFilter.asStateFlow()

        private val _durationFilter = MutableStateFlow(DurationFilter.ALL)
        val durationFilter: StateFlow<DurationFilter> = _durationFilter.asStateFlow()

        /** Formate, die in der Bibliothek tatsaechlich vorkommen (fuer das Filtermenue). */
        val availableFormats: StateFlow<List<AudioFileFormat>> =
            libraryRepository.availableSongs
                .map { list ->
                    list
                        .mapNotNull { AudioFileFormat.fromFileName(it.displayName) }
                        .distinct()
                        .sortedBy { it.ordinal }
                }.asState(emptyList())

        private val _searchQuery = MutableStateFlow("")
        val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

        /** Titelliste, client-seitig sortiert und nach Hi-Res/Format/Dauer gefiltert. */
        val songs: StateFlow<List<Song>> =
            combine(
                libraryRepository.availableSongs,
                _sort,
                _hiResOnly,
                _formatFilter,
                _durationFilter,
            ) { list, sort, hiResOnly, format, duration ->
                list
                    .asSequence()
                    .filter { !hiResOnly || isHiRes(it) }
                    .filter { format == null || AudioFileFormat.fromFileName(it.displayName) == format }
                    .filter { duration == DurationFilter.ALL || it.durationMs in duration.range }
                    .sortedWith(comparatorFor(sort))
                    .toList()
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val albums: StateFlow<List<Album>> = browseRepository.albums.asState(emptyList())
        val artists: StateFlow<List<Artist>> = browseRepository.artists.asState(emptyList())
        val genres: StateFlow<List<Genre>> = browseRepository.genres.asState(emptyList())
        val folders: StateFlow<List<LibraryFolder>> = browseRepository.folders.asState(emptyList())
        val favorites: StateFlow<List<Song>> = browseRepository.favorites.asState(emptyList())
        val recentlyAdded: StateFlow<List<Song>> = browseRepository.recentlyAdded().asState(emptyList())
        val mostPlayed: StateFlow<List<Song>> = browseRepository.mostPlayed().asState(emptyList())

        /** IDs favorisierter Songs; erlaubt jedem Listeneintrag ein Herz-Toggle. */
        val favoriteIds: StateFlow<Set<Long>> =
            browseRepository.favorites
                .map { list -> list.map { it.mediaStoreId }.toSet() }
                .asState(emptySet())

        /** Songs der aufgeklappten Sammlung; leer solange keine Detailansicht offen ist. */
        val detailSongs: StateFlow<List<Song>> =
            _detail
                .flatMapLatest { detail ->
                    when (detail?.view) {
                        LibraryView.ALBUMS -> browseRepository.songsByAlbum(detail.key)
                        LibraryView.ARTISTS -> browseRepository.songsByArtist(detail.key)
                        LibraryView.GENRES -> browseRepository.songsByGenre(detail.key)
                        LibraryView.FOLDERS -> browseRepository.songsByFolder(detail.key)
                        else -> flowOf(emptyList())
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        /** Suchergebnisse; leerer Query liefert eine leere Liste. */
        val searchResults: StateFlow<List<Song>> =
            _searchQuery
                .debounce(250)
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        flowOf(emptyList())
                    } else {
                        flowOf(runSearch(query))
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        private suspend fun runSearch(query: String): List<Song> {
            var result = emptyList<Song>()
            browseRepository.search(query).onSuccess { result = it }
            return result
        }

        fun selectView(view: LibraryView) {
            _selectedView.value = view
            _detail.value = null
        }

        fun openBucket(
            view: LibraryView,
            key: String,
            label: String,
        ) {
            _detail.value = BucketDetail(view, key, label)
        }

        fun closeDetail() {
            _detail.value = null
        }

        /** Verschiebt [view] in der Anzeigereihenfolge um eine Position (Plan Phase 6.4). */
        fun moveView(
            view: LibraryView,
            up: Boolean,
        ) {
            val order = orderedViews.value.toMutableList()
            val from = order.indexOf(view)
            val to = if (up) from - 1 else from + 1
            if (from < 0 || to !in order.indices) return
            order[from] = order[to].also { order[to] = order[from] }
            persistConfig(order, hiddenViews.value)
        }

        /** Blendet [view] ein/aus; mindestens eine Ansicht bleibt sichtbar. */
        fun toggleViewHidden(view: LibraryView) {
            val hidden = hiddenViews.value.toMutableSet()
            if (!hidden.remove(view)) {
                if (visibleViews.value.size <= 1) return
                hidden.add(view)
            }
            persistConfig(orderedViews.value, hidden)
            if (_selectedView.value in hidden) {
                _selectedView.value =
                    orderedViews.value.firstOrNull { it !in hidden } ?: LibraryView.SONGS
            }
        }

        private fun persistConfig(
            order: List<LibraryView>,
            hidden: Set<LibraryView>,
        ) {
            viewModelScope.launch {
                viewPreferences.setConfig(
                    LibraryViewConfig(
                        orderedKeys = order.map { it.name },
                        hiddenKeys = hidden.map { it.name }.toSet(),
                    ),
                )
            }
        }

        fun setSort(sort: SongSort) {
            _sort.value = sort
        }

        fun toggleHiResOnly() {
            _hiResOnly.value = !_hiResOnly.value
        }

        /** Filtert die Titelliste auf [format]; null hebt den Filter auf. */
        fun setFormatFilter(format: AudioFileFormat?) {
            _formatFilter.value = format
        }

        /** Filtert die Titelliste nach Dauer-Bereich. */
        fun setDurationFilter(filter: DurationFilter) {
            _durationFilter.value = filter
        }

        fun setSearchQuery(query: String) {
            _searchQuery.value = query
        }

        fun toggleFavorite(songId: Long) {
            val makeFavorite = songId !in favoriteIds.value
            viewModelScope.launch { browseRepository.setFavorite(songId, makeFavorite) }
        }

        /** Nach erteilter Berechtigung oder Pull-to-Refresh. */
        fun refresh(force: Boolean = false) {
            viewModelScope.launch {
                _isRefreshing.value = true
                libraryRepository
                    .refreshLibrary(force)
                    .onSuccess { _error.value = LibraryError.NONE }
                    .onFailure { error ->
                        _error.value =
                            if (error is AppError.PermissionDenied) {
                                LibraryError.PERMISSION_MISSING
                            } else {
                                LibraryError.SCAN_FAILED
                            }
                    }
                _isRefreshing.value = false
            }
        }

        /** Ersetzt die Queue durch [list] und startet bei [index]; zaehlt die Wiedergabe. */
        fun play(
            list: List<Song>,
            index: Int,
        ) {
            if (index !in list.indices) return
            viewModelScope.launch {
                browseRepository.recordPlayback(list[index].mediaStoreId)
                playbackRepository.setQueue(list, index, playWhenReady = true)
            }
        }

        /** Reiht [song] direkt hinter dem laufenden Titel ein ("als Naechstes"). */
        fun playNext(song: Song) {
            viewModelScope.launch { playbackRepository.playNext(song) }
        }

        /** Haengt [song] ans Ende der Warteschlange an. */
        fun addToQueue(song: Song) {
            viewModelScope.launch { playbackRepository.addToQueueEnd(song) }
        }

        /**
         * Stoesst die Onset-Erkennung (A2, Marker/Waveform-Plan Phase 5) fuer
         * [song] an — explizit vom Nutzer ueber das Kontextmenue ("Drops
         * automatisch erkennen"). Kandidaten erscheinen als unbestaetigte
         * AUTO_DETECTED-Marker in der Review-Liste der Einstellungen.
         */
        fun detectDrops(song: Song) {
            viewModelScope.launch { trackAnalysisRepository.requestOnsetDetection(song) }
        }

        private fun <T> Flow<T>.asState(initial: T): StateFlow<T> =
            stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

        private fun comparatorFor(sort: SongSort): Comparator<Song> =
            when (sort) {
                SongSort.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayTitle() }
                SongSort.ARTIST -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist ?: "" }
                SongSort.ALBUM -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.album ?: "" }
                SongSort.DURATION -> compareBy { it.durationMs }
                SongSort.DATE_ADDED -> compareByDescending { it.dateModifiedSeconds }
            }

        private fun Song.displayTitle(): String = title ?: displayName

        private fun isHiRes(song: Song): Boolean = AudioFileFormat.fromFileName(song.displayName)?.hiResCapable == true

        private fun keyToView(key: String): LibraryView? = LibraryView.entries.firstOrNull { it.name == key }

        private fun reconcileOrder(keys: List<String>?): List<LibraryView> {
            if (keys == null) return DEFAULT_ORDER
            val known = keys.mapNotNull(::keyToView)
            val missing = DEFAULT_ORDER.filterNot { it in known }
            return known + missing
        }

        private companion object {
            val DEFAULT_ORDER: List<LibraryView> = LibraryView.entries.toList()
        }
    }
