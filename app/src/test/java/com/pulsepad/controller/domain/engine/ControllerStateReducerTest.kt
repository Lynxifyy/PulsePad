package com.pulsepad.controller.domain.engine

import com.pulsepad.controller.domain.model.ControllerAxis
import com.pulsepad.controller.domain.model.ControllerButton
import com.pulsepad.controller.domain.model.ControllerInput
import com.pulsepad.controller.domain.model.ControllerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerStateReducerTest {

    @Test
    fun `pressing and releasing a button updates the pressed set`() {
        val pressed = ControllerStateReducer.reduce(
            ControllerState(),
            ControllerInput.Button(deviceId = 3, button = ControllerButton.CROSS, pressed = true)
        )
        assertTrue(pressed.pressedButtons.contains(ControllerButton.CROSS))
        assertEquals(3, pressed.deviceId)

        val released = ControllerStateReducer.reduce(
            pressed,
            ControllerInput.Button(deviceId = 3, button = ControllerButton.CROSS, pressed = false)
        )
        assertFalse(released.pressedButtons.contains(ControllerButton.CROSS))
    }

    @Test
    fun `axis samples replace the previous value for that axis`() {
        val first = ControllerStateReducer.reduce(
            ControllerState(),
            ControllerInput.Axis(deviceId = 1, axis = ControllerAxis.LEFT_STICK_X, value = 0.2f)
        )
        val second = ControllerStateReducer.reduce(
            first,
            ControllerInput.Axis(deviceId = 1, axis = ControllerAxis.LEFT_STICK_X, value = -0.8f)
        )
        assertEquals(-0.8f, second.axes[ControllerAxis.LEFT_STICK_X]!!, 0.0001f)
    }

    @Test
    fun `reducing a batch matches reducing one event at a time`() {
        val events = listOf(
            ControllerInput.Button(1, ControllerButton.L1, true),
            ControllerInput.Axis(1, ControllerAxis.L2, 0.5f),
            ControllerInput.Button(1, ControllerButton.DPAD_UP, true)
        )
        val batched = ControllerStateReducer.reduce(ControllerState(), events)
        val sequential = events.fold(ControllerState()) { state, input ->
            ControllerStateReducer.reduce(state, input)
        }
        assertEquals(sequential, batched)
    }

    @Test
    fun `independent axes do not clobber each other`() {
        val state = ControllerStateReducer.reduce(
            ControllerState(),
            listOf(
                ControllerInput.Axis(1, ControllerAxis.LEFT_STICK_X, 0.5f),
                ControllerInput.Axis(1, ControllerAxis.RIGHT_STICK_Y, -0.5f)
            )
        )
        assertEquals(0.5f, state.axes[ControllerAxis.LEFT_STICK_X]!!, 0.0001f)
        assertEquals(-0.5f, state.axes[ControllerAxis.RIGHT_STICK_Y]!!, 0.0001f)
    }
}
