package com.dropsync.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * App-Einstieg; enthaelt keine Fachlogik (Bauplan 3.2/5).
 * Hilt stellt die wenigen langlebigen Objekte bereit (Schritt 2.4).
 */
@HiltAndroidApp
class DropSyncApplication : Application()
