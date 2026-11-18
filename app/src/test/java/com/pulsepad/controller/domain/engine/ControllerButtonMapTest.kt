package com.pulsepad.controller.domain.engine

import com.pulsepad.controller.domain.model.ControllerButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerButtonMapTest {

    @Test
    fun `face buttons map to PlayStation shapes`() {
        assertEquals(ControllerButton.CROSS, ControllerButtonMap.buttonForKeyCode(96))
        assertEquals(ControllerButton.CIRCLE, ControllerButtonMap.buttonForKeyCode(97))
        assertEquals(ControllerButton.SQUARE, ControllerButtonMap.buttonForKeyCode(99))
        assertEquals(ControllerButton.TRIANGLE, ControllerButtonMap.buttonForKeyCode(100))
    }

    @Test
    fun `shoulder bumpers and analog triggers map to the same canonical button`() {
        assertEquals(ControllerButton.L1, ControllerButtonMap.buttonForKeyCode(102))
        assertEquals(ControllerButton.R1, ControllerButtonMap.buttonForKeyCode(103))
        assertEquals(ControllerButton.L2, ControllerButtonMap.buttonForKeyCode(104))
        assertEquals(ControllerButton.R2, ControllerButtonMap.buttonForKeyCode(105))
    }

    @Test
    fun `stick presses and system buttons map`() {
        assertEquals(ControllerButton.L3, ControllerButtonMap.buttonForKeyCode(106))
        assertEquals(ControllerButton.R3, ControllerButtonMap.buttonForKeyCode(107))
        assertEquals(ControllerButton.OPTIONS, ControllerButtonMap.buttonForKeyCode(108))
        assertEquals(ControllerButton.SHARE, ControllerButtonMap.buttonForKeyCode(109))
        assertEquals(ControllerButton.GUIDE, ControllerButtonMap.buttonForKeyCode(110))
    }

    @Test
    fun `dpad maps from both the keycode and the generic button ranges`() {
        // KEYCODE_DPAD_UP and the @hide KEYCODE_BUTTON_DPAD_UP both mean "up".
        assertEquals(ControllerButton.DPAD_UP, ControllerButtonMap.buttonForKeyCode(19))
        assertEquals(ControllerButton.DPAD_UP, ControllerButtonMap.buttonForKeyCode(201))
        assertEquals(ControllerButton.DPAD_DOWN, ControllerButtonMap.buttonForKeyCode(20))
        assertEquals(ControllerButton.DPAD_DOWN, ControllerButtonMap.buttonForKeyCode(203))
        assertEquals(ControllerButton.DPAD_LEFT, ControllerButtonMap.buttonForKeyCode(21))
        assertEquals(ControllerButton.DPAD_LEFT, ControllerButtonMap.buttonForKeyCode(205))
        assertEquals(ControllerButton.DPAD_RIGHT, ControllerButtonMap.buttonForKeyCode(22))
        assertEquals(ControllerButton.DPAD_RIGHT, ControllerButtonMap.buttonForKeyCode(204))
    }

    @Test
    fun `pads impersonating keyboards still map their start and back keys`() {
        assertEquals(ControllerButton.OPTIONS, ControllerButtonMap.buttonForKeyCode(66))
        assertEquals(ControllerButton.SHARE, ControllerButtonMap.buttonForKeyCode(4))
    }

    @Test
    fun `unrelated keys are not controller input`() {
        assertNull(ControllerButtonMap.buttonForKeyCode(29))
        assertFalse(ControllerButtonMap.isMappedKeyCode(29))
        assertTrue(ControllerButtonMap.isMappedKeyCode(96))
    }

    @Test
    fun `every mapped keycode resolves to a button`() {
        assertTrue(ControllerButtonMap.MAPPED_KEYCODES.isNotEmpty())
        ControllerButtonMap.MAPPED_KEYCODES.forEach { keyCode ->
            assertTrue(
                "keycode $keyCode should map to a button",
                ControllerButtonMap.buttonForKeyCode(keyCode) != null
            )
        }
    }
}
