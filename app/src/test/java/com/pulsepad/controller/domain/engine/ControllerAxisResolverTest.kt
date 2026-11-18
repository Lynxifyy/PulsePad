package com.pulsepad.controller.domain.engine

import com.pulsepad.controller.domain.model.ControllerAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerAxisResolverTest {

    @Test
    fun `left stick uses the primary axis ids`() {
        val resolved = ControllerAxisResolver.resolve(
            listOf(
                ControllerAxisResolver.AXIS_X to 0.5f,
                ControllerAxisResolver.AXIS_Y to -0.25f
            )
        )
        assertEquals(0.5f, resolved[ControllerAxis.LEFT_STICK_X]!!, 0.0001f)
        assertEquals(-0.25f, resolved[ControllerAxis.LEFT_STICK_Y]!!, 0.0001f)
    }

    @Test
    fun `right stick is read from the z pair`() {
        val resolved = ControllerAxisResolver.resolve(
            listOf(
                ControllerAxisResolver.AXIS_Z to 0.75f,
                ControllerAxisResolver.AXIS_RZ to -0.5f
            )
        )
        assertEquals(0.75f, resolved[ControllerAxis.RIGHT_STICK_X]!!, 0.0001f)
        assertEquals(-0.5f, resolved[ControllerAxis.RIGHT_STICK_Y]!!, 0.0001f)
    }

    @Test
    fun `right stick is also read from the rx pair used by some Xbox drivers`() {
        val resolved = ControllerAxisResolver.resolve(
            listOf(
                ControllerAxisResolver.AXIS_RX to 0.4f,
                ControllerAxisResolver.AXIS_RY to 0.6f
            )
        )
        assertEquals(0.4f, resolved[ControllerAxis.RIGHT_STICK_X]!!, 0.0001f)
        assertEquals(0.6f, resolved[ControllerAxis.RIGHT_STICK_Y]!!, 0.0001f)
    }

    @Test
    fun `non-neutral alias wins over a neutral primary axis`() {
        // AXIS_Z reports 0 while AXIS_RX carries the real value.
        val resolved = ControllerAxisResolver.resolve(
            listOf(
                ControllerAxisResolver.AXIS_Z to 0f,
                ControllerAxisResolver.AXIS_RX to 0.9f
            )
        )
        assertEquals(0.9f, resolved[ControllerAxis.RIGHT_STICK_X]!!, 0.0001f)
    }

    @Test
    fun `neutral alias does not overwrite a real primary reading`() {
        val resolved = ControllerAxisResolver.resolve(
            listOf(
                ControllerAxisResolver.AXIS_Z to 0.9f,
                ControllerAxisResolver.AXIS_RX to 0f
            )
        )
        assertEquals(0.9f, resolved[ControllerAxis.RIGHT_STICK_X]!!, 0.0001f)
    }

    @Test
    fun `triggers are read from both trigger and brake-or-gas axes`() {
        val fromTriggers = ControllerAxisResolver.resolve(
            listOf(
                ControllerAxisResolver.AXIS_LTRIGGER to 1f,
                ControllerAxisResolver.AXIS_RTRIGGER to 0.5f
            )
        )
        assertEquals(1f, fromTriggers[ControllerAxis.L2]!!, 0.0001f)
        assertEquals(0.5f, fromTriggers[ControllerAxis.R2]!!, 0.0001f)

        val fromPedals = ControllerAxisResolver.resolve(
            listOf(
                ControllerAxisResolver.AXIS_BRAKE to 0.25f,
                ControllerAxisResolver.AXIS_GAS to 0.75f
            )
        )
        assertEquals(0.25f, fromPedals[ControllerAxis.L2]!!, 0.0001f)
        assertEquals(0.75f, fromPedals[ControllerAxis.R2]!!, 0.0001f)
    }

    @Test
    fun `trigger values are clamped to the unipolar range`() {
        val resolved = ControllerAxisResolver.resolve(
            listOf(ControllerAxisResolver.AXIS_LTRIGGER to 4f)
        )
        assertEquals(1f, resolved[ControllerAxis.L2]!!, 0.0001f)
    }

    @Test
    fun `stick values are clamped to the bipolar range`() {
        assertEquals(1f, ControllerAxisResolver.normaliseStick(3f), 0.0001f)
        assertEquals(-1f, ControllerAxisResolver.normaliseStick(-3f), 0.0001f)
    }

    @Test
    fun `a neutral hat axis produces no reading`() {
        val resolved = ControllerAxisResolver.resolve(
            listOf(ControllerAxisResolver.AXIS_HAT_X to 0f)
        )
        assertTrue(resolved.isEmpty())
    }

    @Test
    fun `a pressed hat axis is treated as stick deflection`() {
        val resolved = ControllerAxisResolver.resolve(
            listOf(ControllerAxisResolver.AXIS_HAT_X to 1f)
        )
        assertEquals(1f, resolved[ControllerAxis.LEFT_STICK_X]!!, 0.0001f)
    }

    @Test
    fun `unknown axes are ignored`() {
        val resolved = ControllerAxisResolver.resolve(listOf(99 to 0.5f))
        assertTrue(resolved.isEmpty())
    }
}
