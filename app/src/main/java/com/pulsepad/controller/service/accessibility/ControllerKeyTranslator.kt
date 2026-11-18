package com.pulsepad.controller.service.accessibility

import android.view.KeyEvent
import com.pulsepad.controller.domain.model.ControllerButton
import com.pulsepad.controller.domain.model.ControllerInput

/**
 * Translates raw Android keycodes into the canonical [ControllerButton] set.
 *
 * The mapping is deliberately tolerant because controllers do not agree on a
 * layout, and Fire OS builds frequently report third-party controllers as
 * generic input devices:
 *
 * * The PS4/PS5 face buttons arrive as `BUTTON_A/B/X/Y`, which is why the
 *   PlayStation *shape* names (Cross, Circle, Square, Triangle) are used for the
 *   canonical entries even on Xbox hardware.
 * * The D-pad may arrive either as `KEYCODE_DPAD_*` (standard) or as the generic
 *   `BUTTON_DPAD_*`/`BUTTON_1..4` range (some third-party pads), so both are
 *   accepted.
 * * `BUTTON_START`/`BUTTON_SELECT` are sometimes reported as the keyboard
 *   `KEYCODE_ENTER`/`KEYCODE_BACK` on pads that impersonate keyboards.
 *
 * This object holds no Android framework state, so it is exercised directly from
 * JVM unit tests.
 */
object ControllerKeyTranslator {

    // Inlined because these keycodes are @hide in the public SDK:
    // android.view.KeyEvent.KEYCODE_BUTTON_DPAD_UP/DOWN/LEFT/RIGHT.
    private const val KEYCODE_BUTTON_DPAD_UP = 201
    private const val KEYCODE_BUTTON_DPAD_DOWN = 203
    private const val KEYCODE_BUTTON_DPAD_LEFT = 205
    private const val KEYCODE_BUTTON_DPAD_RIGHT = 204

    private val KEYCODE_TO_BUTTON: Map<Int, ControllerButton> = buildMap {
        // Face buttons: PlayStation layout is the canonical naming.
        put(KeyEvent.KEYCODE_BUTTON_A, ControllerButton.CROSS)
        put(KeyEvent.KEYCODE_BUTTON_B, ControllerButton.CIRCLE)
        put(KeyEvent.KEYCODE_BUTTON_X, ControllerButton.SQUARE)
        put(KeyEvent.KEYCODE_BUTTON_Y, ControllerButton.TRIANGLE)

        // Shoulders and triggers.
        put(KeyEvent.KEYCODE_BUTTON_L1, ControllerButton.L1)
        put(KeyEvent.KEYCODE_BUTTON_R1, ControllerButton.R1)
        put(KeyEvent.KEYCODE_BUTTON_L2, ControllerButton.L2)
        put(KeyEvent.KEYCODE_BUTTON_R2, ControllerButton.R2)

        // Stick presses.
        put(KeyEvent.KEYCODE_BUTTON_THUMBL, ControllerButton.L3)
        put(KeyEvent.KEYCODE_BUTTON_THUMBR, ControllerButton.R3)

        // Menu / share / guide.
        put(KeyEvent.KEYCODE_BUTTON_START, ControllerButton.OPTIONS)
        put(KeyEvent.KEYCODE_BUTTON_SELECT, ControllerButton.SHARE)
        put(KeyEvent.KEYCODE_BUTTON_MODE, ControllerButton.GUIDE)

        // D-pad, standard layout.
        put(KeyEvent.KEYCODE_DPAD_UP, ControllerButton.DPAD_UP)
        put(KeyEvent.KEYCODE_DPAD_DOWN, ControllerButton.DPAD_DOWN)
        put(KeyEvent.KEYCODE_DPAD_LEFT, ControllerButton.DPAD_LEFT)
        put(KeyEvent.KEYCODE_DPAD_RIGHT, ControllerButton.DPAD_RIGHT)

        // D-pad, generic-pad layout used by some Fire OS device drivers.
        // KeyEvent.KEYCODE_BUTTON_DPAD_* is @hide, so the AOSP values are
        // inlined here rather than referenced.
        put(KEYCODE_BUTTON_DPAD_UP, ControllerButton.DPAD_UP)
        put(KEYCODE_BUTTON_DPAD_DOWN, ControllerButton.DPAD_DOWN)
        put(KEYCODE_BUTTON_DPAD_LEFT, ControllerButton.DPAD_LEFT)
        put(KEYCODE_BUTTON_DPAD_RIGHT, ControllerButton.DPAD_RIGHT)

        // Keyboard-style start/back on pads that enumerate as keyboards.
        put(KeyEvent.KEYCODE_ENTER, ControllerButton.OPTIONS)
        put(KeyEvent.KEYCODE_BACK, ControllerButton.SHARE)
    }

    /** True when [keyCode] is a button PulsePad understands. */
    fun isMappedKeyCode(keyCode: Int): Boolean = KEYCODE_TO_BUTTON.containsKey(keyCode)

    /**
     * Converts a [KeyEvent] into a canonical input, or null when the key is not a
     * controller button. Uses [KeyEvent.getAction] so press and release are both
     * reported; repeat events are ignored to avoid phantom double-taps.
     */
    fun translate(event: KeyEvent): ControllerInput.Button? {
        val button = KEYCODE_TO_BUTTON[event.keyCode] ?: return null
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return null
        if (event.repeatCount > 0) return null
        return ControllerInput.Button(
            deviceId = event.deviceId,
            button = button,
            pressed = event.action == KeyEvent.ACTION_DOWN
        )
    }
}
