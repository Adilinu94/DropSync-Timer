package com.dropsync.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dropsync.core.model.SongMarker
import com.dropsync.domain.library.MarkerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        markerRepository: MarkerRepository,
    ) : ViewModel() {
        /** Nicht zugeordnete Marker fuer die manuelle Zuordnung (Schritt 6.6). */
        val unmatchedMarkers: StateFlow<List<SongMarker>> =
            markerRepository.unmatchedMarkers.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyList(),
            )
    }
