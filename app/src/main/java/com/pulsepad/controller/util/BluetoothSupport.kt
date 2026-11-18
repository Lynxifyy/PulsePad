package com.pulsepad.controller.util

import android.bluetooth.BluetoothProfile

/**
 * `BluetoothProfile.HID_HOST` is an `@hide` constant: the profile exists on
 * Android 9+ but is not exposed to third-party apps in the public SDK. Reading it
 * reflectively is the only way to request the proxy without depending on a
 * framework stub, so this helper resolves it once and falls back to the AOSP
 * value when reflection is blocked (some Fire OS builds restrict it).
 *
 * The *decision* logic lives in [decideHidHostAvailability] so it can be unit
 * tested on the JVM without the Android framework.
 */
object BluetoothSupport {

    /** AOSP value of the hidden `BluetoothProfile.HID_HOST` constant. */
    const val HID_HOST_PROFILE_FALLBACK = 4

    /** Resolved lazily; null when reflection is unavailable. */
    private val reflectedHidHostProfile: Int? by lazy {
        try {
            BluetoothProfile::class.java.getField("HID_HOST").getInt(null)
        } catch (noSuchField: NoSuchFieldException) {
            AppLog.d(TAG, "HID_HOST constant missing from this platform build")
            null
        } catch (illegalAccess: IllegalAccessException) {
            AppLog.d(TAG, "HID_HOST constant is not readable on this platform build")
            null
        }
    }

    val hidHostProfile: Int
        get() = reflectedHidHostProfile ?: HID_HOST_PROFILE_FALLBACK

    /**
     * Whether the Bluetooth HID host profile can be used for direct controller
     * connections.
     *
     * `getProfileProxy` returns false when the adapter is off or the profile is
     * unsupported, and throws when the app lacks the Bluetooth permission. Both
     * cases mean "use the bonded-device fallback", never a crash.
     */
    fun decideHidHostAvailability(proxyObtainable: Boolean, failure: Throwable?): Boolean {
        if (failure != null) return false
        return proxyObtainable
    }

    private const val TAG = "BluetoothSupport"
}
