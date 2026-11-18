package com.pulsepad.controller.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled._3dRotation
import androidx.compose.ui.graphics.vector.ImageVector
import com.pulsepad.controller.R

/**
 * The four top-level destinations shown in the bottom navigation bar.
 *
 * Each entry owns its route, its label resource and its icon so the navigation
 * bar and the graph cannot drift apart.
 */
enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
) {
    DEVICES(
        route = "devices",
        labelRes = R.string.nav_devices,
        icon = Icons.Filled.Gamepad
    ),
    STUDIO(
        route = "studio",
        labelRes = R.string.nav_studio,
        icon = Icons.Filled._3dRotation
    ),
    TUNING(
        route = "tuning",
        labelRes = R.string.nav_tuning,
        icon = Icons.Filled.Tune
    ),
    PROFILES(
        route = "profiles",
        labelRes = R.string.nav_profiles,
        icon = Icons.Filled.Bookmarks
    );

    companion object {
        val START = DEVICES

        fun fromRoute(route: String?): TopLevelDestination =
            entries.firstOrNull { it.route == route } ?: START
    }
}
