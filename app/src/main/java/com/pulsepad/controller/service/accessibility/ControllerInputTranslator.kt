package com.pulsepad.controller.service.accessibility

import android.view.KeyEvent
import android.view.MotionEvent
import com.pulsepad.controller.domain.engine.ControllerAxisResolver
import com.pulsepad.controller.domain.engine.ControllerButtonMap
import com.pulsepad.controller.domain.model.ControllerInput

/**
 * Adapter that turns Android `KeyEvent`/`MotionEvent` objects into canonical
 * [ControllerInput] values. All decision logic lives in the pure
 * [ControllerButtonMap] and [ControllerAxisResolver]; this class only reads the
 * framework objects.
 */
object ControllerInputTranslator {

    /**
     * Converts a [KeyEvent] into a canonical button edge, or null when the key is
     * not a controller button or is a key-repeat that would look like a double tap.
     */
    fun translateKeyEvent(event: KeyEvent): ControllerInput.Button? {
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return null
        if (event.repeatCount > 0) return null
        val button = ControllerButtonMap.buttonForKeyCode(event.keyCode) ?: return null
        return ControllerInput.Button(
            deviceId = event.deviceId,
            button = button,
            pressed = event.action == KeyEvent.ACTION_DOWN
        )
    }

    /**
     * Converts a [MotionEvent] into canonical axis samples. Reading every alias
     * axis through the public `getAxisValue` API avoids the `@hide`
     * `getAxisType`/`axisCount` accessors.
     */
    fun translateMotionEvent(event: MotionEvent): List<ControllerInput.Axis> {
        val samples = buildList {
            for (rawAxis in ControllerAxisResolver.STICK_ALIASES.keys) {
                add(rawAxis to event.getAxisValue(rawAxis))
            }
            for (rawAxis in ControllerAxisResolver.TRIGGER_ALIASES.keys) {
                add(rawAxis to event.getAxisValue(rawAxis))
            }
            for (rawAxis in ControllerAxisResolver.HAT_ALIASES.keys) {
                add(rawAxis to event.getAxisValue(rawAxis))
            }
        }
        val resolved = ControllerAxisResolver.resolve(samples)
        return resolved.map { (axis, value) -> ControllerInput.Axis(event.deviceId, axis, value) }
    }
}
