package com.pulsepad.controller.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsepad.controller.data.bluetooth.BluetoothControllerRepository
import com.pulsepad.controller.data.repository.ControllerInputRepository
import com.pulsepad.controller.domain.model.ControllerConnectionState
import com.pulsepad.controller.domain.model.ControllerFamily
import com.pulsepad.controller.domain.model.ControllerState
import com.pulsepad.controller.domain.model.DeviceCapabilities
import com.pulsepad.controller.util.CapabilityChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which controller family the studio is showing. */
enum class StudioModel(val family: ControllerFamily) {
    DUALSHOCK_4(ControllerFamily.DUALSHOCK_4),
    DUALSENSE(ControllerFamily.DUALSENSE),
    XBOX_WIRELESS(ControllerFamily.XBOX_WIRELESS)
}

/**
 * State for the 3D Studio.
 *
 * The selected model is independent of the connected controller: the studio is a
 * configuration preview first, so a user can inspect and map a pad they have not
 * paired yet. Selecting a model does not, by itself, change any mapping.
 */
@HiltViewModel
class StudioViewModel @Inject constructor(
    private val bluetoothRepository: BluetoothControllerRepository,
    private val inputRepository: ControllerInputRepository,
    private val capabilityChecker: CapabilityChecker
) : ViewModel() {

    private val _selectedModel = MutableStateFlow(StudioModel.DUALSENSE)
    val selectedModel: StateFlow<StudioModel> = _selectedModel.asStateFlow()

    private val _capabilities = MutableStateFlow<DeviceCapabilities?>(null)
    val capabilities: StateFlow<DeviceCapabilities?> = _capabilities.asStateFlow()

    /** Live controller snapshot, driving the highlight on the model or schematic. */
    val controllerState: StateFlow<ControllerState> = inputRepository.state

    val connectionState: StateFlow<ControllerConnectionState> = bluetoothRepository.connectionState

    private var userSelectedModel = false

    init {
        bluetoothRepository.start()
        viewModelScope.launch {
            _capabilities.value = capabilityChecker.checkAll()
        }
        // Follow the connected controller's family so the preview matches the pad
        // in the user's hands, until they pick a model explicitly.
        viewModelScope.launch {
            bluetoothRepository.connectionState.collect { state ->
                val connected = state as? ControllerConnectionState.Connected ?: return@collect
                if (!userSelectedModel) {
                    familyToModel(connected.device.family)?.let { _selectedModel.value = it }
                }
            }
        }
    }

    fun selectModel(model: StudioModel) {
        userSelectedModel = true
        _selectedModel.value = model
    }

    /**
     * Remembers that a model could not be rendered on this device.
     *
     * A model that fails to load will fail again, so the studio stops asking
     * Filament for it and shows the schematic instead of retrying on every
     * recomposition.
     */
    fun markModelUnavailable(model: StudioModel) {
        _unavailableModels.value = _unavailableModels.value + model
    }

    private val _unavailableModels = MutableStateFlow<Set<StudioModel>>(emptySet())

    /** Models this device could not load, so the caller can fall back to the schematic. */
    val unavailableModels: StateFlow<Set<StudioModel>> = _unavailableModels.asStateFlow()

    /** True when the device can render the 3D models; false means show the schematic. */
    val canRender3d: Boolean
        get() = _capabilities.value?.openGlEs30Available == true

    /** Whether [model] should be drawn in 3D right now, on this device. */
    fun canRenderIn3d(model: StudioModel): Boolean =
        canRender3d && model !in _unavailableModels.value

    private fun familyToModel(family: ControllerFamily): StudioModel? = when (family) {
        ControllerFamily.DUALSHOCK_4 -> StudioModel.DUALSHOCK_4
        ControllerFamily.DUALSENSE -> StudioModel.DUALSENSE
        ControllerFamily.XBOX_WIRELESS -> StudioModel.XBOX_WIRELESS
        ControllerFamily.UNKNOWN -> null
    }
}
