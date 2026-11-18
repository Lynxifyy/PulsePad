package com.pulsepad.controller.service.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.pulsepad.controller.util.AppLog

/**
 * Sends the user to the accessibility service list.
 *
 * PulsePad cannot enable its own accessibility service: only the user can, from
 * system settings. This helper tries the most specific intent first and falls
 * back to the generic settings screen, because Fire OS and heavily customised
 * Android builds sometimes reject the documented deep links.
 */
object AccessibilitySettingsNavigator {

    private const val TAG = "AccessibilitySettings"

    fun openSettings(context: Context) {
        val candidates = listOf(
            // The service-specific page. Not documented as public, but widely
            // supported and the most direct route when it works.
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                putExtra(EXTRA_SERVICE_COMPONENT, componentName(context))
            },
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )

        for (intent in candidates) {
            if (launchSafely(context, intent)) return
        }
        AppLog.w(TAG, "No accessibility settings screen could be opened on this device")
    }

    private fun componentName(context: Context): String =
        ComponentName(context, PulsePadAccessibilityService::class.java).flattenToString()

    private fun launchSafely(context: Context, intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (activityNotFound: android.content.ActivityNotFoundException) {
        AppLog.d(TAG, "Settings screen not available: ${intent.action}")
        false
    } catch (securityException: SecurityException) {
        AppLog.w(TAG, "Settings screen blocked: ${intent.action}", securityException)
        false
    }

    /** Key used by the Android settings app to highlight a specific service entry. */
    private const val EXTRA_SERVICE_COMPONENT = ":settings:show_fragment_args"
}
