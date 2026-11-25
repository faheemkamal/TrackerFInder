package com.example.trackerfinder.ui

import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.trackerfinder.Proximity
import com.example.trackerfinder.ScanViewModel
import androidx.compose.material3.Icon

/** Phone-style signal bars, 0–4 lit. */
@Composable
fun SignalBars(rssi: Int?, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val lit = when {
        rssi == null -> 0
        rssi > -60 -> 4
        rssi > -70 -> 3
        rssi > -80 -> 2
        rssi > -90 -> 1
        else -> 0
    }
    val off = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.size(22.dp, 18.dp)) {
        val gap = size.width * 0.1f
        val w = (size.width - gap * 3) / 4
        for (i in 0 until 4) {
            val h = size.height * (0.35f + 0.65f * (i + 1) / 4f)
            drawRoundRect(
                color = if (i < lit) color else off,
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 3),
            )
        }
    }
}

/**
 * Radar-style pulse: rings ripple out from the centre, faster when hotter.
 * [heat] null means "searching" (slow, neutral rings).
 */
@Composable
fun RadarPulse(heat: Float?, modifier: Modifier = Modifier, diameter: Dp = 280.dp, content: @Composable () -> Unit) {
    val color = if (heat == null) MaterialTheme.colorScheme.primary else heatColor(heat)
    val period by rememberUpdatedState(if (heat == null) 2400 else (2200 - heat * 1700).toInt())
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(period, easing = LinearEasing))
        }
    }
    val ringColor by rememberUpdatedState(color)
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val maxR = size.minDimension / 2
            val core = maxR * 0.58f
            for (k in 0 until 3) {
                val p = (progress.value + k / 3f) % 1f
                val r = core + (maxR - core) * p
                drawCircle(ringColor.copy(alpha = 0.35f * (1f - p)), radius = r, style = Stroke(width = 3.dp.toPx()))
            }
            drawCircle(
                Brush.radialGradient(listOf(ringColor, ringColor.copy(alpha = 0.78f)), radius = core),
                radius = core,
            )
        }
        content()
    }
}

/** Signal over the last minute: line with a soft fill, cold-to-hot colouring by current heat. */
@Composable
fun SignalChart(values: List<Float>, modifier: Modifier = Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val minV = -100f
        val maxV = -30f
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        if (values.size < 2) return@Canvas
        val stepX = size.width / (ScanViewModel.HISTORY_SAMPLES - 1)
        val offset = ScanViewModel.HISTORY_SAMPLES - values.size
        val line = Path()
        values.forEachIndexed { i, v ->
            val x = (i + offset) * stepX
            val y = size.height * (1f - ((v - minV) / (maxV - minV)).coerceIn(0f, 1f))
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val color = heatColor(Proximity.heat(values.last().toDouble()))
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(offset * stepX, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(line, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = NumberStyle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(icon, null, Modifier.padding(20.dp).size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        action()
    }
}

@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(modifier, shape = RoundedCornerShape(50), color = container) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(16.dp), tint = content)
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}

fun formatAgo(wallMillis: Long): String {
    if (wallMillis <= 0) return "never"
    val s = (System.currentTimeMillis() - wallMillis) / 1000
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        s < 86_400 -> "${s / 3600} h ago"
        else -> "${s / 86_400} d ago"
    }
}

/**
 * Like LaunchedEffect, but only runs while the app is on screen: paused when you leave the app,
 * restarted when you come back. Use it for every repeating refresh loop; a plain LaunchedEffect
 * loop keeps running in the background as long as Android keeps the app in memory.
 */
@Composable
fun WhileOnScreen(vararg keys: Any?, block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(owner, *keys) {
        owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED, block)
    }
}
