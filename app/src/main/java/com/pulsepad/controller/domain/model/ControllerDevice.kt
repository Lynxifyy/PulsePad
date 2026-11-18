package com.pulsepad.controller.domain.model

/**
 * A controller PulsePad can talk to.
 *
 * Two sources feed this model, because no single API covers every device:
 *
 * * `BluetoothHidHost` connected devices, when the profile is available.
 * * `InputManager` input devices, which is the only source on Fire OS builds that
 *   restrict the HID host profile.
 *
 * [deviceId] is the `InputDevice` id when the device is known to the input
 * system, otherwise [NO_INPUT_DEVICE].
 */
data class ControllerDevice(
    val deviceId: Int,
    val name: String,
    val address: String?,
    val family: ControllerFamily,
    val source: DiscoverySource,
    val isConnected: Boolean
) {
    /** Stable identity across refreshes: the MAC when known, else the name. */
    val stableId: String
        get() = address?.takeIf { it.isNotBlank() } ?: name

    companion object {
        const val NO_INPUT_DEVICE = -1
    }
}

enum class ControllerFamily {
    DUALSHOCK_4,
    DUALSENSE,
    XBOX_WIRELESS,

    /** A pad PulsePad can read but cannot identify by name. */
    UNKNOWN
}

/** Where a [ControllerDevice] entry came from. */
enum class DiscoverySource {
    /** Reported by the Bluetooth HID host profile. */
    HID_HOST,

    /** A bonded Bluetooth device that looks like a peripheral. */
    BONDED,

    /** Visible to the input system; no Bluetooth details available. */
    INPUT_DEVICE
}

/** Connection lifecycle for the currently targeted controller. */
sealed interface ControllerConnectionState {
    data object Disconnected : ControllerConnectionState

    data class Connecting(val device: ControllerDevice) : ControllerConnectionState

    data class Connected(val device: ControllerDevice) : ControllerConnectionState

    /**
     * Connection could not be established. [reason] tells the UI which recovery
     * guidance to show; PulsePad never leaves the user at a dead end.
     */
    data class Error(
        val device: ControllerDevice?,
        val reason: ConnectionErrorReason
    ) : ControllerConnectionState
}

enum class ConnectionErrorReason {
    /** The HID host profile is unavailable, so pairing must happen in Settings. */
    HID_HOST_UNAVAILABLE,

    /** A required Bluetooth permission has not been granted. */
    MISSING_PERMISSION,

    /** The system rejected the connection attempt. */
    SYSTEM_REJECTED
}
