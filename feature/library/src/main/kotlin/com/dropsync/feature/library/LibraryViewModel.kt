package com.dropsync.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dropsync.core.common.AppError
import com.dropsync.core.common.onFailure
import com.dropsync.core.common.onSuccess
import com.dropsync.core.model.Song
import com.dropsync.domain.library.LibraryRepository
import com.dropsync.domain.playback.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Sichtbarer Zustand der Bibliothek (Schritt 12.3: Berechtigung -> Bibliothek -> Play). */
enum class LibraryError { NONE, PERMISSION_MISSING, SCAN_FAILED }

@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        private val libraryRepository: LibraryRepository,
        private val playbackRepository: PlaybackRepository,
    ) : ViewModel() {
        /** Liste kommt aus Room, nie direkt aus MediaStore (Schritt 13.6). */
        val songs: StateFlow<List<Song>> =
            libraryRepository.availableSongs.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyList(),
            )

        private val _error = MutableStateFlow(LibraryError.NONE)
        val error: StateFlow<LibraryError> = _error.asStateFlow()

        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

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

        /** Ersetzt die Queue durch die aktuelle Liste und startet bei [index]. */
        fun playFrom(index: Int) {
            val current = songs.value
            if (index !in current.indices) return
            viewModelScope.launch {
                playbackRepository.setQueue(current, index, playWhenReady = true)
            }
        }
    }
