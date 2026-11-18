package com.pulsepad.controller.domain.engine

import com.pulsepad.controller.domain.model.MappedAction

/**
 * The boundary between the mapping logic and the only injection mechanism
 * Android offers without root: accessibility gesture dispatch.
 *
 * There is exactly one implementation today, but the interface exists so the
 * mapping pipeline can be tested with a recording fake, and so a future engine
 * (for example a companion-mode HID path) can slot in without touching callers.
 */
interface InputInjectionEngine {

    /** Replays [action] as a gesture. Returns false when the system refused it. */
    suspend fun inject(action: MappedAction): Boolean

    /** Whether gesture dispatch can run right now. */
    fun isAvailable(): Boolean

    /**
     * The honest end-to-end cost of a mapped action, in milliseconds.
     *
     * This is not a virtual gamepad: `dispatchGesture` describes a stroke and lets
     * the system play it out, so the round trip is tens of milliseconds at best and
     * varies with device and Android version. Never present it as low latency.
     */
    val estimatedLatencyMs: Int

    /** Stops any repeating work, such as a turbo loop, immediately. */
    fun cancelAll()
}

/** Reports the realistic latency band for the accessibility engine. */
object LatencyExpectation {

    /** Typical floor on a fast, recent device with the shortest interval. */
    const val BEST_MS = 50

    /** Typical ceiling on a slow device, or under load, with the longest interval. */
    const val WORST_MS = 120

    /** The value shown before a real measurement is available. */
    const val TYPICAL_MS = 80

    /**
     * Estimates latency from the configured injection interval.
     *
     * A longer interval batches events and adds up to one interval of queueing, so
     * the estimate moves across the band as the interval changes. The result is an
     * estimate, and the UI says so.
     */
    fun estimateForInterval(intervalMs: Int): Int {
        val clamped = intervalMs.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
        val span = (MAX_INTERVAL_MS - MIN_INTERVAL_MS).toFloat()
        val position = (clamped - MIN_INTERVAL_MS) / span
        return (BEST_MS + position * (WORST_MS - BEST_MS)).toInt()
    }

    const val MIN_INTERVAL_MS = 16
    const val MAX_INTERVAL_MS = 50
}
