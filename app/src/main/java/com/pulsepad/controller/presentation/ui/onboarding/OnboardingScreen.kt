package com.pulsepad.controller.presentation.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsepad.controller.R
import com.pulsepad.controller.domain.model.DeviceCapabilities
import com.pulsepad.controller.presentation.viewmodel.OnboardingViewModel
import com.pulsepad.controller.service.accessibility.AccessibilitySettingsNavigator
import com.pulsepad.controller.util.PermissionSupport
import kotlinx.coroutines.launch

/**
 * First-launch onboarding.
 *
 * The flow is deliberately skippable and explains the two things a user is most
 * likely to be surprised by: that the accessibility service is required, and that
 * the engine is a gesture replay with real latency rather than a virtual gamepad.
 * It also adapts its wording to what the device actually supports, so a Fire tablet
 * that restricts HID host or OpenGL is told so up front instead of discovering it
 * through a silent fallback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })

    var bluetoothGranted by remember {
        mutableStateOf(PermissionSupport.hasBluetoothPermissions(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        bluetoothGranted = result.values.all { it } &&
            PermissionSupport.hasBluetoothPermissions(context)
    }

    fun finish() {
        viewModel.markCompleted()
        onFinished()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.onboarding_title)) },
                actions = {
                    TextButton(onClick = { finish() }) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                },
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                progress = { (pagerState.currentPage + 1f) / PAGE_COUNT },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                OnboardingPage(
                    page = page,
                    capabilities = capabilities,
                    bluetoothGranted = bluetoothGranted,
                    onRequestBluetooth = {
                        val permissions = PermissionSupport.requiredBluetoothPermissions
                            .plus(listOfNotNull(PermissionSupport.notificationPermission))
                            .toTypedArray()
                        permissionLauncher.launch(permissions)
                    },
                    onOpenAccessibility = {
                        AccessibilitySettingsNavigator.openSettings(context)
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.common_back))
                    }
                }
                Button(
                    onClick = {
                        if (pagerState.currentPage == PAGE_COUNT - 1) {
                            finish()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stringResource(
                            if (pagerState.currentPage == PAGE_COUNT - 1) {
                                R.string.onboarding_done
                            } else {
                                R.string.onboarding_next
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    page: Int,
    capabilities: DeviceCapabilities?,
    bluetoothGranted: Boolean,
    onRequestBluetooth: () -> Unit,
    onOpenAccessibility: () -> Unit
) {
    val (icon, titleRes, bodyRes) = pageContent(page, capabilities)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        when (page) {
            PAGE_BLUETOOTH -> if (bluetoothGranted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.onboarding_bluetooth_granted),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Button(onClick = onRequestBluetooth) {
                    Text(stringResource(R.string.onboarding_grant_bluetooth))
                }
            }

            PAGE_ACCESSIBILITY -> Button(onClick = onOpenAccessibility) {
                Text(stringResource(R.string.onboarding_open_accessibility))
            }

            else -> Unit
        }
    }
}

/** Icon, title and body for each page, with the device-dependent wording resolved. */
private fun pageContent(
    page: Int,
    capabilities: DeviceCapabilities?
): Triple<ImageVector, Int, Int> = when (page) {
    PAGE_WELCOME -> Triple(
        Icons.Filled.SportsEsports,
        R.string.onboarding_step_welcome_title,
        R.string.onboarding_step_welcome_body
    )

    PAGE_BLUETOOTH -> Triple(
        Icons.Filled.Bluetooth,
        R.string.onboarding_step_bluetooth_title,
        R.string.onboarding_step_bluetooth_body
    )

    PAGE_ACCESSIBILITY -> Triple(
        Icons.Filled.Security,
        R.string.onboarding_step_accessibility_title,
        R.string.onboarding_step_accessibility_body
    )

    PAGE_LATENCY -> Triple(
        Icons.Filled.Speed,
        R.string.onboarding_step_latency_title,
        R.string.onboarding_step_latency_body
    )

    PAGE_RENDERING -> Triple(
        Icons.Filled.Info,
        R.string.onboarding_step_rendering_title,
        if (capabilities?.openGlEs30Available == true) {
            R.string.onboarding_step_rendering_body_3d
        } else {
            R.string.onboarding_step_rendering_body_2d
        }
    )

    PAGE_HID -> Triple(
        Icons.Filled.Bluetooth,
        R.string.onboarding_step_hid_title,
        if (capabilities?.bluetoothHidHostAvailable == true) {
            R.string.onboarding_step_hid_body_available
        } else {
            R.string.onboarding_step_hid_body_unavailable
        }
    )

    else -> Triple(
        Icons.Filled.CheckCircle,
        R.string.onboarding_step_complete_title,
        R.string.onboarding_step_complete_body
    )
}

private const val PAGE_WELCOME = 0
private const val PAGE_BLUETOOTH = 1
private const val PAGE_ACCESSIBILITY = 2
private const val PAGE_LATENCY = 3
private const val PAGE_RENDERING = 4
private const val PAGE_HID = 5
private const val PAGE_COUNT = 6
