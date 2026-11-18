package com.pulsepad.controller.domain.model

/**
 * The four runtime capabilities PulsePad probes before deciding how to behave.
 * None of them is fatal: each one selects a fallback path instead of blocking
 * the user, which matters on Amazon Fire OS where accessibility services and the
 * Bluetooth HID host profile may be restricted.
 */
data class DeviceCapabilities(
    val accessibilityServiceEnabled: Boolean,
    val openGlEs30Available: Boolean,
    val bluetoothHidHostAvailable: Boolean,
    val playServicesPresent: Boolean,
    val androidSdkInt: Int,
    val manufacturer: String,
    val deviceModel: String
) {
    /**
     * Fire OS ships without Google Play Services and occasionally restricts
     * third-party accessibility services, so the platform is treated as a
     * first-class target rather than an edge case.
     */
    val isFireOs: Boolean
        get() = manufacturer.equals("Amazon", ignoreCase = true)

    /** PulsePad requires API 28; anything older gets a compatibility notice. */
    val isAndroidVersionSupported: Boolean
        get() = androidSdkInt >= MIN_SUPPORTED_SDK

    companion object {
        const val MIN_SUPPORTED_SDK = 28
    }
}

/** Result of a single capability probe, with a human-readable explanation. */
data class CapabilityReport(
    val capability: Capability,
    val available: Boolean,
    val detail: String
)

enum class Capability {
    ACCESSIBILITY_SERVICE,
    OPEN_GL_ES_3,
    BLUETOOTH_HID_HOST,
    PLAY_SERVICES
}
