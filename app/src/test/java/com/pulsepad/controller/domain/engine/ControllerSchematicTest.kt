package com.pulsepad.controller.domain.engine

import com.pulsepad.controller.domain.model.ControllerButton
import com.pulsepad.controller.domain.model.ControllerFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerSchematicTest {

    @Test
    fun `every family exposes the full button set`() {
        val expected = ControllerButton.entries.toSet()
        ControllerFamily.entries.forEach { family ->
            val covered = ControllerSchematic.regions(family).map { it.button }.toSet()
            assertEquals(
                "family $family must expose every button so every control can be mapped",
                expected,
                covered
            )
        }
    }

    @Test
    fun `regions never overlap within a family`() {
        ControllerFamily.entries.forEach { family ->
            val regions = ControllerSchematic.regions(family)
            regions.forEachIndexed { index, first ->
                regions.drop(index + 1).forEach { second ->
                    val dx = first.centerX - second.centerX
                    val dy = first.centerY - second.centerY
                    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                    assertTrue(
                        "${first.button} and ${second.button} overlap in $family",
                        distance >= first.radius + second.radius - 0.0001f
                    )
                }
            }
        }
    }

    @Test
    fun `regions stay inside the viewport`() {
        ControllerFamily.entries.forEach { family ->
            ControllerSchematic.regions(family).forEach { region ->
                assertTrue("${region.button} left edge", region.centerX - region.radius >= 0f)
                assertTrue("${region.button} right edge", region.centerX + region.radius <= 1f)
                assertTrue("${region.button} top edge", region.centerY - region.radius >= 0f)
                assertTrue("${region.button} bottom edge", region.centerY + region.radius <= 1f)
            }
        }
    }

    @Test
    fun `hit testing finds the region at its centre`() {
        ControllerFamily.entries.forEach { family ->
            ControllerSchematic.regions(family).forEach { region ->
                val hit = ControllerSchematic.regionAt(family, region.centerX, region.centerY)
                assertEquals(region.button, hit?.button)
            }
        }
    }

    @Test
    fun `hit testing returns null in empty space`() {
        // The top-left corner is outside every region for every family.
        ControllerFamily.entries.forEach { family ->
            assertNull(ControllerSchematic.regionAt(family, 0.01f, 0.01f))
        }
    }

    @Test
    fun `xbox swaps the stick and d-pad corners relative to playstation`() {
        val xboxLeftStick = ControllerSchematic.regions(ControllerFamily.XBOX_WIRELESS)
            .first { it.button == ControllerButton.L3 }
        val playstationLeftStick = ControllerSchematic.regions(ControllerFamily.DUALSENSE)
            .first { it.button == ControllerButton.L3 }

        assertTrue(
            "the Xbox left stick sits higher than the PlayStation one",
            xboxLeftStick.centerY < playstationLeftStick.centerY
        )

        val xboxDpadUp = ControllerSchematic.regions(ControllerFamily.XBOX_WIRELESS)
            .first { it.button == ControllerButton.DPAD_UP }
        val playstationDpadUp = ControllerSchematic.regions(ControllerFamily.DUALSENSE)
            .first { it.button == ControllerButton.DPAD_UP }
        assertTrue(
            "the Xbox D-pad sits lower than the PlayStation one",
            xboxDpadUp.centerY > playstationDpadUp.centerY
        )
    }

    @Test
    fun `body outline is a closed polygon with sane coordinates`() {
        assertTrue(ControllerSchematic.BODY_OUTLINE.size >= 8)
        ControllerSchematic.BODY_OUTLINE.forEach { (x, y) ->
            assertTrue(x in 0f..1f)
            assertTrue(y in 0f..1f)
        }
        assertNotNull(ControllerSchematic.BODY_OUTLINE.firstOrNull())
    }
}
