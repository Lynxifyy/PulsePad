package com.pulsepad.controller.domain.engine

import com.pulsepad.controller.domain.model.CurveType
import com.pulsepad.controller.domain.model.MappedAction
import com.pulsepad.controller.domain.model.StickConfig
import com.pulsepad.controller.domain.model.TriggerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputTransformEngineTest {

    private val tolerance = 0.0005f

    // ---- Deadzone ---------------------------------------------------------- #

    @Test
    fun `values inside the deadzone collapse to zero`() {
        assertEquals(0f, InputTransformEngine.applyDeadzone(0f, 0.2f), tolerance)
        assertEquals(0f, InputTransformEngine.applyDeadzone(0.1f, 0.2f), tolerance)
        assertEquals(0f, InputTransformEngine.applyDeadzone(-0.2f, 0.2f), tolerance)
    }

    @Test
    fun `deadzone preserves sign`() {
        assertTrue(InputTransformEngine.applyDeadzone(0.5f, 0.2f) > 0f)
        assertTrue(InputTransformEngine.applyDeadzone(-0.5f, 0.2f) < 0f)
    }

    @Test
    fun `full deflection still reaches full range after a deadzone`() {
        assertEquals(1f, InputTransformEngine.applyDeadzone(1f, 0.4f), tolerance)
        assertEquals(-1f, InputTransformEngine.applyDeadzone(-1f, 0.4f), tolerance)
    }

    @Test
    fun `the deadzone edge maps to zero and the range is rescaled`() {
        assertEquals(0f, InputTransformEngine.applyDeadzone(0.3f, 0.3f), tolerance)
        // Halfway between the deadzone edge and full deflection maps to halfway.
        assertEquals(0.5f, InputTransformEngine.applyDeadzone(0.65f, 0.3f), tolerance)
    }

    @Test
    fun `a zero deadzone is a pass-through`() {
        assertEquals(0.42f, InputTransformEngine.applyDeadzone(0.42f, 0f), tolerance)
    }

    // ---- Curves ------------------------------------------------------------ #

    @Test
    fun `the linear curve is the identity`() {
        val config = StickConfig(deadzone = 0f, curve = CurveType.LINEAR)
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { input ->
            assertEquals(input, InputTransformEngine.applyCurve(input, config), tolerance)
        }
    }

    @Test
    fun `the exponential curve stays below the linear curve in the middle`() {
        val config = StickConfig(deadzone = 0f, curve = CurveType.EXPONENTIAL, exponent = 2f)
        assertTrue(InputTransformEngine.applyCurve(0.5f, config) < 0.5f)
        assertEquals(1f, InputTransformEngine.applyCurve(1f, config), tolerance)
    }

    @Test
    fun `a higher exponent is more aggressive near the centre`() {
        val gentle = StickConfig(deadzone = 0f, curve = CurveType.EXPONENTIAL, exponent = 2f)
        val steep = StickConfig(deadzone = 0f, curve = CurveType.EXPONENTIAL, exponent = 4f)
        assertTrue(
            InputTransformEngine.applyCurve(0.5f, steep) <
                InputTransformEngine.applyCurve(0.5f, gentle)
        )
    }

    @Test
    fun `the s-curve is symmetric about its midpoint`() {
        val config = StickConfig(deadzone = 0f, curve = CurveType.S_CURVE)
        assertEquals(0.5f, InputTransformEngine.applyCurve(0.5f, config), tolerance)
        // Symmetry: f(x) + f(1-x) == 1.
        listOf(0.1f, 0.3f, 0.7f, 0.9f).forEach { input ->
            val sum = InputTransformEngine.applyCurve(input, config) +
                InputTransformEngine.applyCurve(1f - input, config)
            assertEquals(1f, sum, tolerance)
        }
    }

    @Test
    fun `every curve passes through the origin and full deflection`() {
        CurveType.entries.forEach { curve ->
            val config = StickConfig(deadzone = 0f, curve = curve, exponent = 3f)
            assertEquals(0f, InputTransformEngine.applyCurve(0f, config), tolerance)
            assertEquals(1f, InputTransformEngine.applyCurve(1f, config), tolerance)
        }
    }

    @Test
    fun `curves preserve sign in both directions`() {
        CurveType.entries.forEach { curve ->
            val config = StickConfig(deadzone = 0f, curve = curve, exponent = 2f)
            val positive = InputTransformEngine.applyCurve(0.6f, config)
            val negative = InputTransformEngine.applyCurve(-0.6f, config)
            assertEquals(positive, -negative, tolerance)
        }
    }

    @Test
    fun `sensitivity scales the output`() {
        val config = StickConfig(deadzone = 0f, curve = CurveType.LINEAR, sensitivity = 0.5f)
        assertEquals(0.25f, InputTransformEngine.applyCurve(0.5f, config), tolerance)
    }

    @Test
    fun `sensitivity above one clamps at full deflection`() {
        val config = StickConfig(deadzone = 0f, curve = CurveType.LINEAR, sensitivity = 2f)
        assertEquals(1f, InputTransformEngine.applyCurve(0.8f, config), tolerance)
        assertEquals(0.5f, InputTransformEngine.applyCurve(0.25f, config), tolerance)
    }

    // ---- Full stick pipeline ----------------------------------------------- #

    @Test
    fun `the stick pipeline applies the deadzone before the curve`() {
        val config = StickConfig(deadzone = 0.2f, curve = CurveType.EXPONENTIAL, exponent = 2f)
        // 0.6 is 0.5 of the way out of a 0.2 deadzone, so the curve sees 0.5 -> 0.25.
        assertEquals(0.25f, InputTransformEngine.transformAxis(0.6f, config), tolerance)
        assertEquals(0f, InputTransformEngine.transformAxis(0.1f, config), tolerance)
    }

    @Test
    fun `the stick pipeline never exceeds full deflection`() {
        val config = StickConfig(deadzone = 0.1f, curve = CurveType.LINEAR, sensitivity = 2f)
        assertTrue(InputTransformEngine.transformAxis(1f, config) <= 1f)
        assertTrue(InputTransformEngine.transformAxis(-1f, config) >= -1f)
    }

    // ---- Triggers ---------------------------------------------------------- #

    @Test
    fun `a trigger below the threshold does not register`() {
        val config = TriggerConfig(activationThreshold = 0.4f)
        assertNull(InputTransformEngine.transformTrigger(0.39f, config))
        assertNull(InputTransformEngine.transformTrigger(0f, config))
    }

    @Test
    fun `a trigger at the threshold starts at zero output`() {
        val config = TriggerConfig(activationThreshold = 0.4f, maxValue = 1f)
        assertEquals(0f, InputTransformEngine.transformTrigger(0.4f, config)!!, tolerance)
    }

    @Test
    fun `a fully pulled trigger reaches the maximum output`() {
        val config = TriggerConfig(activationThreshold = 0.4f, maxValue = 0.8f)
        assertEquals(0.8f, InputTransformEngine.transformTrigger(1f, config)!!, tolerance)
    }

    @Test
    fun `the trigger output sweeps the whole range above the threshold`() {
        val config = TriggerConfig(activationThreshold = 0.2f, maxValue = 1f)
        // Halfway between threshold and full pull is halfway up the output.
        assertEquals(0.5f, InputTransformEngine.transformTrigger(0.6f, config)!!, tolerance)
    }

    @Test
    fun `a trigger with no threshold is always active`() {
        val config = TriggerConfig(activationThreshold = 0f, maxValue = 1f)
        assertNotNull(InputTransformEngine.transformTrigger(0f, config))
        assertEquals(0.5f, InputTransformEngine.transformTrigger(0.5f, config)!!, tolerance)
    }

    // ---- Curve sampling ---------------------------------------------------- #

    @Test
    fun `curve sampling spans the full input range`() {
        val samples = InputTransformEngine.curveSamples(StickConfig(deadzone = 0f), samples = 5)
        assertEquals(5, samples.size)
        assertEquals(0f, samples.first().first, tolerance)
        assertEquals(1f, samples.last().first, tolerance)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `curve sampling needs at least two points`() {
        InputTransformEngine.curveSamples(StickConfig(), samples = 1)
    }

    // ---- Turbo ------------------------------------------------------------- #

    @Test
    fun `turbo interval is the inverse of the rate`() {
        assertEquals(100L, InputTransformEngine.turboIntervalMs(10))
        assertEquals(200L, InputTransformEngine.turboIntervalMs(5))
        assertEquals(33L, InputTransformEngine.turboIntervalMs(30))
    }

    @Test
    fun `turbo interval clamps out-of-range rates`() {
        // Below and above the supported band clamp to the band's endpoints.
        assertEquals(
            InputTransformEngine.turboIntervalMs(MappedAction.Turbo.TURBO_MIN_HZ),
            InputTransformEngine.turboIntervalMs(1)
        )
        assertEquals(
            InputTransformEngine.turboIntervalMs(MappedAction.Turbo.TURBO_MAX_HZ),
            InputTransformEngine.turboIntervalMs(120)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `turbo rejects an unsupported rate`() {
        MappedAction.Turbo(com.pulsepad.controller.domain.model.Point(0.5f, 0.5f), rateHz = 100)
    }
}
