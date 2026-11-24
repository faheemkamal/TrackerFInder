package com.example.trackerfinder.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.trackerfinder.Proximity
import com.example.trackerfinder.TrackState
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val BINS = 24 // 15° each

/** Compass heading in degrees (0 = north), or null if the phone has no suitable sensor. */
@Composable
fun rememberHeading(): State<Float?> {
    val ctx = LocalContext.current
    val heading = remember { mutableStateOf<Float?>(null) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val rot = FloatArray(9)
        val ori = FloatArray(3)
        var sx = 0.0
        var sy = 0.0
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, ori)
                // Low-pass on the unit circle so 359° → 1° doesn't jump.
                sx = sx * 0.8 + cos(ori[0].toDouble()) * 0.2
                sy = sy * 0.8 + sin(ori[0].toDouble()) * 0.2
                val deg = Math.toDegrees(atan2(sy, sx)).toFloat()
                heading.value = (deg + 360f) % 360f
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return heading
}

/**
 * Direction mode. Your body blocks Bluetooth, so with the phone held flat against your chest the
 * signal is strongest when you face the tracker. Turn slowly in a full circle; the app records
 * the signal for each direction and points to the strongest one.
 */
@Composable
fun DirectionPanel(t: TrackState, modifier: Modifier = Modifier, dial: Dp = 280.dp) {
    val headingState = rememberHeading()
    val heading = headingState.value
    val bins = remember { FloatArray(BINS) { Float.NaN } }
    var version by remember { mutableIntStateOf(0) }

    LaunchedEffect(t.samples) {
        val h = headingState.value ?: return@LaunchedEffect
        val rssi = t.rawRssi ?: return@LaunchedEffect
        if (t.searching) return@LaunchedEffect
        val i = (((h + 7.5f) / 15f).toInt()) % BINS
        bins[i] = if (bins[i].isNaN()) rssi.toFloat() else bins[i] * 0.6f + rssi * 0.4f
        version++
    }

    // Reading `version` here makes the panel redraw whenever a new sample lands.
    val covered = if (version >= 0) bins.count { !it.isNaN() } else 0
    val best = bins.indices.filter { !bins[it].isNaN() }.maxByOrNull { bins[it] }
    val bestAngle = best?.let { it * 15f }
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (heading == null) {
            Text(
                "Waiting for the compass… If this doesn't change, your phone may not have a compass sensor.",
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }
        Canvas(Modifier.size(dial)) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2 * 0.92f
            // Rotate the world so the top of the dial is the way the phone is facing.
            rotate(-heading, c) {
                bins.forEachIndexed { i, v ->
                    if (v.isNaN()) return@forEachIndexed
                    val heat = Proximity.heat(v.toDouble())
                    val len = r * (0.35f + 0.65f * heat)
                    drawArc(
                        color = heatColor(heat).copy(alpha = if (i == best) 0.95f else 0.55f),
                        startAngle = i * 15f - 90f - 7f, sweepAngle = 14f, useCenter = true,
                        topLeft = Offset(c.x - len, c.y - len), size = Size(len * 2, len * 2),
                    )
                }
                drawCircle(outline, radius = r, center = c, style = Stroke(2.dp.toPx()))
                // North tick.
                drawLine(onSurface, Offset(c.x, c.y - r), Offset(c.x, c.y - r + 14.dp.toPx()), strokeWidth = 3.dp.toPx())
            }
            // Phone-facing arrow (fixed, pointing up).
            val arrow = Path().apply {
                moveTo(c.x, c.y - r * 0.55f)
                lineTo(c.x - 14.dp.toPx(), c.y - r * 0.55f + 26.dp.toPx())
                lineTo(c.x + 14.dp.toPx(), c.y - r * 0.55f + 26.dp.toPx())
                close()
            }
            drawPath(arrow, primary)
            drawCircle(primary, radius = 6.dp.toPx(), center = c)
        }
        Spacer(Modifier.height(12.dp))
        val instruction = when {
            covered < BINS / 2 -> "Hold the phone flat against your chest and turn slowly in a full circle."
            bestAngle == null -> "Keep turning…"
            else -> {
                val rel = ((bestAngle - heading + 540f) % 360f) - 180f
                when {
                    abs(rel) <= 20 -> "Straight ahead. Walk this way."
                    rel > 0 -> "Turn right about ${rel.roundToInt()}°"
                    else -> "Turn left about ${(-rel).roundToInt()}°"
                }
            }
        }
        Text(instruction, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { covered / BINS.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Directions sampled: $covered of $BINS",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { bins.fill(Float.NaN); version++ }) { Text("New sweep") }
        Text(
            "Works best in open spaces. Do a new sweep every few meters as you walk toward it.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

