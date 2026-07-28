package com.dropsync.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Markenpalette gemaess Design.txt (Repo-Wurzel): Schwarz erzeugt Fokus,
// Lime erzeugt Energie, Weiss erzeugt Ruhe. Lime ist ausschliesslich fuer
// die primaere Aktion reserviert; Kontraste bleiben erhalten (Bauplan 2.6:
// Lime #DFFF2F zu Schwarz #0D0D0D erfuellt AA deutlich).
private val BrandBlack = Color(0xFF0D0D0D)
private val BrandWhite = Color(0xFFFFFFFF)
private val BrandLime = Color(0xFFDFFF2F)
private val SoftGray = Color(0xFFF5F5F5)
private val BorderGray = Color(0xFFEAEAEA)
private val TextGray = Color(0xFF6B6B6B)

private val LightColors =
    lightColorScheme(
        primary = BrandLime,
        onPrimary = BrandBlack,
        secondary = BrandBlack,
        onSecondary = BrandWhite,
        tertiary = BrandBlack,
        onTertiary = BrandWhite,
        background = BrandWhite,
        onBackground = BrandBlack,
        surface = BrandWhite,
        onSurface = BrandBlack,
        surfaceVariant = SoftGray,
        onSurfaceVariant = TextGray,
        surfaceContainer = SoftGray,
        surfaceContainerLow = BrandWhite,
        surfaceContainerHigh = SoftGray,
        outline = BorderGray,
        outlineVariant = BorderGray,
    )

private val DarkColors =
    darkColorScheme(
        primary = BrandLime,
        onPrimary = BrandBlack,
        secondary = BrandWhite,
        onSecondary = BrandBlack,
        tertiary = BrandLime,
        onTertiary = BrandBlack,
        background = BrandBlack,
        onBackground = BrandWhite,
        surface = BrandBlack,
        onSurface = BrandWhite,
        surfaceVariant = Color(0xFF1A1A1A),
        onSurfaceVariant = Color(0xFFB3B3B3),
        surfaceContainer = Color(0xFF1A1A1A),
        surfaceContainerLow = Color(0xFF141414),
        surfaceContainerHigh = Color(0xFF222222),
        outline = Color(0xFF2A2A2A),
        outlineVariant = Color(0xFF2A2A2A),
    )

// Radien gemaess Design.txt: Cards 24, grosse Flaechen 32; Buttons sind
// in Material 3 bereits Pill-Shape.
private val BrandShapes =
    Shapes(
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(24.dp),
        large = RoundedCornerShape(32.dp),
    )

/**
 * Material-3-Theme mit System-Dark-/Light-Mode (Bauplan 2.6, Schritt 12.1).
 * Die Markenidentitaet (Design.txt) verlangt eine feste Schwarz/Weiss/
 * Lime-Palette; Dynamic Color ist deshalb standardmaessig aus und kann
 * bewusst aktiviert werden.
 */
@Composable
fun DropSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = BrandShapes,
        typography = DropSyncTypography,
        content = content,
    )
}
