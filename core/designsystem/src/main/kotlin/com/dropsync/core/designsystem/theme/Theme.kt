package com.dropsync.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Markenfarben ergaenzen Dynamic Color, reduzieren aber keine Kontraste
// (Bauplan 2.6). Die konkreten Kontrastpruefungen erfolgen in Schritt 12.
private val LightColors =
    lightColorScheme(
        primary = Color(0xFF3D5AFE),
        onPrimary = Color(0xFFFFFFFF),
        secondary = Color(0xFF00695C),
        onSecondary = Color(0xFFFFFFFF),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFB3C0FF),
        onPrimary = Color(0xFF00159E),
        secondary = Color(0xFF4DB6AC),
        onSecondary = Color(0xFF003731),
    )

/**
 * Material-3-Theme mit System-Dark-/Light-Mode (Bauplan 2.6, Schritt 12.1).
 * Dynamic Color wird ab Android 12 verwendet, sonst die Markenpalette.
 */
@Composable
fun DropSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> {
                DarkColors
            }

            else -> {
                LightColors
            }
        }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
