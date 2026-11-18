package com.pulsepad.controller.domain.engine

import com.pulsepad.controller.domain.model.MacroStep
import com.pulsepad.controller.domain.model.MappedAction
import com.pulsepad.controller.domain.model.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GesturePlannerTest {

    private val width = 1000
    private val height = 2000

    @Test
    fun `a disabled action plans nothing`() {
        assertTrue(
            GesturePlanner.plan(MappedAction.Disabled, width, height).isEmpty()
        )
    }

    @Test
    fun `normalised points resolve against the display`() {
        val strokes = GesturePlanner.plan(MappedAction.Tap(Point(0.5f, 0.25f)), width, height)
        assertEquals(1, strokes.size)
        val point = strokes.single().path.single()
        assertEquals(500f, point.x, 0.01f)
        assertEquals(500f, point.y, 0.01f)
    }

    @Test
    fun `points are clamped to stay on screen`() {
        val strokes = GesturePlanner.plan(MappedAction.Tap(Point(1.5f, -0.5f)), width, height)
        val point = strokes.single().path.single()
        assertTrue(point.x <= width - 1)
        assertTrue(point.y >= 0f)
    }

    @Test
    fun `a tap becomes a short stroke`() {
        val strokes = GesturePlanner.plan(MappedAction.Tap(Point(0.1f, 0.1f)), width, height)
        assertEquals(GesturePlanner.TAP_DURATION_MS, strokes.single().durationMs)
        assertEquals(0L, strokes.single().startTimeMs)
    }

    @Test
    fun `a hold carries its duration`() {
        val strokes = GesturePlanner.plan(
            MappedAction.Hold(Point(0.1f, 0.1f), durationMs = 900),
            width,
            height
        )
        assertEquals(900L, strokes.single().durationMs)
    }

    @Test
    fun `a swipe keeps its path order`() {
        val path = listOf(Point(0.1f, 0.1f), Point(0.5f, 0.5f), Point(0.9f, 0.9f))
        val strokes = GesturePlanner.plan(MappedAction.Swipe(path, durationMs = 250), width, height)
        val resolved = strokes.single().path
        assertEquals(3, resolved.size)
        assertEquals(100f, resolved.first().x, 0.01f)
        assertEquals(900f, resolved.last().x, 0.01f)
        assertEquals(250L, strokes.single().durationMs)
    }

    @Test
    fun `a swipe with fewer than two points plans nothing`() {
        assertTrue(
            GesturePlanner.plan(MappedAction.Swipe(listOf(Point(0.5f, 0.5f))), width, height).isEmpty()
        )
    }

    @Test
    fun `a macro lays its taps out on one timeline`() {
        val macro = MappedAction.Macro(
            listOf(
                MacroStep.Tap(Point(0.1f, 0.1f), holdMs = 50),
                MacroStep.Delay(120),
                MacroStep.Tap(Point(0.9f, 0.9f), holdMs = 30)
            )
        )
        val strokes = GesturePlanner.plan(macro, width, height)
        assertEquals(2, strokes.size)
        assertEquals(0L, strokes[0].startTimeMs)
        assertEquals(50L, strokes[0].durationMs)
        // The second tap starts after the first tap plus the explicit delay.
        assertEquals(170L, strokes[1].startTimeMs)
        assertEquals(30L, strokes[1].durationMs)
    }

    @Test
    fun `a macro delay does not create a stroke of its own`() {
        val macro = MappedAction.Macro(listOf(MacroStep.Delay(200)))
        assertTrue(GesturePlanner.plan(macro, width, height).isEmpty())
    }

    @Test
    fun `a turbo action plans a single tap`() {
        val strokes = GesturePlanner.plan(MappedAction.Turbo(Point(0.5f, 0.5f), 10), width, height)
        assertEquals(1, strokes.size)
        assertEquals(GesturePlanner.TAP_DURATION_MS, strokes.single().durationMs)
    }

    @Test
    fun `stroke durations are never zero`() {
        // The framework rejects a zero-duration stroke, so a hold of 0 must still
        // produce something the system will accept.
        val strokes = GesturePlanner.plan(
            MappedAction.Hold(Point(0.5f, 0.5f), durationMs = 0),
            width,
            height
        )
        assertTrue(strokes.single().durationMs >= GesturePlanner.MIN_STROKE_MS)
    }

    @Test
    fun `a degenerate display size does not divide by zero`() {
        val strokes = GesturePlanner.plan(MappedAction.Tap(Point(0.5f, 0.5f)), 0, 0)
        assertEquals(1, strokes.size)
        assertTrue(strokes.single().path.single().x >= 0f)
    }
}
