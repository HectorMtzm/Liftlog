package com.hectormtzm.liftlog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.hectormtzm.liftlog.ui.formatShortDate
import kotlin.math.abs

data class ChartPoint(val time: Long, val value: Double)

/**
 * Single-series line chart: 2dp line, 8dp markers with a surface ring, three recessive
 * gridlines, first/last date on the x axis. Tap or drag to inspect a point.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    formatValue: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    var selected by remember(points) { mutableStateOf(points.lastIndex) }

    val sel = points[selected.coerceIn(0, points.lastIndex)]
    Column(modifier) {
        Text(
            "${formatValue(sel.value)}  ·  ${formatShortDate(sel.time)}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        val ticks = remember(points) { niceTicks(points.minOf { it.value }, points.maxOf { it.value }) }
        val lo = ticks.first()
        val hi = ticks.last()

        fun xFor(i: Int, left: Float, width: Float): Float =
            if (points.size == 1) left + width / 2 else left + width * i / (points.size - 1)

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(points) {
                    detectTapGestures { pos ->
                        val left = 44.dp.toPx(); val width = size.width - left - 8.dp.toPx()
                        selected = points.indices.minBy { abs(xFor(it, left, width) - pos.x) }
                    }
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures { change, _ ->
                        val left = 44.dp.toPx(); val width = size.width - left - 8.dp.toPx()
                        selected = points.indices.minBy { abs(xFor(it, left, width) - change.position.x) }
                    }
                }
        ) {
            val left = 44.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = size.height - 20.dp.toPx()
            val width = right - left
            fun yFor(v: Double): Float = (bottom - (v - lo) / (hi - lo) * (bottom - top)).toFloat()

            // Gridlines + y labels
            for (v in ticks) {
                val y = yFor(v)
                drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                val layout = measurer.measure(formatValue(v).substringBefore(' '), labelStyle)
                drawText(layout, topLeft = Offset(left - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
            }
            // X labels: first and last date
            val first = measurer.measure(formatShortDate(points.first().time), labelStyle)
            drawText(first, topLeft = Offset(left, bottom + 4.dp.toPx()))
            if (points.size > 1) {
                val last = measurer.measure(formatShortDate(points.last().time), labelStyle)
                drawText(last, topLeft = Offset(right - last.size.width, bottom + 4.dp.toPx()))
            }

            // Selection crosshair
            val sx = xFor(selected.coerceIn(0, points.lastIndex), left, width)
            drawLine(
                gridColor, Offset(sx, top), Offset(sx, bottom), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )

            if (points.size > 1) {
                val path = Path()
                points.forEachIndexed { i, p ->
                    val x = xFor(i, left, width); val y = yFor(p.value)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            points.forEachIndexed { i, p ->
                val c = Offset(xFor(i, left, width), yFor(p.value))
                val isSel = i == selected
                val r = if (isSel) 5.dp.toPx() else 4.dp.toPx()
                drawCircle(surface, radius = r + 2.dp.toPx(), center = c)
                drawCircle(lineColor, radius = r, center = c)
            }
        }
    }
}

/** Round axis ticks (steps of 1, 2, 2.5 or 5 x 10^n) covering [min, max], 3-5 lines. */
internal fun niceTicks(min: Double, max: Double): List<Double> {
    var lo = min
    var hi = max
    if (hi - lo < 1e-9) {
        val pad = if (hi == 0.0) 1.0 else abs(hi) * 0.1
        lo -= pad; hi += pad
    }
    val raw = (hi - lo) / 3
    val mag = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * mag }.first { it >= raw }
    val start = (kotlin.math.floor(lo / step) * step).coerceAtLeast(if (min >= 0) 0.0 else Double.NEGATIVE_INFINITY)
    val end = kotlin.math.ceil(hi / step) * step
    val out = mutableListOf<Double>()
    var v = start
    while (v <= end + step * 1e-6) { out += v; v += step }
    return out
}
