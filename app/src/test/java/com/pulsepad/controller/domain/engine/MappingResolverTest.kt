package com.pulsepad.controller.domain.engine

import com.pulsepad.controller.domain.model.ControllerButton
import com.pulsepad.controller.domain.model.MacroStep
import com.pulsepad.controller.domain.model.MappedAction
import com.pulsepad.controller.domain.model.MappingConfiguration
import com.pulsepad.controller.domain.model.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappingResolverTest {

    private val at = Point(0.5f, 0.5f)

    @Test
    fun `an unmapped button produces no work`() {
        val plan = MappingResolver.onPress(ControllerButton.CROSS, MappingConfiguration())
        assertTrue(plan.isNoOp)
    }

    @Test
    fun `a disabled mapping produces no work`() {
        val config = MappingConfiguration().withMapping(ControllerButton.CROSS, MappedAction.Disabled)
        assertTrue(MappingResolver.onPress(ControllerButton.CROSS, config).isNoOp)
    }

    @Test
    fun `a tap mapping produces exactly one action`() {
        val config = MappingConfiguration().withMapping(ControllerButton.CROSS, MappedAction.Tap(at))
        val plan = MappingResolver.onPress(ControllerButton.CROSS, config)
        assertEquals(1, plan.actions.size)
        assertTrue(plan.actions.single() is MappedAction.Tap)
        assertNull(plan.repeatEveryMs)
    }

    @Test
    fun `a hold mapping carries its duration`() {
        val hold = MappedAction.Hold(at, durationMs = 750)
        val config = MappingConfiguration().withMapping(ControllerButton.R1, hold)
        val plan = MappingResolver.onPress(ControllerButton.R1, config)
        assertEquals(hold, plan.actions.single())
    }

    @Test
    fun `a turbo mapping schedules a first tap and a repeat interval`() {
        val turbo = MappedAction.Turbo(at, rateHz = 10)
        val config = MappingConfiguration().withMapping(ControllerButton.SQUARE, turbo)
        val plan = MappingResolver.onPress(ControllerButton.SQUARE, config)
        assertEquals(100L, plan.repeatEveryMs)
        assertEquals(1, plan.actions.size)
        assertTrue(plan.actions.single() is MappedAction.Tap)
        assertFalse(plan.isNoOp)
    }

    @Test
    fun `turbo is the only action that repeats`() {
        val turboConfig = MappingConfiguration()
            .withMapping(ControllerButton.SQUARE, MappedAction.Turbo(at, 12))
        val tapConfig = MappingConfiguration()
            .withMapping(ControllerButton.SQUARE, MappedAction.Tap(at))

        assertTrue(MappingResolver.stopsRepeatOnRelease(ControllerButton.SQUARE, turboConfig))
        assertFalse(MappingResolver.stopsRepeatOnRelease(ControllerButton.SQUARE, tapConfig))
        assertFalse(MappingResolver.stopsRepeatOnRelease(ControllerButton.CROSS, tapConfig))
    }

    @Test
    fun `a macro keeps its step order`() {
        val macro = MappedAction.Macro(
            listOf(
                MacroStep.Tap(Point(0.1f, 0.1f)),
                MacroStep.Delay(50),
                MacroStep.Tap(Point(0.9f, 0.9f), holdMs = 30)
            )
        )
        val config = MappingConfiguration().withMapping(ControllerButton.TRIANGLE, macro)
        val plan = MappingResolver.onPress(ControllerButton.TRIANGLE, config)
        assertEquals(macro, plan.actions.single())

        val taps = MappingResolver.macroTaps(macro)
        assertEquals(2, taps.size)
        assertEquals(Point(0.1f, 0.1f), taps.first().at)
        assertEquals(Point(0.9f, 0.9f), taps.last().at)
    }

    @Test
    fun `macro duration sums taps and delays`() {
        val macro = MappedAction.Macro(
            listOf(
                MacroStep.Tap(Point(0f, 0f), holdMs = 40),
                MacroStep.Delay(100),
                MacroStep.Tap(Point(1f, 1f), holdMs = 60)
            )
        )
        assertEquals(200L, MappingResolver.macroDurationMs(macro))
    }

    @Test
    fun `mapped buttons are listed in enum order and exclude disabled entries`() {
        val config = MappingConfiguration()
            .withMapping(ControllerButton.R1, MappedAction.Tap(at))
            .withMapping(ControllerButton.CROSS, MappedAction.Tap(at))
            .withMapping(ControllerButton.SHARE, MappedAction.Disabled)

        val mapped = MappingResolver.mappedButtons(config)
        assertEquals(
            listOf(ControllerButton.CROSS, ControllerButton.R1),
            mapped.map { it.source }
        )
        assertTrue(mapped.none { it.isDisabled })
    }

    @Test
    fun `mapping count ignores disabled entries`() {
        val config = MappingConfiguration()
            .withMapping(ControllerButton.CROSS, MappedAction.Tap(at))
            .withMapping(ControllerButton.CIRCLE, MappedAction.Disabled)
        assertEquals(1, config.mappedCount)
    }

    @Test
    fun `removing a mapping falls back to disabled`() {
        val config = MappingConfiguration()
            .withMapping(ControllerButton.CROSS, MappedAction.Tap(at))
            .withoutMapping(ControllerButton.CROSS)
        assertEquals(MappedAction.Disabled, config.actionFor(ControllerButton.CROSS))
    }
}
