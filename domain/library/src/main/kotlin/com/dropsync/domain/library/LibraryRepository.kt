package com.dropsync.domain.library

import com.dropsync.core.common.AppResult
import com.dropsync.core.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * Ergebnis eines Bibliotheksabgleichs (Bauplan Schritt 4).
 * [skippedBecauseUnchanged] ist true, wenn der MediaStore-Aenderungsstand
 * unveraendert war und deshalb kein Vollscan lief (Schritt 4.3).
 */
data class LibraryScanResult(
    val skippedBecauseUnchanged: Boolean,
    val totalSongs: Int,
    val newOrUpdatedSongs: Int,
    val markedUnavailable: Int,
)

/**
 * Vertrag der lokalen Musikbibliothek (ADR-0003).
 * Implementierung liegt in :data:library (MediaStore + Room).
 */
interface LibraryRepository {
    /** Alle bekannten Songs, auch nicht verfuegbare (fuer Historie/Marker). */
    val songs: Flow<List<Song>>

    /** Nur abspielbare Songs. */
    val availableSongs: Flow<List<Song>>

    /**
     * Gleicht die Bibliothek mit MediaStore ab. Ohne [force] wird der
     * Abgleich uebersprungen, wenn sich der MediaStore-Stand nicht
     * geaendert hat. Fehlende Berechtigung liefert
     * AppError.PermissionDenied; es gibt keinen stillen leeren Screen.
     */
    suspend fun refreshLibrary(force: Boolean): AppResult<LibraryScanResult>

    suspend fun getSong(mediaStoreId: Long): AppResult<Song>

    /** Markiert einen Song mit nicht mehr lesbarer URI (Schritt 4.4). */
    suspend fun markUnavailable(mediaStoreId: Long): AppResult<Unit>
}
