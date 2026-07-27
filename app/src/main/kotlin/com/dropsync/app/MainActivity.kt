package com.dropsync.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.dropsync.core.designsystem.theme.DropSyncTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Einzige Activity; hostet die Navigation mit den drei Zielen Musik,
 * Training, Einstellungen (Bauplan Schritt 12.2). Die Shell passt sich
 * ueber Window Size Classes an (12.6).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DropSyncTheme {
                DropSyncApp(windowSizeClass = calculateWindowSizeClass(this))
            }
        }
    }
}
