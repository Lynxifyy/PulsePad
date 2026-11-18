package com.pulsepad.controller.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.os.Build
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import com.pulsepad.controller.util.AppLog

/**
 * The only root-free, ADB-free mechanism Android offers for system-wide input
 * translation.
 *
 * The service captures controller input with `onKeyEvent` and (on API 34+)
 * `onMotionEvent`, and replays mapped actions with [dispatchGesture]. It is
 * deliberately passive: it holds no gesture state and injects nothing until the
 * injection engine in [com.pulsepad.controller.domain.engine] has been armed by
 * the user, because `dispatchGesture` can drive any app in the foreground.
 */
class PulsePadAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            // Filter key events so controller buttons reach onKeyEvent instead of
            // being consumed by the foreground app.
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            eventTypes = AccessibilityServiceInfo.DEFAULT or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = GESTURE_SCHEDULING_TIMEOUT_MS
        }
        AccessibilityServiceBridge.onServiceConnected(this)
        AppLog.i(TAG, "Accessibility service connected; gesture dispatch available")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        AccessibilityServiceBridge.onServiceDisconnected()
        AppLog.i(TAG, "Accessibility service unbound")
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
        AccessibilityServiceBridge.onServiceInterrupted()
        AppLog.w(TAG, "Accessibility service interrupted by the system")
    }

    override fun onDestroy() {
        AccessibilityServiceBridge.onServiceDisconnected()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    /**
     * Controller buttons arrive here because the service requests filtered key
     * events. Returning `true` consumes the event so the foreground app does not
     * also act on it; unmapped or disabled buttons return `false` so normal
     * behaviour is preserved.
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val input = ControllerKeyTranslator.translate(event) ?: return false
        return ControllerInputRelay.dispatch(input, event)
    }

    /**
     * Analog stick and trigger movement. `onMotionEvent` exists only on API 34+;
     * older devices rely on the foreground capture pipeline in
     * [com.pulsepad.controller.service.input.InputCaptureService].
     */
    override fun onMotionEvent(event: MotionEvent) {
        for (input in ControllerMotionTranslator.translate(event)) {
            ControllerInputRelay.dispatch(input, event)
        }
    }

    /** Replays a tap, hold, swipe or macro. Returns false when dispatch is rejected. */
    fun performGesture(description: GestureDescription): Boolean = try {
        dispatchGesture(description, null, null)
    } catch (securityException: SecurityException) {
        AppLog.w(TAG, "Gesture dispatch rejected by the system", securityException)
        false
    }

    /** Exposed for diagnostics: the framework reports whether gestures are permitted. */
    fun canDispatchGestures(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            serviceInfo?.let { info ->
                info.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES != 0
            } ?: false
        } else {
            false
        }

    companion object {
        private const val TAG = "AccessibilityService"
        const val GESTURE_SCHEDULING_TIMEOUT_MS = 16L
    }
}
