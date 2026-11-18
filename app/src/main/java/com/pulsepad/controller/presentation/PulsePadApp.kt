package com.pulsepad.controller.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pulsepad.controller.presentation.ui.capability.CapabilityScreen

/**
 * Root composable. Phase 0 exposes only the capability report; the four-destination
 * navigation shell is introduced in Phase 2.
 */
@Composable
fun PulsePadApp(modifier: Modifier = Modifier) {
    CapabilityScreen(modifier = modifier)
}
