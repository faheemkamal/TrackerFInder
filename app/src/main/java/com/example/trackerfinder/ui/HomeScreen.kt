package com.example.trackerfinder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackerfinder.FavoriteStatus
import com.example.trackerfinder.Proximity
import com.example.trackerfinder.ScanViewModel
import com.example.trackerfinder.findhub.HubDevice
import com.example.trackerfinder.findhub.HubSettings
import com.example.trackerfinder.findhub.HubViewModel
import com.example.trackerfinder.status
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/** Landing tab: everything at a glance, with one tap to the thing you most likely want. */
@Composable
fun HomeScreen(
    vm: ScanViewModel,
    hub: HubViewModel,
    contentPadding: PaddingValues,
    openTagOnMap: (String) -> Unit,
    openHistory: (String) -> Unit,
    goNearby: () -> Unit,
    goMap: () -> Unit,
    goFavorites: () -> Unit,
) {
    val ctx = LocalContext.current
    val ui by hub.ui.collectAsStateWithLifecycle()
    val allFavorites by vm.favorites.collectAsStateWithLifecycle()
    // Home is about Bluetooth trackers; saved Wi-Fi networks live in the Favorites tab only.
    val favorites = remember(allFavorites) { allFavorites.filter { it.kind != com.example.trackerfinder.TrackerKind.WIFI } }
    val devices by vm.devices.collectAsStateWithLifecycle()
    val rssiAt1m by vm.rssiAt1m.collectAsStateWithLifecycle()
    val connected = ui.account?.connected == true
    LaunchedEffect(connected) {
        if (!connected) return@LaunchedEffect
        hub.loadCheckInfo()
        while (true) { delay(60_000); hub.refreshQuiet() }
    }
    val statuses = favorites.map { it to it.status(devices.values) }
    val inRange = statuses.count { it.second !is FavoriteStatus.Away }
    val tf = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    // Read area alerts from storage once per tag list, not on every Bluetooth update.
    val fencedIds = remember(ui.devices) { ui.devices.filter { HubSettings.geofence(ctx, it.id) != null }.map { it.id }.toSet() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Summary
        item {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(
                        when {
                            favorites.isEmpty() && ui.devices.isEmpty() -> "Let's find your first tracker"
                            favorites.isNotEmpty() && inRange == favorites.size -> "All trackers nearby"
                            favorites.isNotEmpty() -> "$inRange of ${favorites.size} tracker${if (favorites.size == 1) "" else "s"} nearby"
                            else -> "${ui.devices.size} tag${if (ui.devices.size == 1) "" else "s"} on Find Hub"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        buildString {
                            if (connected) {
                                append("Find Hub: ${ui.devices.size} tag${if (ui.devices.size == 1) "" else "s"}")
                                val last = ui.checks.firstOrNull()?.ts
                                if (ui.backgroundOn) append(" · checked every ${ui.intervalMin} min") else append(" · auto checks off")
                                if (last != null) append(" · last ${formatAgo(last * 1000)}")
                            } else append("Find Hub not connected")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (ui.guard) {
                        Spacer(Modifier.height(10.dp))
                        Pill("Park & guard on", MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary, icon = Icons.Filled.Lock)
                    }
                }
            }
        }
        // Quick actions
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction(Icons.Filled.Search, "Scan", Modifier.weight(1f), onClick = goNearby)
                QuickAction(Icons.Filled.LocationOn, "Map", Modifier.weight(1f), onClick = goMap)
                QuickAction(Icons.Filled.Refresh, if (ui.busy != null) "Busy…" else "Check", Modifier.weight(1f), enabled = connected && ui.busy == null) { hub.checkAllNow() }
                QuickAction(Icons.Filled.Lock, if (ui.guard) "Unguard" else "Guard", Modifier.weight(1f), enabled = connected) { hub.setGuard(!ui.guard) }
            }
        }

        // Find Hub tags
        item { SectionHeader("Find Hub tags", if (connected) "Open map" else null, goMap) }
        if (!connected) item {
            Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.clickable(onClick = goMap)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("See your tag anywhere", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (ui.account?.signedIn == true) "Signed in. Finish step 2 (unlock) on the Map tab to see locations."
                        else "Connect your Google account to see where your tag was last reported, with history and alerts.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = goMap) { Text(if (ui.account?.signedIn == true) "Finish setup" else "Connect Find Hub") }
                }
            }
        } else if (ui.devices.isEmpty()) item {
            Text("No tags found on this Google account yet.", style = MaterialTheme.typography.bodyMedium)
        } else items(ui.devices, key = { "hub-" + it.id }) { d ->
            TagCard(
                d,
                fenced = d.id in fencedIds,
                onMap = { openTagOnMap(d.id) },
                onHistory = { openHistory(d.id) },
                onShare = { if (d.lastLat != null) shareLocation(ctx, d.name, d.lastLat, d.lastLon!!, d.lastTs) },
            )
        }

        // Favorites (Bluetooth)
        item { SectionHeader("Nearby trackers (Bluetooth)", if (favorites.isNotEmpty()) "See all" else null, goFavorites) }
        if (favorites.isEmpty()) item {
            Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.clickable(onClick = goNearby)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Save your tracker", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Scan, find your tracker in the list, and star it. Then it shows here with a live in-range status.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = goNearby) { Text("Scan nearby") }
                }
            }
        } else items(statuses, key = { "fav-" + it.first.id }) { (fav, st) ->
            val rssi = when (st) {
                is FavoriteStatus.Here -> st.device.rssi
                is FavoriteStatus.Maybe -> st.strongest.rssi
                FavoriteStatus.Away -> null
            }
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    SignalBars(rssi)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(fav.nickname, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when (st) {
                                is FavoriteStatus.Here -> "In range · " + Proximity.zone(rssi!!.toDouble(), rssiAt1m).label.lowercase()
                                is FavoriteStatus.Maybe -> "Possibly nearby"
                                FavoriteStatus.Away -> "Out of range · last heard ${formatAgo(fav.lastSeenAt)}"
                            } + if (fav.alertWhenLeft) " · watching" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (st is FavoriteStatus.Away) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        )
                    }
                    FilledTonalButton(onClick = { vm.find(fav.id) }) { Text("Find") }
                }
            }
        }

        // Checks & alerts
        if (connected) item {
            SectionHeader("Checks & alerts", null) {}
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val last = ui.checks.firstOrNull()
                    StatusLine("Automatic checks", if (ui.backgroundOn) "Every ${ui.intervalMin} min" else "Off")
                    StatusLine("Last check", last?.let { formatAgo(it.ts * 1000) + if (!it.ok) " (failed)" else if (it.added > 0) " (+${it.added})" else "" } ?: "None yet")
                    if (ui.backgroundOn) StatusLine("Next check", ui.nextCheckAt?.let { "~" + tf.format(Date(it)) } ?: "Scheduling")
                    ui.totalSaved?.let { StatusLine("Locations stored", "$it") }
                    StatusLine("Movement alerts", if (ui.guard) "On (${formatKm(HubSettings.moveDistance(ctx).toDouble())})" else "Off")
                    val fences = fencedIds.size
                    StatusLine("Area alerts", if (fences == 0) "None set" else "$fences tag${if (fences == 1) "" else "s"}")
                    StatusLine("Left-behind alerts", favorites.count { it.alertWhenLeft }.let { if (it == 0) "Off" else "Watching $it" })
                }
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (enabled) 1f else 0.5f),
    ) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TagCard(d: HubDevice, fenced: Boolean, onMap: () -> Unit, onHistory: () -> Unit, onShare: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.clickable(onClick = onMap),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(d.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (fenced) Pill("Area alert", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (d.lastTs == null) "No location yet" else "Last seen ${formatAgo(d.lastTs * 1000)}" + (d.lastAccuracy?.let { " · ±${it.toInt()} m" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            val addr by com.example.trackerfinder.rememberAddress(d.lastLat, d.lastLon)
            if (d.lastLat != null) Text(
                (addr ?: String.format(java.util.Locale.US, "%.5f, %.5f", d.lastLat, d.lastLon)) + " · ${d.pointCount} saved",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val ctx = LocalContext.current
            val batt = remember(d.id, d.lastTs) { com.example.trackerfinder.findhub.HubSettings.battery(ctx, d.id) }
            if (batt != null) Text(
                batt.label + " · checked " + formatAgo(batt.at),
                style = MaterialTheme.typography.bodySmall,
                color = if (batt.level == "low" || batt.level == "critical") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            d.lastError?.let { Text("Last check problem: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onMap) { Text("Map") }
                OutlinedButton(onClick = onHistory) { Text("History") }
                if (d.lastLat != null) OutlinedButton(onClick = onShare) { Text("Share") }
            }
        }
    }
}
