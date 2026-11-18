package com.pulsepad.controller.domain.model

/**
 * Canonical controller buttons across the three supported families. The names
 * follow the PlayStation layout because it has the most distinct labels; the
 * Xbox equivalents are documented per entry.
 *
 * Physical controllers do not agree on a keycode layout, so several raw keycodes
 * can map onto one canonical button (see the raw-to-canonical translation layer).
 */
enum class ControllerButton {
    /** Cross / A */
    CROSS,

    /** Circle / B */
    CIRCLE,

    /** Square / X */
    SQUARE,

    /** Triangle / Y */
    TRIANGLE,

    /** Left bumper */
    L1,

    /** Right bumper */
    R1,

    /** Left trigger; analog value is also reported through [ControllerAxis.L2] */
    L2,

    /** Right trigger; analog value is also reported through [ControllerAxis.R2] */
    R2,

    /** Left stick press */
    L3,

    /** Right stick press */
    R3,

    DPAD_UP,
    DPAD_DOWN,
    DPAD_LEFT,
    DPAD_RIGHT,

    /** Options / Menu / Start */
    OPTIONS,

    /** Share / View / Back */
    SHARE,

    /** PS button / Guide button */
    GUIDE
}

/** Analog axes exposed by a controller. Values are normalised to -1..1 (sticks) or 0..1 (triggers). */
enum class ControllerAxis {
    LEFT_STICK_X,
    LEFT_STICK_Y,
    RIGHT_STICK_X,
    RIGHT_STICK_Y,
    L2,
    R2
}

/** A single decoded controller event, either a digital button edge or an analog axis sample. */
sealed interface ControllerInput {
    /** Identifies the physical device the event came from, or -1 when unknown. */
    val deviceId: Int

    data class Button(
        override val deviceId: Int,
        val button: ControllerButton,
        val pressed: Boolean
    ) : ControllerInput

    data class Axis(
        override val deviceId: Int,
        val axis: ControllerAxis,
        val value: Float
    ) : ControllerInput
}

/** Snapshot of every button and axis, used to drive the live highlight view. */
data class ControllerState(
    val deviceId: Int = NO_DEVICE,
    val pressedButtons: Set<ControllerButton> = emptySet(),
    val axes: Map<ControllerAxis, Float> = emptyMap()
) {
    fun withButton(button: ControllerButton, pressed: Boolean): ControllerState =
        copy(pressedButtons = if (pressed) pressedButtons + button else pressedButtons - button)

    fun withAxis(axis: ControllerAxis, value: Float): ControllerState =
        copy(axes = axes + (axis to value))

    companion object {
        const val NO_DEVICE = -1
    }
}
