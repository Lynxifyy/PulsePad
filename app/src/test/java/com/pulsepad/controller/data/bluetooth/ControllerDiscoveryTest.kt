package com.pulsepad.controller.data.bluetooth

import com.pulsepad.controller.domain.model.ControllerDevice
import com.pulsepad.controller.domain.model.ControllerFamily
import com.pulsepad.controller.domain.model.DiscoverySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerDiscoveryTest {

    @Test
    fun `a recognised controller name is a candidate without any device class`() {
        assertTrue(ControllerDiscovery.isControllerCandidate("Wireless Controller", null))
        assertTrue(ControllerDiscovery.isControllerCandidate("Xbox Wireless Controller", null))
    }

    @Test
    fun `an unrecognised name is a candidate only when it is a peripheral`() {
        assertTrue(
            ControllerDiscovery.isControllerCandidate(
                "BT Gamepad",
                ControllerDiscovery.MAJOR_CLASS_PERIPHERAL
            )
        )
        assertFalse(ControllerDiscovery.isControllerCandidate("BT Gamepad", 256))
        assertFalse(ControllerDiscovery.isControllerCandidate("BT Gamepad", null))
    }

    @Test
    fun `bonded and input observations of the same controller merge into one entry`() {
        val merged = ControllerDiscovery.merge(
            listOf(
                ControllerDiscovery.DiscoveryRecord(
                    name = "DualSense Wireless Controller",
                    address = "AA:BB:CC:DD:EE:FF",
                    majorClass = ControllerDiscovery.MAJOR_CLASS_PERIPHERAL,
                    source = DiscoverySource.BONDED,
                    isConnected = false
                ),
                ControllerDiscovery.DiscoveryRecord(
                    name = "DualSense Wireless Controller",
                    address = null,
                    majorClass = null,
                    deviceId = 7,
                    source = DiscoverySource.INPUT_DEVICE,
                    isConnected = true
                )
            )
        )
        assertEquals(1, merged.size)
        val device = merged.first()
        assertEquals(7, device.deviceId)
        assertEquals("AA:BB:CC:DD:EE:FF", device.address)
        assertEquals(ControllerFamily.DUALSENSE, device.family)
        assertTrue(device.isConnected)
    }

    @Test
    fun `the address is the merge key so a richer source wins`() {
        val merged = ControllerDiscovery.merge(
            listOf(
                ControllerDiscovery.DiscoveryRecord(
                    name = "Xbox Wireless Controller",
                    address = "11:22:33:44:55:66",
                    majorClass = null,
                    source = DiscoverySource.HID_HOST,
                    isConnected = true
                ),
                ControllerDiscovery.DiscoveryRecord(
                    name = "Xbox Wireless Controller",
                    address = "11:22:33:44:55:66",
                    majorClass = null,
                    source = DiscoverySource.BONDED,
                    isConnected = true
                )
            )
        )
        assertEquals(1, merged.size)
        assertEquals(DiscoverySource.HID_HOST, merged.first().source)
    }

    @Test
    fun `nameless records are dropped rather than shown blank`() {
        val merged = ControllerDiscovery.merge(
            listOf(
                ControllerDiscovery.DiscoveryRecord(
                    name = null,
                    address = "AA:BB:CC:DD:EE:FF",
                    majorClass = null,
                    source = DiscoverySource.BONDED
                )
            )
        )
        assertTrue(merged.isEmpty())
    }

    @Test
    fun `connected controllers sort ahead of disconnected ones`() {
        val merged = ControllerDiscovery.merge(
            listOf(
                ControllerDiscovery.DiscoveryRecord(
                    name = "Zzz Controller",
                    address = "01:00:00:00:00:01",
                    majorClass = ControllerDiscovery.MAJOR_CLASS_PERIPHERAL,
                    source = DiscoverySource.BONDED,
                    isConnected = true
                ),
                ControllerDiscovery.DiscoveryRecord(
                    name = "Aaa Controller",
                    address = "01:00:00:00:00:02",
                    majorClass = ControllerDiscovery.MAJOR_CLASS_PERIPHERAL,
                    source = DiscoverySource.BONDED,
                    isConnected = false
                )
            )
        )
        assertEquals("Zzz Controller", merged.first().name)
    }

    @Test
    fun `a device with no address falls back to its name as the key`() {
        val merged = ControllerDiscovery.merge(
            listOf(
                ControllerDiscovery.DiscoveryRecord(
                    name = "Generic Pad",
                    address = null,
                    majorClass = null,
                    source = DiscoverySource.BONDED
                ),
                ControllerDiscovery.DiscoveryRecord(
                    name = "Generic Pad",
                    address = null,
                    majorClass = null,
                    deviceId = 4,
                    source = DiscoverySource.INPUT_DEVICE
                )
            )
        )
        assertEquals(1, merged.size)
        assertEquals(4, merged.first().deviceId)
    }

    @Test
    fun `bluetooth-only observations carry no input device id`() {
        val merged = ControllerDiscovery.merge(
            listOf(
                ControllerDiscovery.DiscoveryRecord(
                    name = "Generic Pad",
                    address = "AA:BB:CC:DD:EE:00",
                    majorClass = null,
                    source = DiscoverySource.BONDED
                )
            )
        )
        assertEquals(ControllerDevice.NO_INPUT_DEVICE, merged.first().deviceId)
    }
}
