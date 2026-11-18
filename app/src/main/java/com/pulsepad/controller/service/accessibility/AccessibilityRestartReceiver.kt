package com.pulsepad.controller.service.accessibility

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pulsepad.controller.util.AppLog

/**
 * Restart hook for the accessibility service.
 *
 * Android does not let an app enable its own accessibility service, and the OS may
 * kill the service under memory pressure. This receiver lets a notification action
 * bring the user back to the accessibility settings screen so they can re-enable
 * it. It deliberately does **not** attempt to enable the service itself.
 */
class AccessibilityRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RESTART_ACCESSIBILITY) return
        AppLog.i(TAG, "User requested accessibility re-enable from notification")
        AccessibilitySettingsNavigator.openSettings(context)
    }

    companion object {
        private const val TAG = "AccessibilityRestart"
        const val ACTION_RESTART_ACCESSIBILITY =
            "com.pulsepad.controller.action.RESTART_ACCESSIBILITY"
    }
}
