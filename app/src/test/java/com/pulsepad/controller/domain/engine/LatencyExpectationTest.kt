package com.pulsepad.controller.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatencyExpectationTest {

    @Test
    fun `the shortest interval gives the best estimate`() {
        assertEquals(
            LatencyExpectation.BEST_MS,
            LatencyExpectation.estimateForInterval(LatencyExpectation.MIN_INTERVAL_MS)
        )
    }

    @Test
    fun `the longest interval gives the worst estimate`() {
        assertEquals(
            LatencyExpectation.WORST_MS,
            LatencyExpectation.estimateForInterval(LatencyExpectation.MAX_INTERVAL_MS)
        )
    }

    @Test
    fun `out-of-range intervals clamp into the band`() {
        assertEquals(
            LatencyExpectation.BEST_MS,
            LatencyExpectation.estimateForInterval(0)
        )
        assertEquals(
            LatencyExpectation.WORST_MS,
            LatencyExpectation.estimateForInterval(10_000)
        )
    }

    @Test
    fun `the estimate stays inside the published band`() {
        (0..80).forEach { interval ->
            val estimate = LatencyExpectation.estimateForInterval(interval)
            assertTrue(
                "estimate $estimate for $interval ms",
                estimate in LatencyExpectation.BEST_MS..LatencyExpectation.WORST_MS
            )
        }
    }

    @Test
    fun `a longer interval never estimates faster than a shorter one`() {
        val estimates = (LatencyExpectation.MIN_INTERVAL_MS..LatencyExpectation.MAX_INTERVAL_MS)
            .map { LatencyExpectation.estimateForInterval(it) }
        estimates.zipWithNext { earlier, later ->
            assertTrue("$earlier should not exceed $later", earlier <= later)
        }
    }
}
