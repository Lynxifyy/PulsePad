package com.pulsepad.controller.domain.engine

import com.pulsepad.controller.domain.model.ControllerButton

/**
 * Maps raw Android keycodes to the canonical [ControllerButton] set.
 *
 * The mapping is deliberately tolerant because controllers disagree on layout and
 * Fire OS builds frequently report third-party controllers as generic input
 * devices:
 *
 * * The PS4/PS5 face buttons arrive as `BUTTON_A/B/X/Y`, which is why the
 *   PlayStation *shape* names (Cross, Circle, Square, Triangle) are the canonical
 *   entries even on Xbox hardware.
 * * The D-pad may arrive as `KEYCODE_DPAD_*` or as the generic `BUTTON_DPAD_*`
 *   range used by some third-party pads, so both are accepted.
 * * `BUTTON_START`/`BUTTON_SELECT` are sometimes reported as the keyboard
 *   `KEYCODE_ENTER`/`KEYCODE_BACK` on pads that impersonate keyboards.
 *
 * This object is free of Android framework types so the mapping can be unit
 * tested on the JVM.
 */
object ControllerButtonMap {

    // Inlined because these keycodes are @hide in the public SDK:
    // android.view.KeyEvent.KEYCODE_BUTTON_DPAD_UP/DOWN/LEFT/RIGHT.
    private const val KEYCODE_BUTTON_DPAD_UP = 201
    private const val KEYCODE_BUTTON_DPAD_DOWN = 203
    private const val KEYCODE_BUTTON_DPAD_RIGHT = 204
    private const val KEYCODE_BUTTON_DPAD_LEFT = 205

    private const val KEYCODE_BUTTON_A = 96
    private const val KEYCODE_BUTTON_B = 97
    private const val KEYCODE_BUTTON_C = 98
    private const val KEYCODE_BUTTON_X = 99
    private const val KEYCODE_BUTTON_Y = 100
    private const val KEYCODE_BUTTON_Z = 101
    private const val KEYCODE_BUTTON_L1 = 102
    private const val KEYCODE_BUTTON_R1 = 103
    private const val KEYCODE_BUTTON_L2 = 104
    private const val KEYCODE_BUTTON_R2 = 105
    private const val KEYCODE_BUTTON_THUMBL = 106
    private const val KEYCODE_BUTTON_THUMBR = 107
    private const val KEYCODE_BUTTON_START = 108
    private const val KEYCODE_BUTTON_SELECT = 109
    private const val KEYCODE_BUTTON_MODE = 110

    private const val KEYCODE_DPAD_UP = 19
    private const val KEYCODE_DPAD_DOWN = 20
    private const val KEYCODE_DPAD_LEFT = 21
    private const val KEYCODE_DPAD_RIGHT = 22
    private const val KEYCODE_ENTER = 66
    private const val KEYCODE_BACK = 4

    private val KEYCODE_TO_BUTTON: Map<Int, ControllerButton> = mapOf(
        KEYCODE_BUTTON_A to ControllerButton.CROSS,
        KEYCODE_BUTTON_B to ControllerButton.CIRCLE,
        KEYCODE_BUTTON_X to ControllerButton.SQUARE,
        KEYCODE_BUTTON_Y to ControllerButton.TRIANGLE,

        KEYCODE_BUTTON_L1 to ControllerButton.L1,
        KEYCODE_BUTTON_R1 to ControllerButton.R1,
        KEYCODE_BUTTON_L2 to ControllerButton.L2,
        KEYCODE_BUTTON_R2 to ControllerButton.R2,

        KEYCODE_BUTTON_THUMBL to ControllerButton.L3,
        KEYCODE_BUTTON_THUMBR to ControllerButton.R3,

        KEYCODE_BUTTON_START to ControllerButton.OPTIONS,
        KEYCODE_BUTTON_SELECT to ControllerButton.SHARE,
        KEYCODE_BUTTON_MODE to ControllerButton.GUIDE,

        KEYCODE_DPAD_UP to ControllerButton.DPAD_UP,
        KEYCODE_DPAD_DOWN to ControllerButton.DPAD_DOWN,
        KEYCODE_DPAD_LEFT to ControllerButton.DPAD_LEFT,
        KEYCODE_DPAD_RIGHT to ControllerButton.DPAD_RIGHT,

        KEYCODE_BUTTON_DPAD_UP to ControllerButton.DPAD_UP,
        KEYCODE_BUTTON_DPAD_DOWN to ControllerButton.DPAD_DOWN,
        KEYCODE_BUTTON_DPAD_LEFT to ControllerButton.DPAD_LEFT,
        KEYCODE_BUTTON_DPAD_RIGHT to ControllerButton.DPAD_RIGHT,

        KEYCODE_ENTER to ControllerButton.OPTIONS,
        KEYCODE_BACK to ControllerButton.SHARE
    )

    /**
     * The keycodes PulsePad accepts as controller input. `InputDevice.hasKeys`
     * is fed this set to recognise pads that enumerate as keyboards.
     */
    val MAPPED_KEYCODES: Set<Int> = KEYCODE_TO_BUTTON.keys

    fun buttonForKeyCode(keyCode: Int): ControllerButton? = KEYCODE_TO_BUTTON[keyCode]

    fun isMappedKeyCode(keyCode: Int): Boolean = KEYCODE_TO_BUTTON.containsKey(keyCode)
}
