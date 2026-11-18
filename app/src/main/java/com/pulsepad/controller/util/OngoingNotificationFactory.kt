package com.pulsepad.controller.util

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.pulsepad.controller.R
import com.pulsepad.controller.presentation.MainActivity

/**
 * Builds the two ongoing notices PulsePad shows.
 *
 * Both are deliberately low-importance and un-dismissable only for as long as the
 * underlying work is running: the capture notice reflects a connected controller,
 * and the injection notice reflects an *armed* engine. Showing the injection
 * notice is the user's visible guarantee that gesture dispatch is active, so it is
 * never suppressed.
 */
object OngoingNotificationFactory {

    fun buildCaptureNotice(context: Context, controllerName: String? = null): Notification {
        val text = controllerName
            ?.let { context.getString(R.string.notification_capture_connected, it) }
            ?: context.getString(R.string.notification_capture_idle)
        return baseNotification(
            context = context,
            channelId = NotificationChannels.CAPTURE_CHANNEL_ID,
            title = context.getString(R.string.notification_capture_title),
            text = text
        )
    }

    fun buildInjectionNotice(context: Context, targetAppName: String? = null): Notification {
        val text = targetAppName
            ?.let { context.getString(R.string.notification_injection_active, it) }
            ?: context.getString(R.string.notification_injection_active_unknown)
        return baseNotification(
            context = context,
            channelId = NotificationChannels.INJECTION_CHANNEL_ID,
            title = context.getString(R.string.notification_injection_title),
            text = text
        )
    }

    private fun baseNotification(
        context: Context,
        channelId: String,
        title: String,
        text: String
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_pulsepad)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
