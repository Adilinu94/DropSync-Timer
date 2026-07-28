package com.dropsync.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dropsync.feature.audio.AudioSettingsScreen
import com.dropsync.feature.library.LibraryScreen
import com.dropsync.feature.player.DropRestCard
import com.dropsync.feature.player.MiniPlayer
import com.dropsync.feature.settings.SettingsScreen
import com.dropsync.feature.timer.TimerSection
import com.dropsync.feature.workout.WorkoutFeature

/**
 * Hauptnavigation mit genau drei Zielen (Bauplan 12.2): Musik, Training,
 * Einstellungen. Kompakt: Bottom Navigation; ab Medium: Navigation Rail
 * per Window Size Classes, keine Geraetemodellabfragen (12.6).
 */
enum class TopLevelDestination(
    val route: String,
    val icon: ImageVector,
    val labelRes: Int,
) {
    MUSIC("music", Icons.Filled.LibraryMusic, R.string.nav_music),
    TRAINING("training", Icons.Filled.FitnessCenter, R.string.nav_training),
    SETTINGS("settings", Icons.Filled.Settings, R.string.nav_settings),
}

/**
 * Unterseite der Einstellungen (kein viertes Hauptziel): Audio/DSP-Regler.
 * Erreichbar ueber den Audio-Einstieg in [SettingsScreen].
 */
private const val ROUTE_AUDIO_SETTINGS = "audio_settings"

@Composable
fun DropSyncApp(windowSizeClass: WindowSizeClass) {
    val navController = rememberNavController()
    val useRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    if (useRail) {
        Row(modifier = Modifier.fillMaxSize()) {
            DropSyncNavigationRail(navController)
            DropSyncContent(navController, showBottomBar = false)
        }
    } else {
        DropSyncContent(navController, showBottomBar = true)
    }
}

@Composable
private fun DropSyncContent(
    navController: NavHostController,
    showBottomBar: Boolean,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Column {
                // Der aktive Mini-Player bleibt in der Shell sichtbar (12.2).
                MiniPlayer()
                if (showBottomBar) {
                    DropSyncNavigationBar(navController)
                }
            }
        },
    ) { innerPadding ->
        DropSyncNavHost(navController, innerPadding)
    }
}

@Composable
private fun DropSyncNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.MUSIC.route,
    ) {
        composable(TopLevelDestination.MUSIC.route) {
            LibraryScreen(contentPadding = contentPadding)
        }
        composable(TopLevelDestination.TRAINING.route) {
            // Timer und Trainingslog teilen sich den Trainingskontext; nur
            // :app kennt beide Features (Modulregel 3.2).
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = contentPadding.calculateTopPadding()),
            ) {
                TimerSection()
                // Drop-Rest gehoert fachlich zum Satzende im Training (11.2);
                // die Karte lebt in :feature:player, weil sie Playback braucht.
                DropRestCard(modifier = Modifier.padding(horizontal = 16.dp))
                // Interner NavHost des Trainings-Tabs (Session, Bibliothek,
                // Routinen, Fortschritt); Timer/DropRest bleiben sichtbar.
                WorkoutFeature(
                    contentPadding =
                        PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        composable(TopLevelDestination.SETTINGS.route) {
            SettingsScreen(
                contentPadding = contentPadding,
                onOpenAudioSettings = { navController.navigate(ROUTE_AUDIO_SETTINGS) },
            )
        }
        composable(ROUTE_AUDIO_SETTINGS) {
            AudioSettingsScreen(
                contentPadding = contentPadding,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun DropSyncNavigationBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val label = stringResource(destination.labelRes)
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { navController.navigateTopLevel(destination.route) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun DropSyncNavigationRail(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    NavigationRail {
        TopLevelDestination.entries.forEach { destination ->
            val label = stringResource(destination.labelRes)
            NavigationRailItem(
                selected = currentRoute == destination.route,
                onClick = { navController.navigateTopLevel(destination.route) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(label) },
            )
        }
    }
}

/** Standard-Navigationsmuster: ein Backstack-Eintrag je Top-Level-Ziel. */
private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
