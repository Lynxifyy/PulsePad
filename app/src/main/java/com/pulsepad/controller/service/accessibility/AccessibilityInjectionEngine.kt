package com.pulsepad.controller.service.accessibility

import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.util.DisplayMetrics
import android.view.WindowManager
import com.pulsepad.controller.domain.engine.GesturePlanner
import com.pulsepad.controller.domain.engine.InputInjectionEngine
import com.pulsepad.controller.domain.engine.LatencyExpectation
import com.pulsepad.controller.domain.engine.StrokeSpec
import com.pulsepad.controller.data.repository.MappingRepository
import com.pulsepad.controller.domain.model.MappedAction
import com.pulsepad.controller.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import com.pulsepad.controller.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * The accessibility implementation of the injection engine.
 *
 * `dispatchGesture` is the only non-root API that can drive another app, and it is
 * a *gesture* API, not an input-device API: the system animates a stroke along a
 * path. That has two consequences the rest of the app must be honest about. First,
 * the round trip costs tens of milliseconds, so this can never behave like a wired
 * controller. Second, a game that reads gamepad input directly will never see these
 * events, because no gamepad event is ever synthesised.
 *
 * The engine is a singleton because the service instance comes and goes with the
 * system; it looks the current service up through [AccessibilityServiceBridge] on
 * every dispatch rather than holding a stale reference.
 */
@Singleton
class AccessibilityInjectionEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val mappingRepository: MappingRepository
) : InputInjectionEngine {

    init {
        // The tuning screen writes the interval to the repository, which is the one
        // source of truth; the engine follows it rather than keeping its own copy
        // that could drift out of step.
        scope.launch {
            mappingRepository.injectionIntervalMs.collect { interval ->
                injectionIntervalMs = interval
            }
        }
    }

    @Volatile
    private var injectionIntervalMs: Int = LatencyExpectation.MIN_INTERVAL_MS

    private val repeatJobs = mutableMapOf<String, Job>()

    override suspend fun inject(action: MappedAction): Boolean {
        val service = AccessibilityServiceBridge.currentService() ?: return false
        val metrics = displayMetrics()
        val strokes = GesturePlanner.plan(
            action = action,
            screenWidthPx = metrics.widthPixels,
            screenHeightPx = metrics.heightPixels
        )
        if (strokes.isEmpty()) return false
        return dispatchStrokes(service, strokes)
    }

    override fun isAvailable(): Boolean {
        val service = AccessibilityServiceBridge.currentService() ?: return false
        return service.canDispatchGestures()
    }

    override val estimatedLatencyMs: Int
        get() = LatencyExpectation.estimateForInterval(injectionIntervalMs)

    /**
     * Starts a turbo loop for [key], tapping at [intervalMs] until [stopRepeat].
     *
     * Turbo is the only action that repeats, so it is modelled as a named job that
     * the matching button release cancels. Anything left running is cancelled by
     * [cancelAll] when the engine is disarmed.
     */
    fun startRepeat(key: String, action: MappedAction.Tap, intervalMs: Long) {
        stopRepeat(key)
        repeatJobs[key] = scope.launch {
            while (isActive) {
                if (!inject(action)) break
                delay(intervalMs)
            }
        }
    }

    fun stopRepeat(key: String) {
        repeatJobs.remove(key)?.cancel()
    }

    override fun cancelAll() {
        repeatJobs.values.forEach { it.cancel() }
        repeatJobs.clear()
    }

    private suspend fun dispatchStrokes(
        service: PulsePadAccessibilityService,
        strokes: List<StrokeSpec>
    ): Boolean {
        val builder = GestureDescription.Builder()
        var strokeCount = 0
        strokes.forEach { stroke ->
            if (stroke.path.size < 2 && stroke.path.size != 1) return@forEach
            val path = Path().apply {
                val first = stroke.path.first()
                moveTo(first.x, first.y)
                stroke.path.drop(1).forEach { point -> lineTo(point.x, point.y) }
                // A single-point path is a tap, but the framework rejects a path
                // with no line segment, so give it a zero-length one.
                if (stroke.path.size == 1) lineTo(first.x, first.y)
            }
            val clampedStart = stroke.startTimeMs.coerceAtLeast(0L)
            val clampedDuration = stroke.durationMs.coerceAtLeast(GesturePlanner.MIN_STROKE_MS)
            builder.addStroke(GestureDescription.StrokeDescription(path, clampedStart, clampedDuration))
            strokeCount++
        }
        if (strokeCount == 0) return false

        return suspendCancellableCoroutine { continuation ->
            val accepted = service.performGesture(builder.build())
            if (continuation.isActive) continuation.resume(accepted)
        }
    }

    private fun displayMetrics(): DisplayMetrics {
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        manager?.defaultDisplay?.getRealMetrics(metrics)
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) {
            // Fall back to the resource metrics if the window manager is unavailable,
            // which happens on some locked-down builds.
            val fallback = context.resources.displayMetrics
            metrics.widthPixels = fallback.widthPixels
            metrics.heightPixels = fallback.heightPixels
        }
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) {
            AppLog.w(TAG, "Display metrics unavailable; defaulting to 1080x1920")
            metrics.widthPixels = 1080
            metrics.heightPixels = 1920
        }
        return metrics
    }

    private companion object {
        const val TAG = "InjectionEngine"
    }
}
