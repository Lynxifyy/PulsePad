package com.pulsepad.controller.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsepad.controller.domain.model.Capability
import com.pulsepad.controller.domain.model.CapabilityReport
import com.pulsepad.controller.domain.model.DeviceCapabilities
import com.pulsepad.controller.util.CapabilityChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CapabilityUiState(
    val loading: Boolean = true,
    val capabilities: DeviceCapabilities? = null,
    val reports: List<CapabilityReport> = emptyList()
)

@HiltViewModel
class CapabilityViewModel @Inject constructor(
    private val capabilityChecker: CapabilityChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow(CapabilityUiState())
    val uiState: StateFlow<CapabilityUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(loading = true)
        viewModelScope.launch {
            val capabilities = capabilityChecker.checkAll()
            _uiState.value = CapabilityUiState(
                loading = false,
                capabilities = capabilities,
                reports = capabilities.toReports()
            )
        }
    }
}

/**
 * Flattens the capability snapshot into display rows. The strings are resolved in
 * the composable so this mapping stays free of Android resource lookups.
 */
fun DeviceCapabilities.toReports(): List<CapabilityReport> = listOf(
    CapabilityReport(Capability.ACCESSIBILITY_SERVICE, accessibilityServiceEnabled, ""),
    CapabilityReport(Capability.OPEN_GL_ES_3, openGlEs30Available, ""),
    CapabilityReport(Capability.BLUETOOTH_HID_HOST, bluetoothHidHostAvailable, ""),
    CapabilityReport(Capability.PLAY_SERVICES, playServicesPresent, "")
)
