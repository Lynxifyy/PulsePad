package com.pulsepad.controller.presentation.viewmodel

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsepad.controller.data.bluetooth.BluetoothControllerRepository
import com.pulsepad.controller.data.repository.ControllerInputRepository
import com.pulsepad.controller.domain.model.ControllerConnectionState
import com.pulsepad.controller.domain.model.ControllerDevice
import com.pulsepad.controller.domain.model.ControllerInput
import com.pulsepad.controller.domain.model.ControllerState
import com.pulsepad.controller.util.AppLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the input debug readout: which controllers are visible, which one is
 * targeted, and the current button/axis snapshot.
 */
@HiltViewModel
class InputDebugViewModel @Inject constructor(
    private val bluetoothRepository: BluetoothControllerRepository,
    inputRepository: ControllerInputRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val devices: StateFlow<List<ControllerDevice>> = bluetoothRepository.devices
    val connectionState: StateFlow<ControllerConnectionState> = bluetoothRepository.connectionState
    val hidHostUnavailable: StateFlow<Boolean> = bluetoothRepository.hidHostUnavailable
    val isScanning: StateFlow<Boolean> = bluetoothRepository.isScanning

    val controllerState: StateFlow<ControllerState> = inputRepository.state
    val lastInput: StateFlow<ControllerInput?> = inputRepository.lastInput

    init {
        bluetoothRepository.start()
    }

    fun scan() = bluetoothRepository.scan()

    /**
     * Opens the system Bluetooth settings screen. On devices without the HID host
     * profile this is the only way to pair a controller, so the debug screen links
     * to it directly.
     */
    fun openBluetoothSettings() {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            appContext.startActivity(intent)
        } catch (activityNotFound: ActivityNotFoundException) {
            AppLog.w(TAG, "Bluetooth settings screen is not available", activityNotFound)
        } catch (securityException: SecurityException) {
            AppLog.w(TAG, "Bluetooth settings screen is blocked", securityException)
        }
    }

    fun connect(device: ControllerDevice) {
        viewModelScope.launch { bluetoothRepository.connect(device) }
    }

    private companion object {
        const val TAG = "InputDebugViewModel"
    }
}
