package com.pulsepad.controller.domain.engine

import com.pulsepad.controller.domain.model.MacroStep
import com.pulsepad.controller.domain.model.MappedAction
import com.pulsepad.controller.domain.model.Point

/**
 * One stroke of a gesture, described in screen pixels and milliseconds.
 *
 * This is a plain data description rather than an Android `GestureDescription`, so
 * the translation from a mapping to a gesture is pure and unit testable; the
 * injection engine turns these specs into the framework object.
 */
data class StrokeSpec(
    val path: List<PixelPoint>,
    val startTimeMs: Long,
    val durationMs: Long
)

/** A point in screen pixels. */
data class PixelPoint(val x: Float, val y: Float)

/**
 * Converts a mapping into the strokes that reproduce it.
 *
 * Positions in a mapping are normalised 0..1, so the same profile works on a phone
 * and a tablet. This is the layer that resolves them against the actual display,
 * which is also why it takes the pixel size as a parameter instead of reading it
 * from a global: that keeps it testable and free of a Context.
 */
object GesturePlanner {

    /** The shortest stroke the framework will accept without complaint. */
    const val MIN_STROKE_MS = 1L

    /** A tap is a zero-length stroke; anything shorter reads as a drag to some apps. */
    const val TAP_DURATION_MS = 40L

    fun plan(
        action: MappedAction,
        screenWidthPx: Int,
        screenHeightPx: Int
    ): List<StrokeSpec> {
        val width = screenWidthPx.coerceAtLeast(1)
        val height = screenHeightPx.coerceAtLeast(1)

        return when (action) {
            MappedAction.Disabled -> emptyList()

            is MappedAction.Tap -> listOf(
                singleStroke(action.at, width, height, TAP_DURATION_MS)
            )

            is MappedAction.Hold -> listOf(
                singleStroke(action.at, width, height, action.durationMs.toLong())
            )

            is MappedAction.Swipe -> planSwipe(action, width, height)

            is MappedAction.Macro -> planMacro(action, width, height)

            // A turbo assignment is replayed as a stream of taps by the engine; one
            // plan call describes one tap.
            is MappedAction.Turbo -> listOf(
                singleStroke(action.at, width, height, TAP_DURATION_MS)
            )
        }
    }

    private fun planSwipe(action: MappedAction.Swipe, width: Int, height: Int): List<StrokeSpec> {
        if (action.path.size < 2) return emptyList()
        return listOf(
            StrokeSpec(
                path = action.path.map { it.toPixels(width, height) },
                startTimeMs = 0L,
                durationMs = action.durationMs.toLong().coerceAtLeast(MIN_STROKE_MS)
            )
        )
    }

    /**
     * Lays macro steps out on one timeline.
     *
     * Each tap is its own stroke, offset by the sum of the preceding durations, so
     * the framework can play the whole macro without the engine holding a coroutine
     * open between steps.
     */
    private fun planMacro(action: MappedAction.Macro, width: Int, height: Int): List<StrokeSpec> {
        val strokes = mutableListOf<StrokeSpec>()
        var cursorMs = 0L
        action.steps.forEach { step ->
            when (step) {
                is MacroStep.Tap -> {
                    val duration = if (step.holdMs > 0) step.holdMs.toLong() else TAP_DURATION_MS
                    strokes += StrokeSpec(
                        path = listOf(step.at.toPixels(width, height)),
                        startTimeMs = cursorMs,
                        durationMs = duration.coerceAtLeast(MIN_STROKE_MS)
                    )
                    cursorMs += duration
                }

                is MacroStep.Delay -> cursorMs += step.durationMs.toLong().coerceAtLeast(0L)
            }
        }
        return strokes
    }

    private fun singleStroke(
        at: Point,
        width: Int,
        height: Int,
        durationMs: Long
    ): StrokeSpec = StrokeSpec(
        path = listOf(at.toPixels(width, height)),
        startTimeMs = 0L,
        durationMs = durationMs.coerceAtLeast(MIN_STROKE_MS)
    )

    /** Resolves a normalised point against the display, clamped to stay on screen. */
    fun Point.toPixels(width: Int, height: Int): PixelPoint = PixelPoint(
        x = (x * width).coerceIn(0f, (width - 1).toFloat()),
        y = (y * height).coerceIn(0f, (height - 1).toFloat())
    )
}
