package com.pulsepad.controller.presentation.ui.mapping

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pulsepad.controller.domain.model.Point

/**
 * The "where gestures land" pad.
 *
 * It shows a normalised coordinate space with a draggable marker, plus any swipe
 * path or macro points overlaid on it. Coordinates are relative, not pixels, so a
 * profile created on a phone targets the same part of the screen on a tablet.
 *
 * Taps and drags both report the same normalised point, so a caller can treat the
 * pad as one input rather than distinguishing the gestures itself.
 */
@Composable
fun TargetPad(
    marker: Point,
    onMarkerMoved: (Point) -> Unit,
    modifier: Modifier = Modifier,
    swipePath: List<Point> = emptyList(),
    macroPoints: List<Point> = emptyList(),
    contentDescription: String? = null,
    onPadTapped: ((Point) -> Unit)? = null
) {
    val background = MaterialTheme.colorScheme.surfaceContainerHighest
    val grid = MaterialTheme.colorScheme.outlineVariant
    val markerColor = MaterialTheme.colorScheme.primary
    val pathColor = MaterialTheme.colorScheme.tertiary
    val macroColor = MaterialTheme.colorScheme.secondary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics { contentDescription?.let { this.contentDescription = it } }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val point = offset.toNormalised(size.width, size.height)
                    onPadTapped?.invoke(point) ?: onMarkerMoved(point)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onMarkerMoved(change.position.toNormalised(size.width, size.height))
                }
            }
    ) {
        drawRect(color = background)
        drawGrid(grid)
        if (swipePath.size >= 2) drawPath(swipePath, pathColor)
        macroPoints.forEach { point -> drawDot(point, macroColor, radius = 5f) }
        drawMarker(marker, markerColor)
    }
}

private fun Offset.toNormalised(width: Int, height: Int): Point = Point(
    x = (x / width.coerceAtLeast(1)).coerceIn(0f, 1f),
    y = (y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
)

private fun DrawScope.drawGrid(color: Color) {
    val step = size.width / 4f
    (1..3).forEach { index ->
        val position = step * index
        drawLine(color, Offset(position, 0f), Offset(position, size.height), strokeWidth = 1f)
        drawLine(color, Offset(0f, position), Offset(size.width, position), strokeWidth = 1f)
    }
    drawRect(color = color, style = Stroke(width = 2f))
}

private fun DrawScope.drawPath(path: List<Point>, color: Color) {
    val composed = Path()
    path.forEachIndexed { index, point ->
        val x = point.x * size.width
        val y = point.y * size.height
        if (index == 0) composed.moveTo(x, y) else composed.lineTo(x, y)
    }
    drawPath(
        path = composed,
        color = color,
        style = Stroke(
            width = 4f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
        )
    )
    path.forEach { drawDot(it, color, radius = 6f) }
}

private fun DrawScope.drawDot(point: Point, color: Color, radius: Float) {
    drawCircle(
        color = color,
        radius = radius,
        center = Offset(point.x * size.width, point.y * size.height)
    )
}

private fun DrawScope.drawMarker(marker: Point, color: Color) {
    val center = Offset(marker.x * size.width, marker.y * size.height)
    drawCircle(color = color.copy(alpha = 0.25f), radius = 26f, center = center)
    drawCircle(color = color, radius = 12f, center = center)
    drawCircle(color = Color.White, radius = 4f, center = center)
}
