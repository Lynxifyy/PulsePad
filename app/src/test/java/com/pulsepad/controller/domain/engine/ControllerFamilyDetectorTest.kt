package com.pulsepad.controller.domain.engine

import com.pulsepad.controller.domain.model.ControllerFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControllerFamilyDetectorTest {

    @Test
    fun `dualshock 4 names are detected`() {
        assertEquals(
            ControllerFamily.DUALSHOCK_4,
            ControllerFamilyDetector.detect("Wireless Controller")
        )
        assertEquals(
            ControllerFamily.DUALSHOCK_4,
            ControllerFamilyDetector.detect("DUALSHOCK 4 Wireless Controller")
        )
    }

    @Test
    fun `dualsense wins over the generic wireless controller label`() {
        assertEquals(
            ControllerFamily.DUALSENSE,
            ControllerFamilyDetector.detect("DualSense Wireless Controller")
        )
        assertEquals(
            ControllerFamily.DUALSENSE,
            ControllerFamilyDetector.detect("DualSense Edge Wireless Controller")
        )
    }

    @Test
    fun `xbox names are detected`() {
        assertEquals(
            ControllerFamily.XBOX_WIRELESS,
            ControllerFamilyDetector.detect("Xbox Wireless Controller")
        )
        assertEquals(
            ControllerFamily.XBOX_WIRELESS,
            ControllerFamilyDetector.detect("Microsoft XBOX One Controller")
        )
    }

    @Test
    fun `detection is case insensitive and tolerant of suffixes`() {
        assertEquals(
            ControllerFamily.DUALSENSE,
            ControllerFamilyDetector.detect("dualsense wireless controller (2)")
        )
    }

    @Test
    fun `unknown and blank names are not guessed`() {
        assertEquals(ControllerFamily.UNKNOWN, ControllerFamilyDetector.detect("BT Remote"))
        assertEquals(ControllerFamily.UNKNOWN, ControllerFamilyDetector.detect(null))
        assertEquals(ControllerFamily.UNKNOWN, ControllerFamilyDetector.detect("   "))
    }

    @Test
    fun `only known families resolve to a model asset`() {
        assertNull(ControllerFamilyDetector.modelAssetName(ControllerFamily.UNKNOWN))
        assertEquals(
            "models/dualsense.glb",
            ControllerFamilyDetector.modelAssetName(ControllerFamily.DUALSENSE)
        )
        assertEquals(
            "models/dualshock4.glb",
            ControllerFamilyDetector.modelAssetName(ControllerFamily.DUALSHOCK_4)
        )
        assertEquals(
            "models/xbox_wireless.glb",
            ControllerFamilyDetector.modelAssetName(ControllerFamily.XBOX_WIRELESS)
        )
    }
}
