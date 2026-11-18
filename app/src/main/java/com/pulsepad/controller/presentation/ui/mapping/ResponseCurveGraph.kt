package com.pulsepad.controller.presentation.ui.mapping

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pulsepad.controller.R
import com.pulsepad.controller.domain.engine.InputTransformEngine
import com.pulsepad.controller.domain.model.StickConfig

/**
 * A live graph of the stick response curve.
 *
 * The reference diagonal shows what a raw, unmodified response would look like, so
 * the effect of the deadzone and the curve is visible at a glance: the shaded
 * region at the left is the deadzone, and the curve's shape is the response.
 */
@Composable
fun ResponseCurveGraph(
    config: StickConfig,
    modifier: Modifier = Modifier
) {
    val curveColor = MaterialTheme.colorScheme.primary
    val deadzoneColor = MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
    val referenceColor = MaterialTheme.colorScheme.outline
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val description = stringResource(R.string.stick_graph_description)

    val samples = InputTransformEngine.curveSamples(config, samples = 48)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .semantics { contentDescription = description }
    ) {
        val padding = 12f
        val graphWidth = size.width - padding * 2
        val graphHeight = size.height - padding * 2

        fun toScreen(input: Float, output: Float): Offset = Offset(
            x = padding + input * graphWidth,
            y = padding + (1f - output) * graphHeight
        )

        // Reference diagonal: output equals input.
        drawLine(
            color = referenceColor,
            start = toScreen(0f, 0f),
            end = toScreen(1f, 1f),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
        )

        // Deadzone band.
        if (config.deadzone > 0f) {
            val bandWidth = config.deadzone * graphWidth
            drawRect(
                color = deadzoneColor,
                topLeft = Offset(padding, padding),
                size = androidx.compose.ui.geometry.Size(bandWidth, graphHeight)
            )
        }

        val path = Path()
        samples.forEachIndexed { index, (input, output) ->
            val offset = toScreen(input, output)
            if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
        }
        drawPath(path, color = curveColor, style = Stroke(width = 4f))

        // Axes last, so they sit above the fill.
        drawRect(
            color = gridColor,
            topLeft = Offset(padding, padding),
            size = androidx.compose.ui.geometry.Size(graphWidth, graphHeight),
            style = Stroke(width = 2f)
        )
    }
}
