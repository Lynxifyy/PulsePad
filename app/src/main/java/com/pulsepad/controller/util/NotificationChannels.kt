package com.pulsepad.controller.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

/**
 * Notification channels for PulsePad's two ongoing notices.
 *
 * Channels only exist from API 26, and PulsePad's minSdk is 28, so creation is
 * unconditional; the `Build` check is kept because the channel id is also used as
 * a plain notification id on every version.
 */
object NotificationChannels {

    /** Ongoing notice shown while a controller is connected and being captured. */
    const val CAPTURE_CHANNEL_ID = "pulsepad_capture"

    /** Ongoing notice shown while the injection engine is armed. */
    const val INJECTION_CHANNEL_ID = "pulsepad_injection"

    const val CAPTURE_NOTIFICATION_ID = 4001
    const val INJECTION_NOTIFICATION_ID = 4002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        val captureChannel = NotificationChannel(
            CAPTURE_CHANNEL_ID,
            context.getString(com.pulsepad.controller.R.string.notification_channel_capture_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(
                com.pulsepad.controller.R.string.notification_channel_capture_description
            )
            setShowBadge(false)
        }
        val injectionChannel = NotificationChannel(
            INJECTION_CHANNEL_ID,
            context.getString(com.pulsepad.controller.R.string.notification_channel_injection_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(
                com.pulsepad.controller.R.string.notification_channel_injection_description
            )
            setShowBadge(false)
        }
        manager.createNotificationChannels(listOf(captureChannel, injectionChannel))
    }
}
