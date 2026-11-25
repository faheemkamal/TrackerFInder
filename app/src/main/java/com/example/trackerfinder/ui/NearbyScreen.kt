package com.example.trackerfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackerfinder.Proximity
import com.example.trackerfinder.ScanViewModel
import com.example.trackerfinder.SeenDevice
import com.example.trackerfinder.TrackerKind
import com.example.trackerfinder.realRssi
import androidx.compose.runtime.mutableIntStateOf

@Composable
fun NearbyScreen(vm: ScanViewModel, contentPadding: PaddingValues) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    // 0 = trackers, 1 = all Bluetooth devices, 2 = Wi-Fi networks
    var view by rememberSaveable { mutableIntStateOf(0) }
    val trackersOnly = view == 0
    val wifiView = view == 2
    val shown = devices.values.filter {
        when (view) {
            0 -> it.kind.isTracker
            1 -> it.kind != TrackerKind.WIFI
            else -> it.kind == TrackerKind.WIFI
        }
    }.sortedByDescending { it.rssi }
    val favAddresses = favorites.map { it.lastAddress }.toSet()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                if (wifiView) "Wi-Fi networks around you, strongest first. Tap one to hunt for it with the hot/cold finder, or star it from there."
                else "Hold your phone right next to your tracker. The one at the top with the strongest signal is almost certainly it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = view == 0, onClick = { view = 0 }, label = { Text("Trackers") })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = view == 1, onClick = { view = 1 }, label = { Text("All") })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = view == 2, onClick = { view = 2 }, label = { Text("Wi-Fi") })
                Spacer(Modifier.weight(1f))
                Text("${shown.size} nearby", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (wifiView) item {
            Text(
                when {
                    !vm.wifiOn() -> "Wi-Fi is off. Turn it on to see networks (you don't need to connect to any)."
                    vm.wifiThrottled() -> "Android lets apps scan Wi-Fi only about every 30 seconds, so this list and the finder update slowly. " +
                        "The network you're connected to updates every second. For faster updates: Developer options → turn off \"Wi-Fi scan throttling\"."
                    else -> "Wi-Fi scan throttling is off, so networks update every few seconds."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (shown.isEmpty()) {
            item {
                RadarPulse(heat = null, Modifier.fillMaxWidth().padding(top = 24.dp), diameter = 180.dp) {
                    Icon(Icons.Filled.Search, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = "Scanning…",
                    body = if (wifiView) "No Wi-Fi networks heard yet. The first scan can take up to 30 seconds. Location must be on."
                    else if (trackersOnly) "No trackers heard yet. Press the tracker's button to wake it, or try All devices. " +
                        "If even All devices stays empty, turn on Location in quick settings."
                    else "Nothing heard yet. Check that Bluetooth and Location are on.",
                )
            }
        }
        items(shown, key = { it.address }) { d ->
            DeviceCard(d, isFavorite = d.address in favAddresses) { vm.select(d) }
        }
    }
}

@Composable
private fun DeviceCard(d: SeenDevice, isFavorite: Boolean, onClick: () -> Unit) {
    val heat = Proximity.heat(d.rssi.toDouble())
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            leadingContent = {
                androidx.compose.foundation.Canvas(Modifier.size(12.dp)) { drawCircle(heatColor(heat)) }
            },
            headlineContent = { Text(d.name ?: d.kind.label, maxLines = 1) },
            supportingContent = {
                Text(
                    if (d.name != null) "${d.kind.label} · ${d.address}" else d.address,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1,
                )
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isFavorite) {
                        Icon(Icons.Filled.Star, "Favorite", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(8.dp))
                    }
                    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.End) {
                        SignalBars(d.rssi)
                        Text("${d.realRssi} dBm", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
        )
    }
}
