package com.dropsync.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dropsync.core.common.getOrNull
import com.dropsync.domain.library.LibraryRepository
import com.dropsync.domain.playback.PlaybackRepository
import com.dropsync.domain.playback.QueueItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Zustand des Mini-Players (Schritt 12.2). */
data class MiniPlayerState(
    val isVisible: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String? = null,
)

/**
 * Zustand des Now-Playing-Screens (Marker/Waveform-Plan Phase 1):
 * breitere Projektion derselben `playbackRepository.state`-Quelle, die
 * auch den Mini-Player speist — keine zweite Wahrheit.
 */
data class NowPlayingUiState(
    val isVisible: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val artist: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** MediaStore-ID des laufenden Songs (5.1); null bei leerer Queue. */
    val songId: Long? = null,
    /** Content-URI fuer den Cover-Art-Lader (MediaMetadataRetriever). */
    val contentUri: String? = null,
)

/** Zustand des Queue-Editors (Plan Phase 6, Punkt 3). */
data class QueueUiState(
    val items: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
)

@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        private val playbackRepository: PlaybackRepository,
        private val libraryRepository: LibraryRepository,
    ) : ViewModel() {
        @OptIn(ExperimentalCoroutinesApi::class)
        val miniPlayer: StateFlow<MiniPlayerState> =
            playbackRepository.state
                .mapLatest { state ->
                    val songId = state.currentSongId
                    if (songId == null) {
                        MiniPlayerState()
                    } else {
                        val song = libraryRepository.getSong(songId).getOrNull()
                        MiniPlayerState(
                            isVisible = true,
                            isPlaying = state.isPlaying,
                            title = song?.title ?: song?.displayName ?: "",
                            artist = song?.artist,
                        )
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MiniPlayerState())

        fun togglePlayPause() {
            viewModelScope.launch {
                if (miniPlayer.value.isPlaying) {
                    playbackRepository.pause()
                } else {
                    playbackRepository.play()
                }
            }
        }

        fun skipToNext() {
            viewModelScope.launch { playbackRepository.skipToNext() }
        }

        fun skipToPrevious() {
            viewModelScope.launch { playbackRepository.skipToPrevious() }
        }

        fun seekTo(positionMs: Long) {
            viewModelScope.launch { playbackRepository.seekTo(positionMs) }
        }

        /** Now-Playing-Projektion (Marker/Waveform-Plan Phase 1). */
        @OptIn(ExperimentalCoroutinesApi::class)
        val nowPlaying: StateFlow<NowPlayingUiState> =
            playbackRepository.state
                .mapLatest { state ->
                    val songId = state.currentSongId
                    if (songId == null) {
                        NowPlayingUiState()
                    } else {
                        val song = libraryRepository.getSong(songId).getOrNull()
                        NowPlayingUiState(
                            isVisible = true,
                            isPlaying = state.isPlaying,
                            title = song?.title ?: song?.displayName ?: "",
                            artist = song?.artist,
                            positionMs = state.positionMs,
                            durationMs = state.durationMs,
                            songId = songId,
                            contentUri = song?.contentUri,
                        )
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowPlayingUiState())

        private val tickedPositionMs = MutableStateFlow<Long?>(null)

        /**
         * Live-Position aus dem Ticker des Now-Playing-Screens; null,
         * solange kein Tick vorliegt (dann gilt [NowPlayingUiState.positionMs]).
         */
        val livePositionMs: StateFlow<Long?> = tickedPositionMs.asStateFlow()

        /**
         * Ein Ticker-Schritt: fragt `snapshotNow()` ab, weil `state` die
         * Position nur bei Player-Ereignissen aktualisiert. Wird nur vom
         * sichtbaren Now-Playing-Screen aufgerufen (kein Hintergrund-Polling).
         */
        fun refreshPosition() {
            viewModelScope.launch {
                playbackRepository.snapshotNow().getOrNull()?.let {
                    tickedPositionMs.value = it.positionMs
                }
            }
        }

        /** Beobachtbare Warteschlange fuer den Queue-Editor (Plan Phase 6). */
        val queue: StateFlow<QueueUiState> =
            playbackRepository.state
                .map { QueueUiState(items = it.queue, currentIndex = it.currentIndex) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QueueUiState())

        fun playQueueItem(index: Int) {
            viewModelScope.launch { playbackRepository.skipToQueueIndex(index) }
        }

        fun moveQueueItem(
            fromIndex: Int,
            toIndex: Int,
        ) {
            viewModelScope.launch { playbackRepository.moveInQueue(fromIndex, toIndex) }
        }

        fun removeQueueItem(index: Int) {
            viewModelScope.launch { playbackRepository.removeFromQueue(index) }
        }
    }
