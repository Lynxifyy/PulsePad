package com.pulsepad.controller.data.bluetooth

import com.pulsepad.controller.domain.engine.ControllerFamilyDetector
import com.pulsepad.controller.domain.model.ControllerDevice
import com.pulsepad.controller.domain.model.ControllerFamily
import com.pulsepad.controller.domain.model.DiscoverySource

/**
 * Pure rules for deciding which Bluetooth devices are controllers and for
 * merging the two discovery sources into one list.
 *
 * `BluetoothClass.Device.Major.PERIPHERAL` is the reliable marker on devices
 * where the HID host profile is unavailable: Android does not expose a
 * "gamepad" major class, so peripheral devices are the only candidates that can
 * be enumerated without the profile. Names that look like known controllers are
 * accepted even when the class is unhelpful, because Fire OS drivers frequently
 * report third-party pads with a generic class.
 *
 * Free of Android framework types so it is unit testable on the JVM.
 */
object ControllerDiscovery {

    /** `android.bluetooth.BluetoothClass.Device.Major.PERIPHERAL`. */
    const val MAJOR_CLASS_PERIPHERAL = 1280

    /** `android.bluetooth.BluetoothClass.Device.PERIPHERAL_KEYBOARD_POINTING`. */
    const val DEVICE_CLASS_PERIPHERAL_KEYBOARD_POINTING = 1472

    /**
     * Whether a bonded device should be offered as a controller.
     *
     * [majorClass] is null when the device class could not be read, in which case
     * the name is the only signal available.
     */
    fun isControllerCandidate(name: String?, majorClass: Int?): Boolean {
        if (ControllerFamilyDetector.detect(name) != ControllerFamily.UNKNOWN) return true
        return majorClass == MAJOR_CLASS_PERIPHERAL
    }

    /** A flat record of what a discovery source observed. */
    data class DiscoveryRecord(
        val name: String?,
        val address: String?,
        val majorClass: Int?,
        val deviceId: Int = ControllerDevice.NO_INPUT_DEVICE,
        val source: DiscoverySource,
        val isConnected: Boolean = false
    )

    /**
     * Merges HID host, bonded-device and input-device observations into one list.
     *
     * Keying is by lower-cased name, not address: the input-device source cannot
     * read a Bluetooth address (`InputDevice.getBluetoothAddress` is `@hide`), so
     * address keying would list the same physical controller twice - once with the
     * address from Bluetooth, once without it from the input system. A second pass
     * then collapses entries that share an address but were reported under
     * different names.
     *
     * A later record upgrades an earlier one rather than duplicating it, and richer
     * sources win: an input-device observation carries the `deviceId` that a
     * Bluetooth-only record cannot, while a Bluetooth record carries the address
     * and connection state. Connected controllers are listed first.
     */
    fun merge(records: List<DiscoveryRecord>): List<ControllerDevice> {
        val byName = LinkedHashMap<String, ControllerDevice>()

        for (record in records) {
            val name = record.name?.takeIf { it.isNotBlank() } ?: continue
            val key = name.lowercase()
            val existing = byName[key]

            byName[key] = ControllerDevice(
                deviceId = when {
                    record.deviceId != ControllerDevice.NO_INPUT_DEVICE -> record.deviceId
                    existing != null -> existing.deviceId
                    else -> ControllerDevice.NO_INPUT_DEVICE
                },
                name = name,
                address = record.address?.takeIf { it.isNotBlank() } ?: existing?.address,
                family = ControllerFamilyDetector.detect(name).takeIf {
                    it != ControllerFamily.UNKNOWN
                } ?: existing?.family ?: ControllerFamily.UNKNOWN,
                source = if (existing != null && record.source == DiscoverySource.BONDED) {
                    existing.source
                } else {
                    record.source
                },
                isConnected = record.isConnected || (existing?.isConnected ?: false)
            )
        }

        return collapseSharedAddresses(byName.values).sortedWith(
            compareByDescending<ControllerDevice> { it.isConnected }.thenBy { it.name.lowercase() }
        )
    }

    /**
     * Collapses entries that report the same non-null address. Drivers sometimes
     * advertise a different name per profile, which would otherwise leave two rows
     * for one controller.
     */
    private fun collapseSharedAddresses(devices: Collection<ControllerDevice>): List<ControllerDevice> {
        val byAddress = LinkedHashMap<String, ControllerDevice>()
        val withoutAddress = mutableListOf<ControllerDevice>()

        for (device in devices) {
            val address = device.address
            if (address == null) {
                withoutAddress += device
                continue
            }
            val existing = byAddress[address]
            byAddress[address] = if (existing == null) {
                device
            } else {
                existing.copy(
                    deviceId = if (device.deviceId != ControllerDevice.NO_INPUT_DEVICE) {
                        device.deviceId
                    } else {
                        existing.deviceId
                    },
                    isConnected = existing.isConnected || device.isConnected,
                    family = if (existing.family == ControllerFamily.UNKNOWN) {
                        device.family
                    } else {
                        existing.family
                    }
                )
            }
        }

        return byAddress.values + withoutAddress
    }
}
