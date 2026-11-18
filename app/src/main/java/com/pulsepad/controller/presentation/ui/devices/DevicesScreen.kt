package com.pulsepad.controller.presentation.ui.devices

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsepad.controller.R
import com.pulsepad.controller.domain.model.ControllerConnectionState
import com.pulsepad.controller.domain.model.ControllerDevice
import com.pulsepad.controller.domain.model.ControllerFamily
import com.pulsepad.controller.presentation.ui.components.AccessibilityBanner
import com.pulsepad.controller.presentation.viewmodel.AccessibilityStatusViewModel
import com.pulsepad.controller.presentation.viewmodel.InputDebugViewModel

/**
 * Primary destination: what is paired, what is connected, and what to do next.
 *
 * The screen has to stay useful when nothing works - no controller paired, no HID
 * host profile, no accessibility service - so every one of those states has an
 * explicit card or banner rather than an empty list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onOpenInputDebug: () -> Unit,
    onOpenCapabilities: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InputDebugViewModel = hiltViewModel(),
    accessibilityViewModel: AccessibilityStatusViewModel = hiltViewModel()
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val accessibilityEnabled by accessibilityViewModel.enabledInSettings
        .collectAsStateWithLifecycle()
    val serviceKilled by accessibilityViewModel.serviceKilled.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val disconnectedMessage = stringResource(R.string.edge_controller_disconnected)

    // A disconnect is reported once, on the transition into Disconnected. Watching
    // the state itself would re-show the snackbar on every recomposition while the
    // controller stays disconnected.
    var wasConnected by remember { mutableStateOf(false) }
    LaunchedEffect(connectionState) {
        val connectedNow = connectionState is ControllerConnectionState.Connected
        if (wasConnected && !connectedNow) {
            snackbarHostState.showSnackbar(disconnectedMessage)
        }
        wasConnected = connectedNow
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.devices_title)) },
                actions = {
                    IconButton(onClick = onOpenCapabilities) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = stringResource(R.string.capability_title)
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
            if (!accessibilityEnabled) {
                item {
                    AccessibilityBanner(
                        onEnableClick = accessibilityViewModel::openAccessibilitySettings
                    )
                }
            } else if (serviceKilled) {
                item {
                    ServiceKilledCard(
                        onReEnable = accessibilityViewModel::openAccessibilitySettings,
                        onDismiss = accessibilityViewModel::dismissServiceKilled
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.devices_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                        Text(stringResource(R.string.devices_scanning))
                    } else {
                        Icon(Icons.Filled.BluetoothSearching, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.devices_scan))
                    }
                }
            }

            if (devices.isEmpty()) {
                item { EmptyStateCard(onOpenBluetoothSettings = viewModel::openBluetoothSettings) }
            } else {
                item {
                    Text(
                        text = stringResource(R.string.devices_section_available),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(items = devices, key = { it.stableId }) { device ->
                    DeviceCard(
                        device = device,
                        connectionState = connectionState,
                        onConnect = { viewModel.connect(device) }
                    )
                }
            }

            item {
                FilledTonalButton(
                    onClick = onOpenInputDebug,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Memory, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.devices_input_debug))
                }
            }
        }
    }
}

/**
 * Shown when the OS stopped the accessibility service while PulsePad was running.
 *
 * It is dismissable, unlike the "never enabled" banner: the user has already been
 * through the settings flow, so this is a nudge rather than an explanation.
 */
@Composable
private fun ServiceKilledCard(onReEnable: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.edge_service_killed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onReEnable) {
                    Text(stringResource(R.string.edge_re_enable_action))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.edge_dismiss))
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(onOpenBluetoothSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Bluetooth,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.devices_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.devices_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onOpenBluetoothSettings) {
                Icon(Icons.Filled.Bluetooth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.devices_empty_action))
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: ControllerDevice,
    connectionState: ControllerConnectionState,
    onConnect: () -> Unit
) {
    val connected = connectionState as? ControllerConnectionState.Connected
    val isConnected = connected?.device?.stableId == device.stableId
    val connecting = connectionState as? ControllerConnectionState.Connecting
    val isConnecting = connecting?.device?.stableId == device.stableId

    val accent = animateColorAsState(
        targetValue = if (isConnected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "deviceAccent"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.SportsEsports,
                    contentDescription = null,
                    tint = accent.value
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
                ConnectionStatusPill(isConnected = isConnected, isConnecting = isConnecting)
            }

            Spacer(Modifier.height(16.dp))

            if (isConnected) {
                OutlinedButton(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.LinkOff, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.devices_disconnect))
                }
            } else {
                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConnecting
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Filled.Link, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.devices_connect))
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusPill(isConnected: Boolean, isConnecting: Boolean) {
    val label = when {
        isConnecting -> stringResource(R.string.devices_status_connecting)
        isConnected -> stringResource(R.string.devices_connected)
        else -> stringResource(R.string.devices_bonded)
    }
    val container = when {
        isConnected -> MaterialTheme.colorScheme.primaryContainer
        isConnecting -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Surface(shape = RoundedCornerShape(50), color = container) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun ControllerFamily.labelRes(): Int = when (this) {
    ControllerFamily.DUALSHOCK_4 -> R.string.input_debug_family_dualshock4
    ControllerFamily.DUALSENSE -> R.string.input_debug_family_dualsense
    ControllerFamily.XBOX_WIRELESS -> R.string.input_debug_family_xbox
    ControllerFamily.UNKNOWN -> R.string.input_debug_family_unknown
}
