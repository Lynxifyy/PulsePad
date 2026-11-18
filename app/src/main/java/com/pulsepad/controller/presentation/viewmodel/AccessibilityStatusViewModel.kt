package com.pulsepad.controller.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsepad.controller.service.accessibility.AccessibilityServiceBridge
import com.pulsepad.controller.service.accessibility.AccessibilitySettingsNavigator
import com.pulsepad.controller.util.CapabilityChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reports whether system-wide mapping is currently possible.
 *
 * Two signals are combined because they mean different things:
 * `CapabilityChecker` reads whether the user has enabled the service in settings,
 * while [AccessibilityServiceBridge] reports whether the process is actually
 * connected right now. The service can be enabled and still not be running, for
 * example in the moment after the OS kills it to reclaim memory.
 */
@HiltViewModel
class AccessibilityStatusViewModel @Inject constructor(
    private val capabilityChecker: CapabilityChecker,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _enabledInSettings = MutableStateFlow(false)

    /** True when the user has granted the service in system settings. */
    val enabledInSettings: StateFlow<Boolean> = _enabledInSettings.asStateFlow()

    /**
     * True once the OS has stopped the service while the app was running.
     *
     * This is distinct from "never enabled": the user has already granted the
     * service, so the right advice is to turn it back on, not to explain what it
     * is. The flag clears as soon as the service reconnects.
     */
    private val _serviceKilled = MutableStateFlow(false)
    val serviceKilled: StateFlow<Boolean> = _serviceKilled.asStateFlow()

    /** Live connection state of the running service process. */
    val connectionState: StateFlow<AccessibilityServiceBridge.ConnectionState> =
        AccessibilityServiceBridge.connectionState

    init {
        refresh()
        viewModelScope.launch {
            AccessibilityServiceBridge.connectionState.collect { state ->
                when (state) {
                    AccessibilityServiceBridge.ConnectionState.INTERRUPTED ->
                        _serviceKilled.value = true

                    AccessibilityServiceBridge.ConnectionState.CONNECTED ->
                        _serviceKilled.value = false

                    AccessibilityServiceBridge.ConnectionState.DISCONNECTED -> Unit
                }
            }
        }
    }

    fun dismissServiceKilled() {
        _serviceKilled.value = false
    }

    /**
     * Re-reads the settings flag. Called on resume, since the user has to leave
     * PulsePad to toggle the service and there is no callback for the change.
     */
    fun refresh() {
        viewModelScope.launch {
            _enabledInSettings.value = capabilityChecker.isAccessibilityServiceEnabled()
        }
    }

    /** True when the banner should be shown: the service is off or not running. */
    val isMappingUnavailable: Boolean
        get() = !_enabledInSettings.value ||
            AccessibilityServiceBridge.connectionState.value ==
            AccessibilityServiceBridge.ConnectionState.DISCONNECTED

    /**
     * Opens the accessibility service list. The user must toggle PulsePad on
     * themselves; no app can enable its own accessibility service.
     */
    fun openAccessibilitySettings() {
        AccessibilitySettingsNavigator.openSettings(appContext)
    }
}
