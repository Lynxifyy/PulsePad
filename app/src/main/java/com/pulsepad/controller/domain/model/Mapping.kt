package com.pulsepad.controller.domain.model

import kotlinx.serialization.Serializable

/**
 * A screen position in normalised 0..1 coordinates.
 *
 * Mappings are stored resolution-independently so a profile saved on a phone still
 * works on a tablet; the accessibility engine converts to pixels at dispatch time,
 * using the display metrics it reads then.
 */
@Serializable
data class Point(val x: Float, val y: Float)

/** One step of a macro: either a tap or a wait. */
@Serializable
sealed interface MacroStep {
    @Serializable
    data class Tap(val at: Point, val holdMs: Int = 0) : MacroStep

    /** Pause between steps, so a macro can express timing without extra taps. */
    @Serializable
    data class Delay(val durationMs: Int) : MacroStep
}

/**
 * What a control does when it is actuated.
 *
 * These are the only actions PulsePad can perform: everything is expressed as a
 * gesture, because `dispatchGesture` is the sole non-root injection API. There is
 * no "virtual button" action - a game that reads gamepad input directly will not
 * see any of these.
 */
@Serializable
sealed interface MappedAction {

    /** The control is intentionally inactive. */
    @Serializable
    data object Disabled : MappedAction

    /** A single tap at a screen position. */
    @Serializable
    data class Tap(val at: Point) : MappedAction

    /** Press and hold at a screen position for [durationMs]. */
    @Serializable
    data class Hold(val at: Point, val durationMs: Int) : MappedAction

    /** A drag through [path], in order, over [durationMs]. */
    @Serializable
    data class Swipe(val path: List<Point>, val durationMs: Int = 150) : MappedAction

    /** A sequence of taps and waits, replayed in order. */
    @Serializable
    data class Macro(val steps: List<MacroStep>) : MappedAction

    /**
     * Repeated taps while the control is held, at [rateHz] taps per second.
     * The supported range is 5..30 Hz; beyond that the gesture pipeline cannot
     * keep up and the taps coalesce.
     */
    @Serializable
    data class Turbo(val at: Point, val rateHz: Int) : MappedAction {
        init {
            require(rateHz in TURBO_MIN_HZ..TURBO_MAX_HZ) {
                "turbo rate must be $TURBO_MIN_HZ..$TURBO_MAX_HZ Hz, was $rateHz"
            }
        }

        companion object {
            const val TURBO_MIN_HZ = 5
            const val TURBO_MAX_HZ = 30
        }
    }
}

/** The response curve applied to an analog stick's magnitude. */
@Serializable
enum class CurveType {
    /** Output follows input directly. */
    LINEAR,

    /** Small movements stay small; precision near the centre. */
    EXPONENTIAL,

    /** Gentle near the centre and near the edge, steeper in between. */
    S_CURVE
}

/** Deadzone, curve and sensitivity for one analog stick. */
@Serializable
data class StickConfig(
    val deadzone: Float = 0.12f,
    val curve: CurveType = CurveType.LINEAR,
    val exponent: Float = 2.0f,
    /** Overall output multiplier, so a stick can be tamed without changing the curve. */
    val sensitivity: Float = 1.0f
) {
    init {
        require(deadzone in 0f..MAX_DEADZONE) { "deadzone must be 0..$MAX_DEADZONE" }
        require(exponent >= 1f) { "exponent must be at least 1" }
        require(sensitivity in 0f..MAX_SENSITIVITY) { "sensitivity must be 0..$MAX_SENSITIVITY" }
    }

    companion object {
        const val MAX_DEADZONE = 0.9f
        const val MAX_SENSITIVITY = 2.0f
    }
}

/** When a trigger counts as pressed, and how hard it presses at full pull. */
@Serializable
data class TriggerConfig(
    /** Pull fraction at which the trigger registers as pressed. */
    val activationThreshold: Float = 0.35f,
    /** Output multiplier applied to the pull, so a full pull can be softened. */
    val maxValue: Float = 1.0f
) {
    init {
        require(activationThreshold in 0f..1f) { "threshold must be 0..1" }
        require(maxValue in 0f..1f) { "max value must be 0..1" }
    }
}

/** One button's assignment. */
@Serializable
data class ButtonMapping(
    val source: ControllerButton,
    val action: MappedAction
) {
    val isDisabled: Boolean
        get() = action is MappedAction.Disabled
}

/**
 * The complete, self-contained configuration the engine runs on.
 *
 * Everything the engine needs is here, so a configuration can be serialised,
 * diffed and unit tested without touching Android.
 */
@Serializable
data class MappingConfiguration(
    val buttonMappings: Map<ControllerButton, MappedAction> = emptyMap(),
    val leftStick: StickConfig = StickConfig(),
    val rightStick: StickConfig = StickConfig(),
    val trigger: TriggerConfig = TriggerConfig()
) {
    fun actionFor(button: ControllerButton): MappedAction =
        buttonMappings[button] ?: MappedAction.Disabled

    fun withMapping(button: ControllerButton, action: MappedAction): MappingConfiguration =
        copy(buttonMappings = buttonMappings + (button to action))

    fun withoutMapping(button: ControllerButton): MappingConfiguration =
        copy(buttonMappings = buttonMappings - button)

    /** How many controls currently do something. */
    val mappedCount: Int
        get() = buttonMappings.values.count { it !is MappedAction.Disabled }
}
