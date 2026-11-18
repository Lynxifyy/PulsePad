package com.pulsepad.controller.presentation.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsepad.controller.R
import com.pulsepad.controller.domain.model.ConnectionErrorReason
import com.pulsepad.controller.domain.model.ControllerAxis
import com.pulsepad.controller.domain.model.ControllerConnectionState
import com.pulsepad.controller.domain.model.ControllerDevice
import com.pulsepad.controller.domain.model.ControllerFamily
import com.pulsepad.controller.domain.model.ControllerInput
import com.pulsepad.controller.domain.model.ControllerState
import com.pulsepad.controller.domain.model.DiscoverySource
import com.pulsepad.controller.presentation.viewmodel.InputDebugViewModel

/**
 * Phase 1 deliverable: a debug view proving that a connected controller's buttons
 * and axes arrive as the expected canonical values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputDebugScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InputDebugViewModel = hiltViewModel()
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val hidHostUnavailable by viewModel.hidHostUnavailable.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val controllerState by viewModel.controllerState.collectAsStateWithLifecycle()
    val lastInput by viewModel.lastInput.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.input_debug_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.input_debug_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (hidHostUnavailable) {
                item {
                    NoticeCard(
                        title = stringResource(R.string.input_debug_hid_fallback_title),
                        body = stringResource(R.string.input_debug_hid_fallback_body),
                        actionLabel = stringResource(R.string.input_debug_open_bluetooth_settings),
                        onAction = viewModel::openBluetoothSettings
                    )
                }
            }

            item {
                Button(
                    onClick = viewModel::scan,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isScanning
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.input_debug_scanning))
                    } else {
                        Icon(Icons.Filled.BluetoothSearching, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.input_debug_scan))
                    }
                }
            }

            if (devices.isEmpty()) {
                item { EmptyDevicesCard() }
            } else {
                items(count = devices.size, key = { devices[it].stableId }) { index ->
                    val device = devices[index]
                    DeviceRow(
                        device = device,
                        connectionState = connectionState,
                        onConnect = { viewModel.connect(device) }
                    )
                }
            }

            val errorReason = (connectionState as? ControllerConnectionState.Error)?.reason
            if (errorReason != null) {
                item {
                    Text(
                        text = stringResource(errorReason.messageRes()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            item { InputSnapshotCard(controllerState = controllerState, lastInput = lastInput) }
        }
    }
}

@Composable
private fun EmptyDevicesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Bluetooth,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.input_debug_no_devices_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.input_debug_no_devices_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeviceRow(
    device: ControllerDevice,
    connectionState: ControllerConnectionState,
    onConnect: () -> Unit
) {
    val connected = connectionState as? ControllerConnectionState.Connected
    val isConnected = connected?.device?.stableId == device.stableId

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.SportsEsports,
                    contentDescription = null,
                    tint = if (isConnected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(device.family.labelRes()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(
                    text = stringResource(device.source.labelRes()),
                    container = MaterialTheme.colorScheme.surfaceContainerHigh
                )
                StatusChip(
                    text = if (isConnected) {
                        stringResource(R.string.input_debug_connected)
                    } else {
                        stringResource(R.string.input_debug_not_connected)
                    },
                    container = if (isConnected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isConnected
            ) {
                Text(stringResource(R.string.input_debug_connect))
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, container: Color) {
    AssistChip(
        onClick = {},
        label = { Text(text) },
        colors = AssistChipDefaults.assistChipColors(containerColor = container)
    )
}

@Composable
private fun InputSnapshotCard(
    controllerState: ControllerState,
    lastInput: ControllerInput?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.input_debug_buttons_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))

            if (controllerState.pressedButtons.isEmpty()) {
                Text(
                    text = stringResource(R.string.input_debug_no_input_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = controllerState.pressedButtons
                        .sortedBy { it.ordinal }
                        .joinToString(separator = "  ") { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.input_debug_axes_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))

            ControllerAxis.entries.forEach { axis ->
                AxisRow(axis = axis, value = controllerState.axes[axis])
            }

            if (lastInput != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.input_debug_last_event),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = lastInput.describe(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun AxisRow(axis: ControllerAxis, value: Float?) {
    val resolved = value ?: 0f
    val isTrigger = axis == ControllerAxis.L2 || axis == ControllerAxis.R2
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = axis.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        // Sticks are bipolar (-1..1) so the bar fills from the centre; triggers are
        // unipolar (0..1) so the bar fills from the left.
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(4.dp)
                )
        ) {
            val fraction = if (isTrigger) resolved else (resolved + 1f) / 2f
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.input_debug_axis_value, resolved),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun NoticeCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = onAction) {
                    Icon(Icons.Filled.Settings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}

private fun ConnectionErrorReason.messageRes(): Int = when (this) {
    ConnectionErrorReason.HID_HOST_UNAVAILABLE -> R.string.input_debug_error_hid_unavailable
    ConnectionErrorReason.MISSING_PERMISSION -> R.string.input_debug_error_permission
    ConnectionErrorReason.SYSTEM_REJECTED -> R.string.input_debug_error_system_rejected
}

private fun ControllerFamily.labelRes(): Int = when (this) {
    ControllerFamily.DUALSHOCK_4 -> R.string.input_debug_family_dualshock4
    ControllerFamily.DUALSENSE -> R.string.input_debug_family_dualsense
    ControllerFamily.XBOX_WIRELESS -> R.string.input_debug_family_xbox
    ControllerFamily.UNKNOWN -> R.string.input_debug_family_unknown
}

private fun DiscoverySource.labelRes(): Int = when (this) {
    DiscoverySource.HID_HOST -> R.string.input_debug_source_hid_host
    DiscoverySource.BONDED -> R.string.input_debug_source_bonded
    DiscoverySource.INPUT_DEVICE -> R.string.input_debug_source_input_device
}

private fun ControllerInput.describe(): String = when (this) {
    is ControllerInput.Button -> "$button ${if (pressed) "down" else "up"}"
    is ControllerInput.Axis -> "$axis = $value"
}
