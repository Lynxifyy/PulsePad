package com.pulsepad.controller.domain.engine

import com.pulsepad.controller.domain.model.CurveType
import com.pulsepad.controller.domain.model.StickConfig
import com.pulsepad.controller.domain.model.TriggerConfig
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

/**
 * The pure transform pipeline: raw input to mapped output.
 *
 * `RawInput -> Deadzone -> ResponseCurve -> Mapping -> OutputAction`
 *
 * Nothing here touches Android, so every rule is unit testable and the same code
 * runs whether the input came from the accessibility service or the foreground
 * capture service. The engine that actually dispatches gestures lives elsewhere;
 * this layer only decides what should happen.
 */
object InputTransformEngine {

    /**
     * Applies the deadzone to a raw axis value.
     *
     * Values inside the deadzone collapse to zero. Values outside are rescaled from
     * the edge of the deadzone to 1, so the axis still reaches its full range after
     * the deadzone is carved out - without that rescale a large deadzone would make
     * the stick feel like it can never reach the maximum.
     */
    fun applyDeadzone(value: Float, deadzone: Float): Float {
        val clampedDeadzone = deadzone.coerceIn(0f, StickConfig.MAX_DEADZONE)
        val magnitude = abs(value)
        if (magnitude <= clampedDeadzone) return 0f
        val rescaled = (magnitude - clampedDeadzone) / (1f - clampedDeadzone)
        return (rescaled * sign(value)).coerceIn(-1f, 1f)
    }

    /**
     * Applies the response curve to an already-deadzoned magnitude.
     *
     * [value] is expected in -1..1; the sign is preserved and only the magnitude is
     * shaped, so the curve behaves identically in all four quadrants.
     */
    fun applyCurve(value: Float, config: StickConfig): Float {
        val magnitude = abs(value).coerceIn(0f, 1f)
        if (magnitude == 0f) return 0f
        val shaped = when (config.curve) {
            CurveType.LINEAR -> magnitude
            CurveType.EXPONENTIAL -> magnitude.pow(config.exponent.coerceAtLeast(1f))
            CurveType.S_CURVE -> smoothStep(magnitude)
        }
        val scaled = (shaped * config.sensitivity).coerceIn(0f, 1f)
        return scaled * sign(value)
    }

    /** The whole stick pipeline: deadzone, then curve, then sensitivity. */
    fun transformAxis(value: Float, config: StickConfig): Float =
        applyCurve(applyDeadzone(value, config.deadzone), config)

    /**
     * Transforms a trigger pull into a 0..1 output, or null when the pull is below
     * the activation threshold.
     *
     * The pull is rescaled from the threshold to 1, so the output sweeps the whole
     * range above the threshold rather than starting part-way up.
     */
    fun transformTrigger(value: Float, config: TriggerConfig): Float? {
        val pull = value.coerceIn(0f, 1f)
        if (pull < config.activationThreshold) return null
        val span = (1f - config.activationThreshold).coerceAtLeast(MIN_TRIGGER_SPAN)
        val normalised = ((pull - config.activationThreshold) / span).coerceIn(0f, 1f)
        return normalised * config.maxValue
    }

    /**
     * Samples the curve for the live preview graph, at [samples] points across 0..1.
     * Returns pairs of (input, output) magnitudes.
     */
    fun curveSamples(config: StickConfig, samples: Int = 32): List<Pair<Float, Float>> {
        require(samples >= 2) { "need at least two samples" }
        return (0 until samples).map { index ->
            val input = index.toFloat() / (samples - 1)
            input to applyCurve(input, config)
        }
    }

    /**
     * The interval between turbo taps, in milliseconds.
     *
     * Returned as a plain Long so the caller can use it with `delay`; the engine
     * clamps the rate, so this never divides by zero.
     */
    fun turboIntervalMs(rateHz: Int): Long {
        val clamped = rateHz.coerceIn(
            com.pulsepad.controller.domain.model.MappedAction.Turbo.TURBO_MIN_HZ,
            com.pulsepad.controller.domain.model.MappedAction.Turbo.TURBO_MAX_HZ
        )
        return (MILLIS_PER_SECOND / clamped).toLong()
    }

    private fun smoothStep(value: Float): Float = value * value * (3f - 2f * value)

    private const val MILLIS_PER_SECOND = 1_000f
    private const val MIN_TRIGGER_SPAN = 0.01f
}
