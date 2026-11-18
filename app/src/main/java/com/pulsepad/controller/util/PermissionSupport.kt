package com.pulsepad.controller.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Bluetooth permission requirements changed in API 31. Below that, the legacy
 * install-time permissions apply; from API 31 the runtime `BLUETOOTH_CONNECT`
 * and `BLUETOOTH_SCAN` permissions are needed instead.
 *
 * Kept as a small helper so every call site agrees on the same set.
 */
object PermissionSupport {

    val requiredBluetoothPermissions: List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
    }

    /** `POST_NOTIFICATIONS` is runtime-only from API 33 and is needed for the active notice. */
    val notificationPermission: String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else {
            null
        }

    fun missingBluetoothPermissions(context: Context): List<String> =
        requiredBluetoothPermissions.filterNot { isGranted(context, it) }

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasBluetoothPermissions(context: Context): Boolean =
        missingBluetoothPermissions(context).isEmpty()
}
