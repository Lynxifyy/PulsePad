package com.pulsepad.controller.domain.engine

import com.pulsepad.controller.domain.model.ControllerButton
import com.pulsepad.controller.domain.model.ControllerFamily

/**
 * Button positions for the 2D schematic, in normalised 0..1 viewport space with
 * the origin at the top left.
 *
 * The schematic is the fallback the studio shows when the device cannot run the
 * 3D renderer, and it is also what the mapping editor overlays when there is no
 * GLES 3.0. Keeping the geometry here - free of Compose and Android types - means
 * the layout can be unit tested and reused by the canvas and by hit testing.
 */
data class SchematicRegion(
    val button: ControllerButton,
    val centerX: Float,
    val centerY: Float,
    val radius: Float
)

object ControllerSchematic {

    /** The controller body outline, as normalised points. */
    val BODY_OUTLINE: List<Pair<Float, Float>> = listOf(
        0.06f to 0.34f,
        0.18f to 0.28f,
        0.50f to 0.27f,
        0.82f to 0.28f,
        0.94f to 0.34f,
        0.96f to 0.52f,
        0.88f to 0.72f,
        0.78f to 0.78f,
        0.66f to 0.62f,
        0.50f to 0.58f,
        0.34f to 0.62f,
        0.22f to 0.78f,
        0.12f to 0.72f,
        0.04f to 0.52f
    )

    private const val FACE = 0.026f
    private const val FACE_OFFSET = 0.038f
    private const val SHOULDER = 0.034f
    private const val STICK = 0.040f
    private const val MENU = 0.022f
    private const val DPAD = 0.030f
    private const val DPAD_OFFSET = 0.052f
    private const val CENTRE = 0.024f

    /**
     * Regions for a family.
     *
     * The PlayStation pads put both sticks low and the D-pad high-left; the Xbox
     * pad swaps the left stick and the D-pad. The schematic mirrors that so the
     * layout matches the physical pad the user is holding.
     */
    fun regions(family: ControllerFamily): List<SchematicRegion> {
        val shouldersAndMenu = listOf(
            SchematicRegion(ControllerButton.L2, 0.16f, 0.22f, SHOULDER),
            SchematicRegion(ControllerButton.R2, 0.84f, 0.22f, SHOULDER),
            SchematicRegion(ControllerButton.L1, 0.24f, 0.31f, SHOULDER),
            SchematicRegion(ControllerButton.R1, 0.76f, 0.31f, SHOULDER),
            SchematicRegion(ControllerButton.SHARE, 0.43f, 0.36f, MENU),
            SchematicRegion(ControllerButton.OPTIONS, 0.57f, 0.36f, MENU),
            SchematicRegion(ControllerButton.GUIDE, 0.50f, 0.45f, CENTRE)
        )

        val isXbox = family == ControllerFamily.XBOX_WIRELESS
        val faceButtons = faceButtons(centerX = 0.80f, centerY = if (isXbox) 0.58f else 0.62f)

        val sticksAndDpad = when (family) {
            ControllerFamily.XBOX_WIRELESS -> listOf(
                SchematicRegion(ControllerButton.L3, 0.30f, 0.44f, STICK),
                SchematicRegion(ControllerButton.R3, 0.70f, 0.62f, STICK)
            ) + dpad(centerX = 0.30f, centerY = 0.60f)

            else -> listOf(
                SchematicRegion(ControllerButton.L3, 0.30f, 0.60f, STICK),
                SchematicRegion(ControllerButton.R3, 0.66f, 0.60f, STICK)
            ) + dpad(centerX = 0.30f, centerY = 0.44f)
        }

        return shouldersAndMenu + sticksAndDpad + faceButtons
    }

    private fun faceButtons(centerX: Float, centerY: Float): List<SchematicRegion> = listOf(
        SchematicRegion(ControllerButton.TRIANGLE, centerX, centerY - FACE_OFFSET, FACE),
        SchematicRegion(ControllerButton.CIRCLE, centerX + FACE_OFFSET, centerY, FACE),
        SchematicRegion(ControllerButton.CROSS, centerX, centerY + FACE_OFFSET, FACE),
        SchematicRegion(ControllerButton.SQUARE, centerX - FACE_OFFSET, centerY, FACE)
    )

    private fun dpad(centerX: Float, centerY: Float): List<SchematicRegion> = listOf(
        SchematicRegion(ControllerButton.DPAD_UP, centerX, centerY - DPAD_OFFSET, DPAD),
        SchematicRegion(ControllerButton.DPAD_DOWN, centerX, centerY + DPAD_OFFSET, DPAD),
        SchematicRegion(ControllerButton.DPAD_LEFT, centerX - DPAD_OFFSET, centerY, DPAD),
        SchematicRegion(ControllerButton.DPAD_RIGHT, centerX + DPAD_OFFSET, centerY, DPAD)
    )

    /** Region under a normalised point, if any. Used for tap-to-map. */
    fun regionAt(
        family: ControllerFamily,
        normalisedX: Float,
        normalisedY: Float
    ): SchematicRegion? = regions(family).firstOrNull { region ->
        val dx = region.centerX - normalisedX
        val dy = region.centerY - normalisedY
        dx * dx + dy * dy <= region.radius * region.radius
    }
}
