package com.pulsepad.controller.presentation.ui.capability

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsepad.controller.R
import com.pulsepad.controller.domain.model.Capability
import com.pulsepad.controller.domain.model.CapabilityReport
import com.pulsepad.controller.presentation.viewmodel.CapabilityUiState
import com.pulsepad.controller.presentation.viewmodel.CapabilityViewModel
import com.pulsepad.controller.service.accessibility.AccessibilitySettingsNavigator

/**
 * Debug surface reporting the four runtime capabilities PulsePad depends on, so
 * every fallback path is visible before a feature relies on it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapabilityScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CapabilityViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.capability_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    Icon(
                        imageVector = Icons.Filled.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(16.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        CapabilityContent(
            state = state,
            onRefresh = viewModel::refresh,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
private fun CapabilityContent(
    state: CapabilityUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (state.loading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.capability_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(state.reports, key = { it.capability.name }) { report ->
            CapabilityRow(report = report)
        }

        item {
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.capability_refresh))
            }
        }

        if (state.capabilities?.accessibilityServiceEnabled == false) {
            item {
                OutlinedButton(
                    onClick = { AccessibilitySettingsNavigator.openSettings(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.capability_accessibility_open_settings))
                }
            }
        }

        if (state.capabilities?.isAndroidVersionSupported == false) {
            item {
                CompatibilityNotice(
                    title = stringResource(R.string.compat_unsupported_android_title),
                    body = stringResource(R.string.compat_unsupported_android_body)
                )
            }
        }
    }
}

@Composable
private fun CapabilityRow(report: CapabilityReport) {
    val presentation = report.presentation()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = presentation.icon,
                contentDescription = null,
                tint = presentation.tint,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(presentation.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(presentation.detailRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CompatibilityNotice(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class CapabilityPresentation(
    val icon: ImageVector,
    val titleRes: Int,
    val detailRes: Int,
    val tint: Color
)

@Composable
private fun CapabilityReport.presentation(): CapabilityPresentation {
    val success = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    return when (capability) {
        Capability.ACCESSIBILITY_SERVICE -> CapabilityPresentation(
            icon = Icons.Filled.Accessibility,
            titleRes = R.string.capability_accessibility_name,
            detailRes = if (available) R.string.capability_accessibility_ok
            else R.string.capability_accessibility_off,
            tint = if (available) success else MaterialTheme.colorScheme.error
        )

        Capability.OPEN_GL_ES_3 -> CapabilityPresentation(
            icon = if (available) Icons.Filled.CheckCircle else Icons.Filled.Info,
            titleRes = R.string.capability_gles_name,
            detailRes = if (available) R.string.capability_gles_ok
            else R.string.capability_gles_missing,
            tint = if (available) success else muted
        )

        Capability.BLUETOOTH_HID_HOST -> CapabilityPresentation(
            icon = Icons.Filled.Bluetooth,
            titleRes = R.string.capability_hid_host_name,
            detailRes = if (available) R.string.capability_hid_host_ok
            else R.string.capability_hid_host_missing,
            tint = if (available) success else muted
        )

        Capability.PLAY_SERVICES -> CapabilityPresentation(
            icon = Icons.Filled.Info,
            titleRes = R.string.capability_play_services_name,
            detailRes = if (available) R.string.capability_play_services_present
            else R.string.capability_play_services_absent,
            tint = MaterialTheme.colorScheme.secondary
        )
    }
}
