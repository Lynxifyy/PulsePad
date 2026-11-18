package com.pulsepad.controller.service.accessibility

import android.view.MotionEvent
import com.pulsepad.controller.domain.model.ControllerAxis
import com.pulsepad.controller.domain.model.ControllerInput

/**
 * Translates analog stick and trigger movement into the canonical
 * [ControllerAxis] set.
 *
 * Axis numbering is *not* standardised across controllers:
 *
 * * Most pads report the left stick on `AXIS_X`/`AXIS_Y` and the right stick on
 *   `AXIS_Z`/`AXIS_RZ`.
 * * Xbox pads in some driver revisions report the right stick on
 *   `AXIS_RX`/`AXIS_RY` instead.
 * * `AXIS_LTRIGGER`/`AXIS_RTRIGGER` carry the analog trigger value, but a few
 *   pads report triggers as `AXIS_BRAKE`/`AXIS_GAS`.
 * * Some controllers report the D-pad as a hat switch on
 *   `AXIS_HAT_X`/`AXIS_HAT_Y` rather than as discrete buttons.
 *
 * Every documented alias is read through the public `getAxisValue` API rather
 * than `getAxisType`/`axisCount`, which are `@hide`. Values are normalised to
 * -1..1 for sticks and 0..1 for triggers so downstream logic sees one shape.
 */
object ControllerMotionTranslator {

    private val STICK_AXES: List<Pair<Int, ControllerAxis>> = listOf(
        MotionEvent.AXIS_X to ControllerAxis.LEFT_STICK_X,
        MotionEvent.AXIS_Y to ControllerAxis.LEFT_STICK_Y,
        MotionEvent.AXIS_Z to ControllerAxis.RIGHT_STICK_X,
        MotionEvent.AXIS_RZ to ControllerAxis.RIGHT_STICK_Y,
        MotionEvent.AXIS_RX to ControllerAxis.RIGHT_STICK_X,
        MotionEvent.AXIS_RY to ControllerAxis.RIGHT_STICK_Y
    )

    private val TRIGGER_AXES: List<Pair<Int, ControllerAxis>> = listOf(
        MotionEvent.AXIS_LTRIGGER to ControllerAxis.L2,
        MotionEvent.AXIS_RTRIGGER to ControllerAxis.R2,
        MotionEvent.AXIS_BRAKE to ControllerAxis.L2,
        MotionEvent.AXIS_GAS to ControllerAxis.R2
    )

    /** Hat axes are only reported while the D-pad is held, so they are read on demand. */
    private val HAT_AXES: List<Pair<Int, ControllerAxis>> = listOf(
        MotionEvent.AXIS_HAT_X to ControllerAxis.LEFT_STICK_X,
        MotionEvent.AXIS_HAT_Y to ControllerAxis.LEFT_STICK_Y
    )

    /** Android already reports stick axes as -1..1; the clamp guards noisy drivers. */
    private fun normaliseStick(value: Float): Float = value.coerceIn(-1f, 1f)

    /** Trigger axes are 0..1 on the platforms PulsePad targets; clamp for safety. */
    private fun normaliseTrigger(value: Float): Float = value.coerceIn(0f, 1f)

    /**
     * Returns every canonical axis sample carried by [event]. A single
     * [MotionEvent] can report both sticks and both triggers at once.
     */
    fun translate(event: MotionEvent): List<ControllerInput.Axis> {
        val deviceId = event.deviceId
        val resolved = LinkedHashMap<ControllerAxis, Float>()

        fun offer(canonical: ControllerAxis, raw: Float, normalise: (Float) -> Float) {
            val value = normalise(raw)
            val existing = resolved[canonical]
            // Alias axes can report 0 while the primary axis reports a real
            // value, so a non-neutral reading always wins over a neutral one.
            if (existing == null || (existing == 0f && value != 0f)) {
                resolved[canonical] = value
            }
        }

        for ((rawAxis, canonical) in STICK_AXES) {
            offer(canonical, event.getAxisValue(rawAxis), ::normaliseStick)
        }
        for ((rawAxis, canonical) in TRIGGER_AXES) {
            offer(canonical, event.getAxisValue(rawAxis), ::normaliseTrigger)
        }
        for ((rawAxis, canonical) in HAT_AXES) {
            val raw = event.getAxisValue(rawAxis)
            if (raw != 0f) offer(canonical, raw, ::normaliseStick)
        }

        return resolved.map { (axis, value) -> ControllerInput.Axis(deviceId, axis, value) }
    }
}
