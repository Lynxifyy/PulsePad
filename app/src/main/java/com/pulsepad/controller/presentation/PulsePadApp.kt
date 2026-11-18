package com.pulsepad.controller.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsepad.controller.presentation.navigation.PulsePadNavHost
import com.pulsepad.controller.presentation.ui.onboarding.OnboardingScreen
import com.pulsepad.controller.presentation.viewmodel.OnboardingViewModel

/**
 * Root composable: applies the app surface and hosts the navigation graph.
 *
 * Onboarding is shown once, decided by a persisted flag. While the flag is being
 * read it renders nothing rather than defaulting to either branch, so a returning
 * user never sees the onboarding flash on launch.
 */
@Composable
fun PulsePadApp(
    modifier: Modifier = Modifier,
    onboardingViewModel: OnboardingViewModel = hiltViewModel()
) {
    val onboardingCompleted by onboardingViewModel.completed.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (onboardingCompleted) {
            null -> Unit
            false -> OnboardingScreen(onFinished = { })
            true -> PulsePadNavHost()
        }
    }
}
