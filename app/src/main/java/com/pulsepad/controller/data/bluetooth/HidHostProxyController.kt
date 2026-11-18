package com.pulsepad.controller.data.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import com.pulsepad.controller.util.AppLog

/**
 * Reflective wrapper around the `BluetoothHidHost` profile proxy.
 *
 * The proxy is obtained as the public [BluetoothProfile] type, but
 * `BluetoothHidHost.connect` / `disconnect` are `@hide`, so they can only be
 * reached through reflection. Every failure mode degrades to "not available"
 * rather than throwing, because a restricted Fire OS build returning no usable
 * proxy is an expected configuration, not an error.
 */
class HidHostProxyController(private val proxy: BluetoothProfile) {

    /** True when the proxy exposes both connection methods on this build. */
    val isUsable: Boolean by lazy {
        hasMethod(CONNECT_METHOD) && hasMethod(DISCONNECT_METHOD)
    }

    /**
     * Requests a connection. Returns whether the request was accepted; the actual
     * state change arrives later as an ACL broadcast.
     */
    fun connect(device: BluetoothDevice): Boolean =
        invoke(CONNECT_METHOD, device) as? Boolean ?: false

    fun disconnect(device: BluetoothDevice): Boolean =
        invoke(DISCONNECT_METHOD, device) as? Boolean ?: false

    /**
     * Devices the profile reports as connected. An empty list is a valid result on
     * builds that report connections lazily.
     */
    fun connectedDevices(): List<BluetoothDevice> = try {
        proxy.connectedDevices.orEmpty()
    } catch (securityException: SecurityException) {
        AppLog.w(TAG, "Reading connected HID devices needs BLUETOOTH_CONNECT", securityException)
        emptyList()
    }

    private fun hasMethod(name: String): Boolean = try {
        proxy.javaClass.getMethod(name, BluetoothDevice::class.java)
        true
    } catch (noSuchMethod: NoSuchMethodException) {
        AppLog.w(TAG, "HID host proxy has no $name method on this build", noSuchMethod)
        false
    }

    private fun invoke(name: String, device: BluetoothDevice): Any? = try {
        proxy.javaClass.getMethod(name, BluetoothDevice::class.java).invoke(proxy, device)
    } catch (noSuchMethod: NoSuchMethodException) {
        AppLog.w(TAG, "HID host proxy has no $name method on this build", noSuchMethod)
        null
    } catch (illegalAccess: IllegalAccessException) {
        AppLog.w(TAG, "HID host $name is not accessible on this build", illegalAccess)
        null
    } catch (invocation: java.lang.reflect.InvocationTargetException) {
        AppLog.w(TAG, "HID host $name failed", invocation.cause ?: invocation)
        null
    }

    private companion object {
        const val TAG = "HidHostProxyController"
        const val CONNECT_METHOD = "connect"
        const val DISCONNECT_METHOD = "disconnect"
    }
}
