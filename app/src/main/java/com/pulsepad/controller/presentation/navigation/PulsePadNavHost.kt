package com.pulsepad.controller.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pulsepad.controller.R
import com.pulsepad.controller.presentation.ui.capability.CapabilityScreen
import com.pulsepad.controller.presentation.ui.debug.InputDebugScreen
import com.pulsepad.controller.presentation.ui.devices.DevicesScreen
import com.pulsepad.controller.presentation.ui.profiles.ProfilesScreen
import com.pulsepad.controller.presentation.ui.studio.StudioScreen
import com.pulsepad.controller.presentation.ui.tuning.TuningScreen

/**
 * Navigation shell: a bottom bar over the four top-level destinations, plus the
 * input debug route which is a detail screen rather than a tab.
 *
 * State is preserved per tab and each tab pops back to its own root, so switching
 * away from the studio and returning does not rebuild a controller model.
 */
private const val ROUTE_INPUT_DEBUG = "input_debug"
private const val ROUTE_CAPABILITIES = "capabilities"

@Composable
fun PulsePadNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute != ROUTE_INPUT_DEBUG && currentRoute != ROUTE_CAPABILITIES

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                PulsePadBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.START.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(TopLevelDestination.DEVICES.route) {
                DevicesScreen(
                    onOpenInputDebug = { navController.navigate(ROUTE_INPUT_DEBUG) },
                    onOpenCapabilities = { navController.navigate(ROUTE_CAPABILITIES) }
                )
            }
            composable(TopLevelDestination.STUDIO.route) {
                StudioScreen()
            }
            composable(TopLevelDestination.TUNING.route) {
                TuningScreen()
            }
            composable(TopLevelDestination.PROFILES.route) {
                ProfilesScreen()
            }
            composable(ROUTE_INPUT_DEBUG) {
                InputDebugScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(ROUTE_CAPABILITIES) {
                CapabilityScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun PulsePadBottomBar(
    currentRoute: String?,
    onNavigate: (TopLevelDestination) -> Unit
) {
    val hierarchy = currentRoute
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = TopLevelDestination.fromRoute(hierarchy) == destination,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}
