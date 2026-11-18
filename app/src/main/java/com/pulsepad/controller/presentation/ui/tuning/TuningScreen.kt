package com.pulsepad.controller.presentation.ui.tuning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsepad.controller.R
import com.pulsepad.controller.domain.engine.LatencyExpectation
import com.pulsepad.controller.domain.model.MappedAction
import com.pulsepad.controller.service.accessibility.AccessibilityServiceBridge
import com.pulsepad.controller.presentation.viewmodel.TuningViewModel

/**
 * The Tuning destination.
 *
 * Two ideas live here and both are stated plainly: the interval is a CPU-versus-
 * responsiveness trade, and the latency figure is an estimate over a band, not a
 * measurement. The arming switch is the only thing that lets gestures leave the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuningScreen(
    modifier: Modifier = Modifier,
    viewModel: TuningViewModel = hiltViewModel()
) {
    val interval by viewModel.injectionIntervalMs.collectAsStateWithLifecycle()
    val turboRate by viewModel.turboRateHz.collectAsStateWithLifecycle()
    val armed by viewModel.armed.collectAsStateWithLifecycle()
    val gestureBlocked by viewModel.lastInjectionFailed.collectAsStateWithLifecycle()
    val serviceConnection by viewModel.serviceConnection.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val armRejected by viewModel.armRejected.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val armFailedMessage = stringResource(R.string.tuning_arm_failed)

    LaunchedEffect(armRejected) {
        if (armRejected) {
            viewModel.dismissArmRejection()
            snackbarHostState.showSnackbar(armFailedMessage)
        }
    }

    val serviceRunning = serviceConnection == AccessibilityServiceBridge.ConnectionState.CONNECTED
    val fireOsBlocked = capabilities?.isFireOs == true && !serviceRunning
    val estimatedLatency = LatencyExpectation.estimateForInterval(interval)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tuning_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EngineArmCard(
                armed = armed,
                serviceRunning = serviceRunning,
                onArmedChange = viewModel::setArmed
            )

            if (fireOsBlocked) {
                NoticeCard(
                    title = stringResource(R.string.tuning_fireos_title),
                    body = stringResource(R.string.tuning_fireos_body),
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            if (gestureBlocked && armed) {
                NoticeCard(
                    title = stringResource(R.string.tuning_gesture_blocked_title),
                    body = stringResource(R.string.tuning_gesture_blocked_body),
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            SliderCard(
                title = stringResource(R.string.tuning_injection_title),
                body = stringResource(R.string.tuning_injection_body),
                valueLabel = stringResource(R.string.tuning_injection_value, interval),
                value = interval.toFloat(),
                valueRange = LatencyExpectation.MIN_INTERVAL_MS.toFloat()..
                    LatencyExpectation.MAX_INTERVAL_MS.toFloat(),
                steps = INTERVAL_STEPS,
                onChange = { viewModel.setInjectionInterval(it.toInt()) },
                footnote = stringResource(R.string.tuning_interval_warning)
            )

            SliderCard(
                title = stringResource(R.string.tuning_turbo_title),
                body = stringResource(R.string.tuning_turbo_body),
                valueLabel = stringResource(R.string.mapping_turbo_rate_value, turboRate),
                value = turboRate.toFloat(),
                valueRange = MappedAction.Turbo.TURBO_MIN_HZ.toFloat()..
                    MappedAction.Turbo.TURBO_MAX_HZ.toFloat(),
                steps = TURBO_STEPS,
                onChange = { viewModel.setTurboRate(it.toInt()) }
            )

            LatencyCard(estimatedLatency = estimatedLatency)

            Spacer(Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun EngineArmCard(
    armed: Boolean,
    serviceRunning: Boolean,
    onArmedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.PowerSettingsNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.tuning_engine_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.tuning_arm_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = armed, onCheckedChange = onArmedChange)
            }
            Text(
                text = when {
                    !serviceRunning -> stringResource(R.string.tuning_unavailable_notice)
                    armed -> stringResource(R.string.tuning_armed_notice)
                    else -> stringResource(R.string.tuning_disarmed_notice)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (armed && serviceRunning) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun SliderCard(
    title: String,
    body: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    footnote: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.fillMaxWidth()
            )
            footnote?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LatencyCard(estimatedLatency: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.tuning_latency_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = stringResource(R.string.tuning_latency_value, estimatedLatency),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.tuning_latency_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoticeCard(
    title: String,
    body: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = content
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = content
            )
        }
    }
}

private const val INTERVAL_STEPS = 1
private const val TURBO_STEPS = 24
