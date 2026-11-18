package com.pulsepad.controller.service.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import com.pulsepad.controller.domain.engine.ControllerButtonMap
import com.pulsepad.controller.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watches the input system for controller connect/disconnect.
 *
 * `InputManager.registerInputDeviceListener` is the only public API that reports
 * controllers appearing and disappearing, and it works on every supported
 * Android version. PulsePad uses it to raise the "controller disconnected"
 * notification and to pause injection when the pad goes away.
 *
 * Device identity is exposed as `InputDevice` ids rather than objects so callers
 * re-read the device through `InputManager` instead of holding a stale reference.
 */
class InputDeviceWatcher(context: Context) {

    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
    private val handler = Handler(Looper.getMainLooper())

    private val _controllerIds = MutableStateFlow<List<Int>>(emptyList())

    /** Ids of the input devices that currently look like controllers. */
    val controllerIds: StateFlow<List<Int>> = _controllerIds.asStateFlow()

    private var registered = false

    private val listener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            AppLog.d(TAG, "Input device added: $deviceId")
            refresh()
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            AppLog.d(TAG, "Input device removed: $deviceId")
            refresh()
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            AppLog.d(TAG, "Input device changed: $deviceId")
            refresh()
        }
    }

    val isAvailable: Boolean
        get() = inputManager != null

    fun start() {
        val manager = inputManager ?: return
        if (!registered) {
            try {
                manager.registerInputDeviceListener(listener, handler)
                registered = true
            } catch (securityException: SecurityException) {
                AppLog.w(TAG, "Input device listener rejected by the system", securityException)
            }
        }
        refresh()
    }

    fun stop() {
        val manager = inputManager ?: return
        if (!registered) return
        try {
            manager.unregisterInputDeviceListener(listener)
        } catch (illegalArgument: IllegalArgumentException) {
            AppLog.w(TAG, "Input device listener was already unregistered", illegalArgument)
        } finally {
            registered = false
        }
    }

    fun refresh() {
        val manager = inputManager ?: run {
            _controllerIds.value = emptyList()
            return
        }
        val ids = manager.inputDeviceIds.filter { id ->
            manager.getInputDevice(id)?.let(::looksLikeController) == true
        }
        _controllerIds.value = ids
    }

    private fun looksLikeController(device: InputDevice): Boolean {
        val sources = device.sources
        if (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) return true
        if (sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) return true
        // Pads that enumerate as keyboards are accepted only when they expose
        // controller keycodes, so real keyboards are not mistaken for gamepads.
        if (sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD) {
            return device.hasKeys(*ControllerButtonMap.MAPPED_KEYCODES.toIntArray()).any { it }
        }
        return false
    }

    private companion object {
        const val TAG = "InputDeviceWatcher"
    }
}
