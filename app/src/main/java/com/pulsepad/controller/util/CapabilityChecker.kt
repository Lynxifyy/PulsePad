package com.pulsepad.controller.util

import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.pulsepad.controller.domain.model.DeviceCapabilities
import com.pulsepad.controller.service.accessibility.PulsePadAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Probes the runtime capabilities that decide how PulsePad behaves on a given
 * device. Every probe fails closed and reports *why* rather than throwing, so the
 * app can show an explanation on Fire OS builds that restrict a feature.
 */
class CapabilityChecker(private val context: Context) {

    suspend fun checkAll(): DeviceCapabilities = withContext(Dispatchers.Default) {
        DeviceCapabilities(
            accessibilityServiceEnabled = isAccessibilityServiceEnabled(),
            openGlEs30Available = isOpenGlEs30Available(),
            bluetoothHidHostAvailable = isBluetoothHidHostSupported(),
            playServicesPresent = isPlayServicesPresent(),
            androidSdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            deviceModel = Build.MODEL.orEmpty()
        )
    }

    /**
     * An accessibility service counts as enabled only when it appears in the
     * user's trusted-services list. Checking `Settings.Secure` is deliberately
     * avoided: the value is unreliable on Fire OS.
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(context, PulsePadAccessibilityService::class.java)
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as? android.view.accessibility.AccessibilityManager ?: return false
        return manager.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any { info ->
            val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
            ComponentName(serviceInfo.packageName, serviceInfo.name) == expected
        }
    }

    /**
     * Filament, which backs SceneView, needs OpenGL ES 3.0. The value is a packed
     * `major << 16 | minor` integer, so 0x30000 means 3.0.
     */
    fun isOpenGlEs30Available(): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        val configurationInfo = manager.deviceConfigurationInfo ?: return false
        return configurationInfo.reqGlEsVersion >= 0x00030000
    }

    /**
     * `BluetoothHidHost` is the profile PulsePad prefers for connecting
     * controllers directly. Some Fire OS builds do not expose it, in which case
     * the app falls back to bonded-device enumeration plus `InputDevice` capture.
     */
    fun isBluetoothHidHostSupported(): Boolean {
        val adapter = bluetoothAdapterOrNull() ?: return false
        var failure: Throwable? = null
        val proxyObtainable = try {
            adapter.getProfileProxy(
                context,
                NoOpProfileListener,
                BluetoothSupport.hidHostProfile
            )
        } catch (securityException: SecurityException) {
            AppLog.w(TAG, "Missing Bluetooth permission while probing HID host", securityException)
            failure = securityException
            false
        } catch (illegalArgumentException: IllegalArgumentException) {
            AppLog.w(TAG, "HID host profile unsupported on this build", illegalArgumentException)
            failure = IllegalArgumentException("unsupported")
            false
        }
        return BluetoothSupport.decideHidHostAvailability(proxyObtainable, failure)
    }

    /** Informational only: PulsePad has no hard dependency on Google services. */
    fun isPlayServicesPresent(): Boolean = try {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(GOOGLE_PLAY_SERVICES_PACKAGE, 0)
        info != null
    } catch (notFound: PackageManager.NameNotFoundException) {
        AppLog.d(TAG, "Google Play Services not installed; continuing without it")
        false
    }

    private fun bluetoothAdapterOrNull(): BluetoothAdapter? = try {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        manager?.adapter
    } catch (securityException: SecurityException) {
        AppLog.w(TAG, "Missing Bluetooth permission while reading the adapter", securityException)
        null
    }

    private object NoOpProfileListener : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            // The probe only needs to know whether a proxy can be obtained.
        }

        override fun onServiceDisconnected(profile: Int) = Unit
    }

    companion object {
        private const val TAG = "CapabilityChecker"
        private const val GOOGLE_PLAY_SERVICES_PACKAGE = "com.google.android.gms"
    }
}
