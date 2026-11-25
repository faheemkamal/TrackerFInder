package com.example.trackerfinder.ui

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackerfinder.Proximity
import com.example.trackerfinder.ScanViewModel
import com.example.trackerfinder.TrackState
import com.example.trackerfinder.Trend
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinderScreen(vm: ScanViewModel, t: TrackState) {
    val rssiAt1m by vm.rssiAt1m.collectAsStateWithLifecycle()
    val beep by vm.beep.collectAsStateWithLifecycle()
    val vibrate by vm.vibrate.collectAsStateWithLifecycle()
    var directionMode by rememberSaveable { mutableStateOf(false) }
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    // Finding a Wi-Fi network: scan Wi-Fi only while this finder is open.
    val findingWifi = t.kind == com.example.trackerfinder.TrackerKind.WIFI ||
        favorites.firstOrNull { it.id == t.favoriteId }?.kind == com.example.trackerfinder.TrackerKind.WIFI
    androidx.compose.runtime.DisposableEffect(findingWifi) {
        vm.setWifiScreen("finder", findingWifi)
        onDispose { vm.setWifiScreen("finder", false) }
    }
    val fav = favorites.firstOrNull { it.id == t.favoriteId }
    var naming by remember { mutableStateOf(false) }

    // Re-read the clock twice a second so the "lost" state updates even without new adverts.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    WhileOnScreen { while (true) { delay(500); now = SystemClock.elapsedRealtime() } }

    val lostFor = now - t.lastSeen
    val lost = !t.searching && lostFor > ScanViewModel.lostAfter(t.kind)
    val s = t.smoothed
    val heat = if (t.searching || lost || s == null) null else Proximity.heat(s)
    val zone = s?.let { Proximity.zone(it, rssiAt1m) }
    val title = fav?.nickname ?: t.name ?: t.kind.label

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val net = if (t.kind == com.example.trackerfinder.TrackerKind.WIFI) vm.wifiNets.collectAsStateWithLifecycle().value[t.address] else null
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(title, maxLines = 1)
                        if (net != null && net.freq > 0) Text(net.channelLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = vm::closeFinder) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    if (fav != null) {
                        IconButton(onClick = { vm.removeFavorite(fav.id) }) {
                            Icon(Icons.Filled.Star, "Remove from favorites", tint = MaterialTheme.colorScheme.tertiary)
                        }
                    } else if (!t.searching) {
                        IconButton(onClick = { naming = true }) { Icon(Icons.Outlined.Star, "Add to favorites") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !directionMode, onClick = { directionMode = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Distance") }
                SegmentedButton(
                    selected = directionMode, onClick = { directionMode = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Direction") }
            }
            Spacer(Modifier.height(12.dp))
            if (directionMode) {
                DirectionPanel(t, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                TrendPill(t, lost)
                Spacer(Modifier.height(12.dp))
            } else {
            RadarPulse(heat = heat) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    when {
                        t.searching -> {
                            Icon(Icons.Filled.Search, null, Modifier.size(36.dp), tint = Color.White)
                            Text("Searching", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text("Listening for ${title}…", color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                        }
                        lost -> {
                            Text("Lost", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            Text("No signal for ${lostFor / 1000}s", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
                        }
                        else -> {
                            Text(zone?.label ?: "…", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Text(zone?.hint ?: "", color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            TrendPill(t, lost)
            Spacer(Modifier.height(16.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Signal", if (s == null || t.searching) "–" else "${s.toInt()}", Modifier.weight(1f))
                StatTile("Distance*", if (s == null || lost || t.searching) "–" else formatMeters(Proximity.estimateMeters(s, rssiAt1m)), Modifier.weight(1f))
                StatTile("Raw dBm", t.rawRssi?.let { if (t.kind == com.example.trackerfinder.TrackerKind.WIFI) it + ScanViewModel.WIFI_OFFSET else it }?.toString() ?: "–", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))

            SectionCard {
                Text("Last 60 seconds", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                SignalChart(t.history, Modifier.fillMaxWidth().height(110.dp))
            }
            Spacer(Modifier.height(12.dp))

            if (t.kind == com.example.trackerfinder.TrackerKind.WIFI) {
                SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        if (vm.wifiThrottled()) "Wi-Fi updates about every 30 seconds (an Android limit), so walk slowly and pause. " +
                            "If you're connected to this network it updates every second. For faster updates: Developer options → turn off \"Wi-Fi scan throttling\"."
                        else "Wi-Fi scan throttling is off: updates every few seconds.",
                        color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            if (t.lookalikes > 0) {
                SectionCard(container = MaterialTheme.colorScheme.errorContainer) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${t.lookalikes} other tracker(s) of the same type are nearby. If the reading jumps around, " +
                                "the app may have switched to one of them. Go back and pick again right next to your tracker.",
                            color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Notifications, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Beep as I get closer", style = MaterialTheme.typography.titleSmall)
                        Text("Like a metal detector", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = beep, onCheckedChange = vm::setBeep)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Notifications, null, tint = Color.Transparent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Vibrate as I get closer", style = MaterialTheme.typography.titleSmall)
                        Text("For quiet places", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = vibrate, onCheckedChange = vm::setVibrate)
                }
                if (t.kind != com.example.trackerfinder.TrackerKind.WIFI) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = vm::calibrate, enabled = s != null && !lost && !t.searching) { Text("Calibrate at 1 m") }
                    TextButton(onClick = vm::resetCalibration) { Text("Reset") }
                }
                Text(
                    "Put the tracker 1 m away in open space, wait 5 seconds, then tap Calibrate. Reference: $rssiAt1m dBm.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    "*Distance is a rough guess from signal strength. Walls, bodies and pockets can throw it off a lot, " +
                        "so trust warmer/colder more than the number." +
                        if (t.addressChanges > 0) " The tracker changed its Bluetooth address ${t.addressChanges}× (a privacy feature) and the app followed it." else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (naming) {
        NicknameDialog(
            title = "Add to favorites",
            initial = t.name ?: "",
            confirm = "Add",
            onDismiss = { naming = false },
            onConfirm = { vm.addFavorite(it); naming = false },
        )
    }
}

@Composable
private fun TrendPill(t: TrackState, lost: Boolean) {
    val (text, color) = when {
        t.searching && t.kind == com.example.trackerfinder.TrackerKind.WIFI -> "Walk around slowly; Wi-Fi updates take a while" to MaterialTheme.colorScheme.onSurfaceVariant
        t.searching -> "Press the tracker's button or walk around" to MaterialTheme.colorScheme.onSurfaceVariant
        lost -> "Walk around slowly to pick it up again" to MaterialTheme.colorScheme.onSurfaceVariant
        t.trend == Trend.WARMER -> "Getting warmer" to Color(0xFFE53935)
        t.trend == Trend.COLDER -> "Getting colder" to Color(0xFF1E88E5)
        else -> "Holding steady" to MaterialTheme.colorScheme.onSurface
    }
    val animated by animateColorAsState(color, label = "trend")
    val icon = when {
        t.searching || lost -> null
        t.trend == Trend.WARMER -> Icons.Filled.KeyboardArrowUp
        t.trend == Trend.COLDER -> Icons.Filled.KeyboardArrowDown
        else -> null
    }
    Pill(text, animated.copy(alpha = 0.14f), animated, icon = icon)
}

@Composable
private fun SectionCard(
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

fun formatMeters(m: Double): String = when {
    m < 1 -> "<1 m"
    m < 10 -> String.format(Locale.US, "%.1f m", m)
    m < 50 -> "${m.toInt()} m"
    else -> "50+ m"
}
