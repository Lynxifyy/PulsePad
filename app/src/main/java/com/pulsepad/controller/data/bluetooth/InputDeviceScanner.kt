package com.pulsepad.controller.data.bluetooth

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import com.pulsepad.controller.domain.engine.ControllerButtonMap
import com.pulsepad.controller.domain.model.DiscoverySource
import com.pulsepad.controller.util.AppLog

/**
 * Enumerates controllers the input system knows about.
 *
 * This is the only discovery source on Fire OS builds that restrict
 * `BluetoothHidHost`, and it is also what gives a controller its `InputDevice`
 * id, which the capture service needs to read events.
 *
 * Recognition is deliberately tolerant. A device is treated as a controller when
 * it reports `SOURCE_GAMEPAD`/`SOURCE_JOYSTICK`, or when it is a keyboard-class
 * device that exposes controller keycodes - which is how several Fire OS builds
 * report third-party pads.
 *
 * The Bluetooth MAC is intentionally left null here: `InputDevice.getBluetoothAddress`
 * is `@hide`, so the address is only known from the Bluetooth side of discovery and
 * [ControllerDiscovery.merge] fills it in.
 */
class InputDeviceScanner(private val context: Context) {

    fun scan(): List<ControllerDiscovery.DiscoveryRecord> {
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
        if (inputManager == null) {
            AppLog.w(TAG, "InputManager unavailable; skipping input device scan")
            return emptyList()
        }
        val records = mutableListOf<ControllerDiscovery.DiscoveryRecord>()
        for (id in inputManager.inputDeviceIds) {
            val device = inputManager.getInputDevice(id) ?: continue
            toRecord(device)?.let(records::add)
        }
        return records
    }

    private fun toRecord(device: InputDevice): ControllerDiscovery.DiscoveryRecord? {
        if (!isControllerLike(device)) return null
        return ControllerDiscovery.DiscoveryRecord(
            name = device.name,
            address = null,
            majorClass = null,
            deviceId = device.id,
            source = DiscoverySource.INPUT_DEVICE,
            isConnected = true
        )
    }

    private fun isControllerLike(device: InputDevice): Boolean {
        val sources = device.sources
        if (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) return true
        if (sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) return true
        // Keyboard-class devices are only accepted when they expose gamepad
        // keycodes, so real keyboards and remotes are not mistaken for pads.
        if (sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD) {
            return device.hasKeys(*ControllerButtonMap.MAPPED_KEYCODES.toIntArray()).any { it }
        }
        return false
    }

    private companion object {
        const val TAG = "InputDeviceScanner"
    }
}
