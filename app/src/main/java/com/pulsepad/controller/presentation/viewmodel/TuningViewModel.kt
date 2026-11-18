package com.pulsepad.controller.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsepad.controller.data.repository.MappingRepository
import com.pulsepad.controller.domain.engine.LatencyExpectation
import com.pulsepad.controller.domain.model.DeviceCapabilities
import com.pulsepad.controller.service.accessibility.AccessibilityInjectionEngine
import com.pulsepad.controller.service.accessibility.AccessibilityServiceBridge
import com.pulsepad.controller.service.accessibility.InjectionCoordinator
import com.pulsepad.controller.util.CapabilityChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Tuning destination: the injection interval, the turbo rate, and the
 * master arming switch.
 *
 * The latency figure is derived from the interval rather than measured, and the
 * screen labels it as an estimate. Measuring it for real would need a target app
 * that reports when it saw the touch, which PulsePad cannot require.
 */
@HiltViewModel
class TuningViewModel @Inject constructor(
    private val mappingRepository: MappingRepository,
    private val injectionCoordinator: InjectionCoordinator,
    private val engine: AccessibilityInjectionEngine,
    private val capabilityChecker: CapabilityChecker
) : ViewModel() {

    val injectionIntervalMs: StateFlow<Int> = mappingRepository.injectionIntervalMs
    val turboRateHz: StateFlow<Int> = mappingRepository.turboRateHz
    val armed: StateFlow<Boolean> = injectionCoordinator.armed
    val lastInjectionFailed: StateFlow<Boolean> = injectionCoordinator.lastInjectionFailed
    val serviceConnection: StateFlow<AccessibilityServiceBridge.ConnectionState> =
        AccessibilityServiceBridge.connectionState

    private val _capabilities = MutableStateFlow<DeviceCapabilities?>(null)
    val capabilities: StateFlow<DeviceCapabilities?> = _capabilities.asStateFlow()

    private val _armRejected = MutableStateFlow(false)
    val armRejected: StateFlow<Boolean> = _armRejected.asStateFlow()

    /** The honest estimate for the current interval; see [LatencyExpectation]. */
    val estimatedLatencyMs: Int
        get() = engine.estimatedLatencyMs

    init {
        viewModelScope.launch { mappingRepository.load() }
        viewModelScope.launch { _capabilities.value = capabilityChecker.checkAll() }
    }

    fun setInjectionInterval(intervalMs: Int) {
        viewModelScope.launch { mappingRepository.setInjectionInterval(intervalMs) }
    }

    fun setTurboRate(rateHz: Int) {
        viewModelScope.launch { mappingRepository.setTurboRate(rateHz) }
    }

    /**
     * Arms or disarms injection, reporting refusal rather than silently ignoring it.
     *
     * Arming needs a live accessibility service; if the system refuses, the switch
     * must not appear to have taken effect.
     */
    fun setArmed(armed: Boolean) {
        val accepted = injectionCoordinator.setArmed(armed)
        _armRejected.value = armed && !accepted
    }

    fun dismissArmRejection() {
        _armRejected.value = false
    }

    fun refreshCapabilities() {
        viewModelScope.launch { _capabilities.value = capabilityChecker.checkAll() }
    }

    companion object {
        /** Exposed so the UI can label the band without reaching into the engine. */
        val latencyBandMs: IntRange
            get() = LatencyExpectation.BEST_MS..LatencyExpectation.WORST_MS
    }
}
