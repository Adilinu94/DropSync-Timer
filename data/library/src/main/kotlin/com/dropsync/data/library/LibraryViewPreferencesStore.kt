package com.dropsync.data.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dropsync.domain.library.LibraryViewConfig
import com.dropsync.domain.library.LibraryViewPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-Persistenz der Bibliotheksansichts-Konfiguration (Plan Phase 6.4).
 * Reihenfolge und ausgeblendete Ansichten werden als komma-getrennte
 * Schluessellisten gespeichert (die Schluessel sind Enum-Namen ohne Komma).
 * Fehlt der Eintrag, liefert [config] null = noch nicht konfiguriert.
 */
class LibraryViewPreferencesStore(
    private val dataStore: DataStore<Preferences>,
) : LibraryViewPreferencesRepository {
    override val config: Flow<LibraryViewConfig?> =
        dataStore.data.map { prefs ->
            val order =
                prefs[KEY_ORDER]
                    ?.takeIf { it.isNotEmpty() }
                    ?.split(SEP)
                    ?: return@map null
            val hidden =
                prefs[KEY_HIDDEN]
                    ?.takeIf { it.isNotEmpty() }
                    ?.split(SEP)
                    ?.toSet()
                    ?: emptySet()
            LibraryViewConfig(orderedKeys = order, hiddenKeys = hidden)
        }

    override suspend fun setConfig(config: LibraryViewConfig) {
        dataStore.edit { prefs ->
            prefs[KEY_ORDER] = config.orderedKeys.joinToString(SEP)
            prefs[KEY_HIDDEN] = config.hiddenKeys.joinToString(SEP)
        }
    }

    companion object {
        const val DATA_STORE_NAME = "library_view_prefs"
        private const val SEP = ","
        private val KEY_ORDER = stringPreferencesKey("view_order")
        private val KEY_HIDDEN = stringPreferencesKey("view_hidden")
    }
}
