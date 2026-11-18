package com.pulsepad.controller.domain.engine

import com.pulsepad.controller.domain.model.ControllerAxis

/**
 * Resolves raw Android axis ids onto the canonical [ControllerAxis] set and
 * normalises their ranges.
 *
 * Axis numbering is *not* standardised across controllers:
 *
 * * Most pads report the left stick on `AXIS_X`/`AXIS_Y` and the right stick on
 *   `AXIS_Z`/`AXIS_RZ`.
 * * Xbox pads in some driver revisions report the right stick on
 *   `AXIS_RX`/`AXIS_RY` instead.
 * * `AXIS_LTRIGGER`/`AXIS_RTRIGGER` carry the analog trigger value, but a few
 *   pads report triggers as `AXIS_BRAKE`/`AXIS_GAS`.
 * * Some pads report the D-pad as a hat switch on `AXIS_HAT_X`/`AXIS_HAT_Y`.
 *
 * Raw axis ids are passed in as plain integers so this logic stays free of
 * Android framework types and unit testable on the JVM.
 */
object ControllerAxisResolver {

    // android.view.MotionEvent axis ids.
    const val AXIS_X = 0
    const val AXIS_Y = 1
    const val AXIS_Z = 11
    const val AXIS_RX = 12
    const val AXIS_RY = 13
    const val AXIS_RZ = 14
    const val AXIS_HAT_X = 15
    const val AXIS_HAT_Y = 16
    const val AXIS_LTRIGGER = 17
    const val AXIS_RTRIGGER = 18
    const val AXIS_BRAKE = 23
    const val AXIS_GAS = 22

    /** Axis ids that carry stick movement, in priority order. */
    val STICK_ALIASES: Map<Int, ControllerAxis> = mapOf(
        AXIS_X to ControllerAxis.LEFT_STICK_X,
        AXIS_Y to ControllerAxis.LEFT_STICK_Y,
        AXIS_Z to ControllerAxis.RIGHT_STICK_X,
        AXIS_RZ to ControllerAxis.RIGHT_STICK_Y,
        AXIS_RX to ControllerAxis.RIGHT_STICK_X,
        AXIS_RY to ControllerAxis.RIGHT_STICK_Y
    )

    /** Axis ids that carry analog trigger values. */
    val TRIGGER_ALIASES: Map<Int, ControllerAxis> = mapOf(
        AXIS_LTRIGGER to ControllerAxis.L2,
        AXIS_RTRIGGER to ControllerAxis.R2,
        AXIS_BRAKE to ControllerAxis.L2,
        AXIS_GAS to ControllerAxis.R2
    )

    /** Axis ids that carry a D-pad hat switch. */
    val HAT_ALIASES: Map<Int, ControllerAxis> = mapOf(
        AXIS_HAT_X to ControllerAxis.LEFT_STICK_X,
        AXIS_HAT_Y to ControllerAxis.LEFT_STICK_Y
    )

    /** Stick axes are already -1..1; the clamp guards against noisy drivers. */
    fun normaliseStick(value: Float): Float = value.coerceIn(-1f, 1f)

    /** Trigger axes are 0..1 on the platforms PulsePad targets; clamp for safety. */
    fun normaliseTrigger(value: Float): Float = value.coerceIn(0f, 1f)

    /**
     * Collapses a raw axis sample list into at most one value per canonical axis.
     *
     * Alias axes can report 0 while the primary axis reports a real value (or the
     * reverse), so a non-neutral reading always wins over a neutral one.
     */
    fun resolve(samples: List<Pair<Int, Float>>): Map<ControllerAxis, Float> {
        val resolved = LinkedHashMap<ControllerAxis, Float>()
        for ((rawAxis, rawValue) in samples) {
            val canonical = STICK_ALIASES[rawAxis]
            if (canonical != null) {
                offer(resolved, canonical, normaliseStick(rawValue))
                continue
            }
            val trigger = TRIGGER_ALIASES[rawAxis]
            if (trigger != null) {
                offer(resolved, trigger, normaliseTrigger(rawValue))
                continue
            }
            val hat = HAT_ALIASES[rawAxis]
            if (hat != null && rawValue != 0f) {
                offer(resolved, hat, normaliseStick(rawValue))
            }
        }
        return resolved
    }

    private fun offer(target: MutableMap<ControllerAxis, Float>, axis: ControllerAxis, value: Float) {
        val existing = target[axis]
        if (existing == null || (existing == 0f && value != 0f)) {
            target[axis] = value
        }
    }
}
