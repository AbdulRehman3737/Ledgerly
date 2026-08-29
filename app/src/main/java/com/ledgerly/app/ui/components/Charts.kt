package com.ledgerly.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DonutSlice(val color: Color, val value: Float)

/**
 * Rounded donut chart drawn entirely with local Compose Canvas. Segments reveal
 * sequentially with a smooth animation.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    stroke: Dp = 22.dp,
    gapDegrees: Float = 3f,
    center: (@Composable () -> Unit)? = null,
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(total) {
        if (total > 0f) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
        }
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Butt),
            )
            if (total <= 0f) return@Canvas

            var acc = 0f
            val shownDeg = progress.value * 360f
            slices.forEach { s ->
                val startPos = acc / total * 360f
                acc += s.value
                val endPos = acc / total * 360f
                val sweep = if (shownDeg <= startPos) {
                    0f
                } else {
                    (kotlin.math.min(endPos, shownDeg) - startPos - gapDegrees).coerceAtLeast(0f)
                }
                if (sweep > 0f) {
                    drawArc(
                        color = s.color,
                        startAngle = startPos - 90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                }
            }
        }
        center?.invoke()
    }
}

data class BarGroup(val label: String, val values: List<Float>, val colors: List<Color>)

/**
 * Grouped vertical bar chart (e.g. income vs expense per month). Locally rendered.
 */
@Composable
fun GroupedBarChart(
    groups: List<BarGroup>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 180.dp,
    barWidth: Dp = 14.dp,
    corner: Dp = 4.dp,
    valueCompact: (Float) -> String = { "" },
    showValues: Boolean = false,
    labelFormatter: (String) -> String = { it },
) {
    val textMeasurer = rememberTextMeasurer()
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(groups) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(durationMillis = 800, easing = FastOutSlowInEasing))
    }
    val maxValue = groups.maxOfOrNull { g -> g.values.maxOrNull() ?: 0f } ?: 0f
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = TextStyle(color = axisColor, fontSize = 10.sp)

    Canvas(modifier = modifier.fillMaxWidth().height(chartHeight)) {
        if (groups.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val slotW = w / groups.size
        val lblH = 18.dp.toPx()
        val chartH = h - lblH
        val cap = corner.toPx()
        val round = StrokeJoin.Round

        drawLine(baselineColor, Offset(0f, chartH), Offset(w, chartH), strokeWidth = 1.dp.toPx())

        groups.forEachIndexed { gi, group ->
            val n = group.values.size
            val groupW = n * barWidth.toPx() + (n - 1) * 6.dp.toPx()
            val startX = gi * slotW + (slotW - groupW) / 2f
            val max = maxValue.takeIf { it > 0f } ?: 1f
            group.values.forEachIndexed { vi, v ->
                val barH = (v / max) * chartH * reveal.value
                val x = startX + vi * (barWidth.toPx() + 6.dp.toPx())
                val y = chartH - barH
                if (barH > 0f) {
                    val color = group.colors[vi % group.colors.size]
                    if (cap > 0f) {
                        val path = Path().apply {
                            moveTo(x, y + cap)
                            arcTo(Rect(x, y, x + barWidth.toPx(), y + cap * 2), 180f, 90f, false)
                            lineTo(x + barWidth.toPx(), y + cap)
                            arcTo(Rect(x + barWidth.toPx() - cap * 2, y, x + barWidth.toPx(), y + cap * 2), 270f, 90f, false)
                            lineTo(x + barWidth.toPx(), chartH + 1f)
                            lineTo(x, chartH + 1f)
                            close()
                        }
                        drawPath(path, color)
                    } else {
                        drawRect(color, Offset(x, y), Size(barWidth.toPx(), barH))
                    }
                    if (showValues && vi == 0 && barWidth.toPx() >= 8f) {
                        val text = textMeasurer.measure(valueCompact(v))
                        drawText(text, topLeft = Offset(x + (barWidth.toPx() - text.size.width) / 2f, (y - text.size.height - 3.dp.toPx()).coerceAtLeast(0f)))
                    }
                }
            }
            val label = textMeasurer.measure(labelFormatter(group.label), labelStyle)
            drawText(label, topLeft = Offset(gi * slotW + (slotW - label.size.width) / 2f, chartH + 3.dp.toPx()))
        }
    }
}

/**
 * Smooth line chart with a soft gradient fill below the line.
 */
@Composable
fun TrendLineChart(
    points: List<Float>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 140.dp,
    strokeWidth: Dp = 3.dp,
    lineColor: Color,
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(points) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    val revealed by animateFloatAsState(reveal.value, label = "trend")
    val fillColor = lineColor.copy(alpha = 0.16f)

    Canvas(modifier = modifier.fillMaxWidth().height(chartHeight)) {
        if (points.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val max = points.maxOrNull() ?: 0f
        val min = points.minOrNull() ?: 0f
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val pad = 6.dp.toPx()
        val stepX = if (points.size > 1) (w - pad * 2) / (points.size - 1) else 0f
        val path = Path()
        points.forEachIndexed { i, v ->
            val x = pad + i * stepX
            val y = h - pad - ((v - min) / range) * (h - pad * 2)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        clipRect(right = w * revealed) {
            drawPath(
                path,
                lineColor,
                style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            val fill = Path().apply {
                addPath(path)
                lineTo(pad + (points.size - 1) * stepX, h + 1f)
                lineTo(pad, h + 1f)
                close()
            }
            drawPath(fill, fillColor)
        }
    }
}

@Composable
fun FractionBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
) {
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), animationSpec = spring(dampingRatio = 1f, stiffness = 300f), label = "frac")
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = modifier.fillMaxWidth().height(height).background(track, RoundedCornerShape(height / 2))) {
        if (shown > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(shown)
                    .height(height)
                    .background(color, RoundedCornerShape(height / 2)),
            )
        }
    }
}