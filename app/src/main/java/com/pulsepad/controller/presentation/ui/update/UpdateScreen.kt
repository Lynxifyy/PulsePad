package com.pulsepad.controller.presentation.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsepad.controller.R
import com.pulsepad.controller.presentation.viewmodel.UpdatePhase
import com.pulsepad.controller.presentation.viewmodel.UpdateUiState
import com.pulsepad.controller.presentation.viewmodel.UpdateViewModel
import com.pulsepad.controller.util.AppLog

/**
 * Update screen: checks the GitHub releases feed and, when a newer build exists,
 * downloads and hands it to the system installer.
 *
 * The screen is honest about the two things that can stop an install: the device
 * may not allow PulsePad to install packages, and the downloaded build must be
 * signed with the same key as the one already installed. Both are explained here
 * rather than surfacing as a bare failure from the system installer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UpdateViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var includePrerelease by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.update_title)) },
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
                    text = stringResource(
                        R.string.update_current_version,
                        state.currentVersion.toString()
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                PrereleaseRow(
                    checked = includePrerelease,
                    enabled = state.phase != UpdatePhase.CHECKING &&
                        state.phase != UpdatePhase.DOWNLOADING,
                    onCheckedChange = { includePrerelease = it }
                )
            }

            item {
                when (state.phase) {
                    UpdatePhase.IDLE -> CheckPromptCard(
                        onCheck = { viewModel.checkForUpdates(includePrerelease) }
                    )
                    UpdatePhase.CHECKING -> CheckingCard()
                    UpdatePhase.UP_TO_DATE -> UpToDateCard(
                        version = state.currentVersion.toString(),
                        onCheckAgain = { viewModel.checkForUpdates(includePrerelease) }
                    )
                    UpdatePhase.UPDATE_AVAILABLE -> AvailableCard(
                        state = state,
                        onInstall = viewModel::downloadAndInstall,
                        onOpenPermission = {
                            context.startActivity(viewModel.installPermissionIntent())
                        }
                    )
                    UpdatePhase.DOWNLOADING -> DownloadingCard()
                    UpdatePhase.INSTALLING -> InstallingCard()
                    UpdatePhase.FAILED -> FailedCard(
                        onRetry = { viewModel.checkForUpdates(includePrerelease) }
                    )
                }
            }

            state.update?.takeIf {
                state.phase == UpdatePhase.UPDATE_AVAILABLE || state.phase == UpdatePhase.FAILED
            }?.let { update ->
                item {
                    OutlinedButton(
                        onClick = { openUrl(context, update.releasePageUrl) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.update_open_release_page))
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.update_same_signer_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PrereleaseRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.update_prerelease_toggle),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

@Composable
private fun CheckPromptCard(onCheck: () -> Unit) {
    InfoCard(
        icon = Icons.Filled.SystemUpdate,
        title = stringResource(R.string.update_title),
        body = stringResource(R.string.update_checking)
    ) {
        FilledTonalButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.update_check_action))
        }
    }
}

@Composable
private fun CheckingCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.update_checking),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun UpToDateCard(version: String, onCheckAgain: () -> Unit) {
    InfoCard(
        icon = Icons.Filled.CheckCircle,
        title = stringResource(R.string.update_up_to_date_title),
        body = stringResource(R.string.update_up_to_date_body, version)
    ) {
        OutlinedButton(onClick = onCheckAgain, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.update_check_again_action))
        }
    }
}

@Composable
private fun AvailableCard(
    state: UpdateUiState,
    onInstall: () -> Unit,
    onOpenPermission: () -> Unit
) {
    val update = state.update ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.update_available_title, update.version.toString()),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    R.string.update_available_body,
                    state.currentVersion.toString()
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            if (update.apkSizeBytes > 0) {
                Text(
                    text = stringResource(
                        R.string.update_download_size,
                        formatBytes(update.apkSizeBytes)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.canSelfInstall) {
                Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.update_install_action))
                }
            } else {
                PermissionCard(onOpenPermission = onOpenPermission)
            }

            if (update.releaseNotes.isNotBlank()) {
                Text(
                    text = stringResource(R.string.update_release_notes_title),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = update.releaseNotes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = RELEASE_NOTES_MAX_LINES,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(onOpenPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.update_permission_title),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Text(
                text = stringResource(R.string.update_permission_body),
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = onOpenPermission, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.update_permission_action))
            }
        }
    }
}

@Composable
private fun DownloadingCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.update_downloading),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun InstallingCard() {
    InfoCard(
        icon = Icons.Filled.SystemUpdate,
        title = stringResource(R.string.update_installing_title),
        body = stringResource(R.string.update_installing_body)
    )
}

@Composable
private fun FailedCard(onRetry: () -> Unit) {
    InfoCard(
        icon = Icons.Filled.ErrorOutline,
        title = stringResource(R.string.update_failed_title),
        body = stringResource(R.string.update_failed_body)
    ) {
        FilledTonalButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.update_check_again_action))
        }
    }
}

@Composable
private fun InfoCard(
    icon: ImageVector,
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            action?.invoke()
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val megabytes = bytes / (1024.0 * 1024.0)
    return if (megabytes >= 1.0) {
        String.format(java.util.Locale.US, "%.1f MB", megabytes)
    } else {
        String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { error ->
        // A device with no browser is a real possibility on a stripped Fire OS
        // build; log it rather than letting the tap appear to do nothing.
        AppLog.w("UpdateScreen", "No app to open $url", error)
    }
}

private const val RELEASE_NOTES_MAX_LINES = 6
