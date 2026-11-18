package com.pulsepad.controller.service.input

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import com.pulsepad.controller.util.AppLog
import com.pulsepad.controller.util.NotificationChannels
import com.pulsepad.controller.util.OngoingNotificationFactory

/**
 * Foreground service that keeps controller input flowing while PulsePad is in the
 * background.
 *
 * The accessibility service already receives button events, but two things depend
 * on this service being alive:
 *
 * 1. **Connection watching** - `InputManager.InputDeviceListener` callbacks are
 *    delivered to a registered listener, so something in the process must stay
 *    registered to notice a controller being unplugged.
 * 2. **Foreground priority** - a foreground service with an ongoing notice is far
 *    less likely to be killed while the user is in another app, which is exactly
 *    when input capture matters.
 *
 * It is deliberately *not* the injection path: injection belongs to the armed
 * engine in [com.pulsepad.controller.domain.engine], and this service only
 * observes.
 *
 * Note on analog axes: `AccessibilityService.onMotionEvent` (API 34+) is the
 * only way to receive stick and trigger values without a visible overlay. On
 * Android 9-13 there is no public API that delivers them to a background
 * component, so analog remapping is limited to API 34+ devices. Buttons work
 * everywhere.
 */
class InputCaptureService : Service() {

    private val binder = LocalBinder()
    private var watcher: InputDeviceWatcher? = null

    inner class LocalBinder : Binder() {
        fun service(): InputCaptureService = this@InputCaptureService
    }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureChannels(this)
        watcher = InputDeviceWatcher(this).also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NotificationChannels.CAPTURE_NOTIFICATION_ID,
            OngoingNotificationFactory.buildCaptureNotice(this)
        )
        watcher?.refresh()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        watcher?.stop()
        watcher = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    /** Ids of controllers the input system currently reports. */
    fun connectedControllerIds(): List<Int> = watcher?.controllerIds?.value.orEmpty()

    companion object {
        private const val TAG = "InputCaptureService"

        fun start(context: android.content.Context) {
            val intent = Intent(context, InputCaptureService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (illegalState: IllegalStateException) {
                AppLog.w(TAG, "Foreground capture service could not be started", illegalState)
            }
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, InputCaptureService::class.java))
        }
    }
}
