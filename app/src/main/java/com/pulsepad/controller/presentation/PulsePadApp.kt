package com.pulsepad.controller.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pulsepad.controller.presentation.navigation.PulsePadNavHost

/**
 * Root composable: applies the app surface and hosts the navigation graph.
 */
@Composable
fun PulsePadApp(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        PulsePadNavHost()
    }
}
