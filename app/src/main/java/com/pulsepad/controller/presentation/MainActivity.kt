package com.pulsepad.controller.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pulsepad.controller.presentation.theme.PulsePadTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host. All screens are Compose destinations; the accessibility
 * service and the input capture service are separate, system-managed components.
 *
 * `configChanges` in the manifest means rotations do not recreate the activity,
 * which keeps the SceneView engine and the armed injection state alive across
 * orientation changes.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PulsePadTheme {
                PulsePadApp()
            }
        }
    }
}
