package com.pulsepad.controller.presentation.ui.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.pulsepad.controller.domain.engine.ControllerSchematic
import com.pulsepad.controller.domain.engine.SchematicRegion
import com.pulsepad.controller.domain.model.ControllerButton
import com.pulsepad.controller.domain.model.ControllerFamily

/**
 * A 2D vector schematic of a controller, drawn with Compose [Canvas].
 *
 * This is the studio's fallback wherever the Filament renderer cannot run - most
 * importantly on older Fire tablets without OpenGL ES 3.0. It is not a degraded
 * dead end: regions highlight live and every control is tappable to open its
 * mapping, exactly as the 3D nodes are.
 */
@Composable
fun ControllerSchematicCanvas(
    family: ControllerFamily,
    pressedButtons: Set<ControllerButton>,
    selectedButton: ControllerButton?,
    onButtonSelected: (ControllerButton) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    val outlineColor = MaterialTheme.colorScheme.outline
    val bodyColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val selectedColor = MaterialTheme.colorScheme.tertiary

    val regions = ControllerSchematic.regions(family)
    val labels = regions.associate { region ->
        region.button to stringResource(ButtonLabels.labelRes(region.button, family))
    }
    val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor)

    Box(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .pointerInput(family) {
                    detectTapGestures { offset ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        ControllerSchematic.regionAt(
                            family = family,
                            normalisedX = offset.x / width,
                            normalisedY = offset.y / height
                        )?.let { onButtonSelected(it.button) }
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            drawBody(width, height, bodyColor, outlineColor)

            regions.forEach { region ->
                drawRegion(
                    region = region,
                    width = width,
                    height = height,
                    pressed = region.button in pressedButtons,
                    selected = region.button == selectedButton,
                    accent = accent,
                    selectedColor = selectedColor,
                    outlineColor = outlineColor,
                    textMeasurer = textMeasurer,
                    label = labels[region.button].orEmpty(),
                    labelStyle = labelStyle
                )
            }
        }
    }
}

private fun DrawScope.drawBody(
    width: Float,
    height: Float,
    bodyColor: Color,
    outlineColor: Color
) {
    val outline = Path()
    ControllerSchematic.BODY_OUTLINE.forEachIndexed { index, (x, y) ->
        if (index == 0) outline.moveTo(x * width, y * height) else outline.lineTo(x * width, y * height)
    }
    outline.close()
    drawPath(outline, color = bodyColor)
    drawPath(outline, color = outlineColor, style = Stroke(width = 2f))
}

private fun DrawScope.drawRegion(
    region: SchematicRegion,
    width: Float,
    height: Float,
    pressed: Boolean,
    selected: Boolean,
    accent: Color,
    selectedColor: Color,
    outlineColor: Color,
    textMeasurer: TextMeasurer,
    label: String,
    labelStyle: TextStyle
) {
    val center = Offset(region.centerX * width, region.centerY * height)
    // Regions are authored against the width so the pad keeps its proportions on
    // any screen, including the squarer aspect of a Fire tablet.
    val radius = region.radius * width

    val fill = when {
        pressed -> accent.copy(alpha = 0.85f)
        selected -> selectedColor.copy(alpha = 0.55f)
        else -> Color.Transparent
    }
    if (fill != Color.Transparent) {
        drawCircle(color = fill, radius = radius, center = center)
    }
    drawCircle(
        color = if (pressed || selected) accent else outlineColor,
        radius = radius,
        center = center,
        style = Stroke(width = if (pressed || selected) 3f else 1.5f)
    )

    if (label.isNotEmpty()) {
        val layout = textMeasurer.measure(label, labelStyle)
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(
                center.x - layout.size.width / 2f,
                center.y - layout.size.height / 2f
            )
        )
    }
}
