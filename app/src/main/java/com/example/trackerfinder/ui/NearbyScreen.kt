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

@Composable
fun NearbyScreen(vm: ScanViewModel, contentPadding: PaddingValues) {
    val devices by vm.devices.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    var trackersOnly by rememberSaveable { mutableStateOf(true) }
    val shown = devices.values.filter { !trackersOnly || it.kind.isTracker }.sortedByDescending { it.rssi }
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
                "Hold your phone right next to your tracker. The one at the top with the strongest signal is almost certainly it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = trackersOnly, onClick = { trackersOnly = true }, label = { Text("Trackers") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = !trackersOnly, onClick = { trackersOnly = false }, label = { Text("All devices") })
                Spacer(Modifier.weight(1f))
                Text("${shown.size} nearby", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (shown.isEmpty()) {
            item {
                RadarPulse(heat = null, Modifier.fillMaxWidth().padding(top = 24.dp), diameter = 180.dp) {
                    Icon(Icons.Filled.Search, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = "Scanning…",
                    body = if (trackersOnly) "No trackers heard yet. Press the tracker's button to wake it, or try All devices. " +
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
                        Text("${d.rssi} dBm", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
        )
    }
}
