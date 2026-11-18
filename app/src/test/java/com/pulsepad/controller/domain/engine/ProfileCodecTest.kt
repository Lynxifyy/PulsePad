package com.pulsepad.controller.domain.engine

import com.pulsepad.controller.domain.model.ControllerButton
import com.pulsepad.controller.domain.model.CurveType
import com.pulsepad.controller.domain.model.MappedAction
import com.pulsepad.controller.domain.model.MappingConfiguration
import com.pulsepad.controller.domain.model.Point
import com.pulsepad.controller.domain.model.StickConfig
import com.pulsepad.controller.domain.model.TriggerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCodecTest {

    private val configuration = MappingConfiguration(
        buttonMappings = mapOf(
            ControllerButton.CROSS to MappedAction.Tap(Point(0.25f, 0.75f)),
            ControllerButton.R1 to MappedAction.Turbo(Point(0.9f, 0.5f), rateHz = 18),
            ControllerButton.L2 to MappedAction.Hold(Point(0.1f, 0.5f), durationMs = 750),
            ControllerButton.OPTIONS to MappedAction.Swipe(
                path = listOf(Point(0.1f, 0.9f), Point(0.9f, 0.1f)),
                durationMs = 300
            )
        ),
        leftStick = StickConfig(
            deadzone = 0.2f,
            curve = CurveType.EXPONENTIAL,
            exponent = 3.0f,
            sensitivity = 1.4f
        ),
        rightStick = StickConfig(curve = CurveType.S_CURVE),
        trigger = TriggerConfig(activationThreshold = 0.5f, maxValue = 0.85f)
    )

    @Test
    fun `a configuration round-trips unchanged`() {
        val decoded = ProfileCodec.decode(ProfileCodec.encode(configuration))
        assertEquals(configuration, decoded)
    }

    @Test
    fun `every action type survives a round trip`() {
        val actions = listOf(
            MappedAction.Disabled,
            MappedAction.Tap(Point(0.5f, 0.5f)),
            MappedAction.Hold(Point(0.5f, 0.5f), 120),
            MappedAction.Swipe(listOf(Point(0f, 0f), Point(1f, 1f)), 200),
            MappedAction.Macro(
                listOf(
                    com.pulsepad.controller.domain.model.MacroStep.Tap(Point(0.2f, 0.2f), 40),
                    com.pulsepad.controller.domain.model.MacroStep.Delay(90)
                )
            ),
            MappedAction.Turbo(Point(0.3f, 0.3f), 25)
        )
        actions.forEach { action ->
            val encoded = ProfileCodec.encode(
                MappingConfiguration(buttonMappings = mapOf(ControllerButton.CIRCLE to action))
            )
            assertEquals(
                action,
                ProfileCodec.decode(encoded)?.actionFor(ControllerButton.CIRCLE)
            )
        }
    }

    @Test
    fun `malformed JSON decodes to null instead of throwing`() {
        assertNull(ProfileCodec.decode("not json at all"))
        assertNull(ProfileCodec.decode(""))
        assertNull(ProfileCodec.decode("{\"buttonMappings\":\"wrong type\"}"))
    }

    @Test
    fun `an empty object decodes to the default configuration`() {
        // Every field has a default, so "{}" is a valid - if empty - profile. The
        // import dialog gates on `looksLikeProfile` to avoid offering to import
        // arbitrary JSON that happens to parse.
        assertEquals(MappingConfiguration(), ProfileCodec.decode("{}"))
    }

    @Test
    fun `unknown fields are ignored so a newer profile still loads`() {
        val encoded = ProfileCodec.encode(configuration)
            .trimEnd()
            .removeSuffix("}")
        val withExtraField = "$encoded,\"futureSetting\":true}"
        val decoded = ProfileCodec.decode(withExtraField)
        assertEquals(configuration, decoded)
    }

    @Test
    fun `profile detection accepts an exported profile`() {
        assertTrue(ProfileCodec.looksLikeProfile(ProfileCodec.encode(configuration)))
    }

    @Test
    fun `profile detection rejects unrelated JSON`() {
        assertFalse(ProfileCodec.looksLikeProfile("{\"name\":\"something else\"}"))
        assertFalse(ProfileCodec.looksLikeProfile("[]"))
        assertFalse(ProfileCodec.looksLikeProfile(""))
    }
}
