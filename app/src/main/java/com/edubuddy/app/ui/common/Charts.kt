package com.edubuddy.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class Slice(val value: Float, val color: Color)

/** Animated donut. Slices get a small gap; a single slice draws a full ring. */
@Composable
fun DonutChart(
    slices: List<Slice>,
    modifier: Modifier = Modifier,
    size: Dp = 148.dp,
    thickness: Dp = 12.dp,
    description: String = "",
    center: @Composable () -> Unit = {},
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    val anim = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(modifier.size(size).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = thickness.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (total <= 0f) return@Canvas
            val gap = if (slices.count { it.value > 0 } > 1) 2.5f else 0f
            var start = -90f
            val sweepTotal = 360f * anim.value
            slices.filter { it.value > 0 }.forEach { s ->
                val sweep = s.value / total * 360f
                val visible = (sweepTotal - (start + 90f)).coerceIn(0f, sweep)
                if (visible > gap) {
                    drawArc(s.color, start + gap / 2, visible - gap, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                }
                start += sweep
            }
        }
        center()
    }
}

/**
 * Vertical bars, one per value (e.g. daily spending in a month). [highlight] marks today.
 * Labels are drawn under every 5th bar plus the first and last.
 */
@Composable
fun BarChart(
    values: List<Long>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    highlight: Int = -1,
    height: Dp = 120.dp,
    labelFor: (Int) -> String = { (it + 1).toString() },
    description: String = "",
) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(values) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    val muted = color.copy(alpha = 0.22f)
    val empty = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (values.isEmpty()) return@Canvas
            val slot = size.width / values.size
            val barW = (slot * 0.56f).coerceAtMost(14.dp.toPx())
            val radius = CornerRadius(barW / 2, barW / 2)
            values.forEachIndexed { i, v ->
                val x = i * slot + (slot - barW) / 2
                if (v <= 0L) {
                    drawRoundRect(empty, Offset(x, size.height - 3.dp.toPx()), Size(barW, 3.dp.toPx()), radius)
                } else {
                    val h = (v.toFloat() / max * size.height * anim.value).coerceAtLeast(4.dp.toPx())
                    drawRoundRect(if (i == highlight || highlight < 0) color else muted, Offset(x, size.height - h), Size(barW, h), radius)
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            values.indices.forEach { i ->
                val show = i == 0 || i == values.lastIndex || (i + 1) % 5 == 0
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (show) {
                        Text(labelFor(i), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}
