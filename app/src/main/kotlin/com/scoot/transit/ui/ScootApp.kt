package com.scoot.transit.ui

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.scoot.transit.R
import com.scoot.transit.ui.bart.BartScreen
import com.scoot.transit.ui.caltrain.CaltrainScreen
import com.scoot.transit.ui.caltrain.StationDetailScreen
import com.scoot.transit.ui.settings.SettingsScreen
import com.scoot.transit.ui.wayfinding.WayfindingScreen

private sealed class Tab(val route: String, val labelRes: Int, val icon: ImageVector) {
    data object Caltrain : Tab("caltrain", R.string.tab_caltrain, Icons.Filled.Train)
    data object Wayfinding : Tab("wayfinding", R.string.tab_wayfinding, Icons.Filled.Map)
    data object Bart : Tab("bart", R.string.tab_bart, Icons.Filled.DirectionsTransit)
    data object Settings : Tab("settings", R.string.tab_settings, Icons.Filled.Settings)
}

private val tabs = listOf(Tab.Caltrain, Tab.Wayfinding, Tab.Bart, Tab.Settings)

@Composable
fun ScootApp(deepLinkIntent: Intent? = null) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResLocal(tab.labelRes)) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = startDestinationFor(deepLinkIntent),
            modifier = Modifier.padding(padding)
        ) {
            composable(Tab.Caltrain.route) { CaltrainScreen(nav) }
            composable(Tab.Wayfinding.route) {
                WayfindingScreen(
                    presetId = deepLinkIntent?.getStringExtra(EXTRA_PRESET_ID),
                    onConsumePreset = { deepLinkIntent?.removeExtra(EXTRA_PRESET_ID) }
                )
            }
            composable(Tab.Bart.route) { BartScreen() }
            composable(Tab.Settings.route) { SettingsScreen() }
            composable(
                "station/{agency}/{stopId}",
                arguments = listOf(
                    androidx.navigation.navArgument("agency") {
                        type = androidx.navigation.NavType.StringType
                    },
                    androidx.navigation.navArgument("stopId") {
                        type = androidx.navigation.NavType.StringType
                    }
                )
            ) { entry ->
                val agency = entry.arguments?.getString("agency") ?: "CT"
                val stopId = entry.arguments?.getString("stopId") ?: ""
                StationDetailScreen(agency = agency, stopId = stopId, onBack = { nav.popBackStack() })
            }
        }
    }
}

private fun startDestinationFor(intent: Intent?): String {
    if (intent?.action == ACTION_OPEN_PRESET) return Tab.Wayfinding.route
    return Tab.Caltrain.route
}

const val ACTION_OPEN_PRESET = "com.scoot.transit.action.OPEN_PRESET"
const val EXTRA_PRESET_ID = "preset_id"

@Composable
private fun stringResLocal(id: Int): String =
    androidx.compose.ui.res.stringResource(id = id)
