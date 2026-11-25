package com.example.trackerfinder.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.trackerfinder.WifiBands
import com.example.trackerfinder.WifiHeard

/** One Wi-Fi band's drawing range and the channels to mark on its axis. */
private data class Band(val name: String, val lo: Int, val hi: Int, val ticks: List<Int>, val labelEvery: Int, val candidates: List<Int>)

private val BANDS = listOf(
    Band("2.4 GHz", 2399, 2497, (1..14).toList(), 1, (1..13).toList()),
    Band("5 GHz", 5150, 5895, (36..64 step 4) + (100..144 step 4) + (149..177 step 4), 2,
        (36..64 step 4) + (100..144 step 4) + (149..165 step 4)),
    Band("6 GHz", 5935, 7125, (1..233 step 4).toList(), 4, (1..233 step 16).toList()),
)

private fun freqOf(band: Band, ch: Int): Int = when (band.name) {
    "2.4 GHz" -> if (ch == 14) 2484 else 2407 + 5 * ch
    "5 GHz" -> 5000 + 5 * ch
    else -> 5950 + 5 * ch
}

/** The span a network occupies on the chart, in MHz. 2.4 GHz 20 MHz channels really spread over ~22 MHz. */
private fun span(n: WifiHeard): Pair<Float, Float> {
    val c = (if (n.centerMhz > 0) n.centerMhz else n.freq).toFloat()
    val half = if (n.freq < 3000 && n.widthMhz == 20) 11f else n.widthMhz / 2f
    return (c - half) to (c + half)
}

/** Stable, distinct colour per network. */
private fun colorFor(bssid: String): Color {
    val h = ((bssid.hashCode() and 0x7FFFFFFF) % 360).toFloat()
    return Color.hsv(h, 0.70f, 0.92f)
}

/**
 * Wi-Fi spectrum: the classic channel chart. Each network is a rounded hump centred on its channel,
 * as wide as its channel width and as tall as its signal, so overlapping humps show busy channels.
 * Below it: how crowded each channel is, and the quietest choice.
 */
@Composable
internal fun WifiSpectrum(nets: List<WifiHeard>, throttled: Boolean, wifiOn: Boolean) {
    val present = BANDS.filter { b -> nets.any { it.freq in b.lo..b.hi } }
    var bandName by rememberSaveable { mutableStateOf("2.4 GHz") }
    val band = BANDS.first { it.name == (if (present.any { p -> p.name == bandName } || present.isEmpty()) bandName else present.first().name) }
    val inBand = nets.filter { it.freq in band.lo..band.hi }.sortedByDescending { it.rssi }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            when {
                !wifiOn -> "Wi-Fi is off. Turn it on to see the spectrum (you don't need to connect)."
                throttled -> "Updates about every 30 seconds (Android's Wi-Fi scan limit). Taller humps are stronger networks; where humps overlap, those channels are busy."
                else -> "Updates every few seconds. Taller humps are stronger networks; where humps overlap, those channels are busy."
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BANDS.forEach { b ->
                val count = nets.count { it.freq in b.lo..b.hi }
                if (count > 0 || b.name != "6 GHz") FilterChip(
                    selected = band.name == b.name, onClick = { bandName = b.name },
                    label = { Text("${b.name} ($count)") },
                )
            }
        }
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            SpectrumChart(band, inBand, Modifier.fillMaxWidth().height(280.dp).padding(start = 4.dp, end = 12.dp, top = 12.dp, bottom = 8.dp))
        }
        if (inBand.isEmpty()) {
            Text("No ${band.name} networks heard yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            ChannelLoad(band, inBand)
            Text("Networks", style = MaterialTheme.typography.titleSmall)
            inBand.forEach { n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(12.dp)) { drawCircle(colorFor(n.bssid)) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(n.ssid ?: "Hidden network", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(n.channelLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${n.rssi} dBm", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun SpectrumChart(band: Band, nets: List<WifiHeard>, modifier: Modifier) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val ink = MaterialTheme.colorScheme.onSurface.toArgb()
    // Grow in when the band changes, so the humps "rise" like a live analyser.
    val rise by animateFloatAsState(if (nets.isEmpty()) 0f else 1f, tween(600), label = "rise")
    Canvas(modifier) {
        val d = density
        val left = 34 * d
        val bottom = size.height - 22 * d
        val top = 6 * d
        val w = size.width - left
        val minDb = -100f
        val maxDb = -20f
        fun x(f: Float) = left + (f - band.lo) / (band.hi - band.lo) * w
        fun y(db: Float) = bottom - ((db - minDb) / (maxDb - minDb)).coerceIn(0f, 1f) * (bottom - top)

        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10 * d; color = label }
        // dBm grid
        for (db in -100..-20 step 10) {
            val yy = y(db.toFloat())
            drawLine(grid, Offset(left, yy), Offset(size.width, yy), strokeWidth = 1f,
                pathEffect = if (db == -100) null else PathEffect.dashPathEffect(floatArrayOf(4 * d, 4 * d)))
            if (db % 20 == 0) drawContext.canvas.nativeCanvas.drawText("$db", 2 * d, yy + 3.5f * d, small)
        }
        // channel ticks
        small.textAlign = Paint.Align.CENTER
        band.ticks.forEachIndexed { i, ch ->
            val xx = x(freqOf(band, ch).toFloat())
            drawLine(grid, Offset(xx, bottom), Offset(xx, bottom + 4 * d), strokeWidth = 1f)
            if (i % band.labelEvery == 0) drawContext.canvas.nativeCanvas.drawText("$ch", xx, bottom + 15 * d, small)
        }
        // humps, weakest first so strong ones sit on top
        val name = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f * d; color = ink; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
        nets.sortedBy { it.rssi }.forEach { n ->
            val (lo, hi) = span(n)
            val x0 = x(lo); val x1 = x(hi)
            val ww = x1 - x0
            val peak = y(minDb + (n.rssi - minDb) * rise)
            // A smooth arch (parabola) peaking at the network's signal in the channel centre. This is
            // only a picture: the phone reports one signal number per network, not power per MHz.
            val mid = (x0 + x1) / 2
            val p = Path().apply {
                moveTo(x0, bottom)
                quadraticTo(mid, 2 * peak - bottom, x1, bottom)
            }
            val c = colorFor(n.bssid)
            drawPath(p, c.copy(alpha = 0.16f))
            drawPath(p, c, style = Stroke(width = 2 * d))
            val title = (n.ssid ?: "hidden").let { if (it.length > 14) it.take(13) + "…" else it }
            name.color = c.toArgb()
            drawContext.canvas.nativeCanvas.drawText(title, ((x0 + x1) / 2).coerceIn(left + 20 * d, size.width - 20 * d), peak - 4 * d, name)
        }
    }
}

/** How crowded each channel is: networks overlapping it, weighted by how strong they are here. */
@Composable
private fun ChannelLoad(band: Band, nets: List<WifiHeard>) {
    data class Load(val ch: Int, val count: Int, val score: Float)
    val half = if (band.name == "2.4 GHz") 11 else 10
    val loads = band.candidates.map { ch ->
        val f = freqOf(band, ch)
        val over = nets.filter { n -> val (lo, hi) = span(n); hi > f - half && lo < f + half }
        Load(ch, over.size, over.sumOf { (((it.rssi + 100) / 60.0).coerceIn(0.05, 1.0)) }.toFloat())
    }
    val max = (loads.maxOfOrNull { it.score } ?: 1f).coerceAtLeast(0.01f)
    // On 2.4 GHz only 1, 6 and 11 don't overlap each other, so recommend among those.
    val pickFrom = if (band.name == "2.4 GHz") loads.filter { it.ch in listOf(1, 6, 11) } else loads
    val best = pickFrom.minByOrNull { it.score }
    val busiest = loads.maxByOrNull { it.score }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Channel load", style = MaterialTheme.typography.titleSmall)
        if (busiest != null && busiest.count > 0) Text(
            "Busiest: channel ${busiest.ch} (${busiest.count} network${if (busiest.count == 1) "" else "s"}). " +
                (best?.let { "Quietest choice: channel ${it.ch}" + if (band.name == "2.4 GHz") " (of 1, 6, 11)." else "." } ?: ""),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val rows = if (band.name == "2.4 GHz") loads else loads.filter { it.count > 0 || it == best }
        rows.forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ch ${l.ch}", modifier = Modifier.width(56.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (l == best) FontWeight.Bold else null,
                )
                Box(Modifier.weight(1f)) {
                    LinearProgressIndicator(
                        progress = { l.score / max },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = heatColor(l.score / max),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                }
                Text("${l.count}", modifier = Modifier.width(32.dp).padding(start = 8.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
