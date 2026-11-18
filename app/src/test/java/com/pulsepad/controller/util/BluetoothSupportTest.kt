package com.pulsepad.controller.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothSupportTest {

    @Test
    fun `the AOSP hid host profile id is the documented fallback`() {
        // BluetoothProfile.HID_HOST is @hide; 4 is its AOSP value.
        assertEquals(4, BluetoothSupport.HID_HOST_PROFILE_FALLBACK)
    }

    @Test
    fun `a reachable proxy with no failure means the profile is available`() {
        assertTrue(BluetoothSupport.decideHidHostAvailability(proxyObtainable = true, failure = null))
    }

    @Test
    fun `an unobtainable proxy means the fallback path is used`() {
        assertFalse(BluetoothSupport.decideHidHostAvailability(proxyObtainable = false, failure = null))
    }

    @Test
    fun `a thrown failure always reports unavailable even if a proxy was returned`() {
        assertFalse(
            BluetoothSupport.decideHidHostAvailability(
                proxyObtainable = true,
                failure = SecurityException("missing permission")
            )
        )
    }
}
