package com.example.trackerfinder.ui

import com.example.trackerfinder.rememberAddress
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.activity.compose.BackHandler
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackerfinder.findhub.Geo
import com.example.trackerfinder.findhub.Geofence
import com.example.trackerfinder.findhub.GoogleAuthActivity
import com.example.trackerfinder.findhub.HubRange
import com.example.trackerfinder.findhub.HubSettings
import com.example.trackerfinder.findhub.HubUi
import com.example.trackerfinder.findhub.HubViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MapScreen(
    hub: HubViewModel,
    contentPadding: PaddingValues,
    openFindHub: () -> Unit,
    findNearby: (deviceId: String) -> Unit,
    page: Int = 0,
    setPage: (Int) -> Unit = {},
) {
    val ui by hub.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { hub.afterSetupStep() }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(hub::importLoginFile) }

    val full = !ui.loading && ui.account?.connected == true && ui.devices.isNotEmpty()
    Column(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding()).padding(top = if (full) 0.dp else contentPadding.calculateTopPadding())) {
        when {
            ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Starting the Find Hub engine…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ui.account?.connected != true -> SetupPanel(
                ui = ui,
                signIn = { authLauncher.launch(Intent(ctx, GoogleAuthActivity::class.java).putExtra(GoogleAuthActivity.EXTRA_MODE, GoogleAuthActivity.MODE_SIGN_IN)) },
                unlock = { authLauncher.launch(Intent(ctx, GoogleAuthActivity::class.java).putExtra(GoogleAuthActivity.EXTRA_MODE, GoogleAuthActivity.MODE_UNLOCK)) },
                importFile = { importLauncher.launch(arrayOf("application/json", "*/*")) },
            )
            ui.devices.isEmpty() -> EmptyState(
                icon = Icons.Filled.LocationOn,
                title = "No tags on this Google account",
                body = "Tags you've added in Google's Find Hub app appear here. Pull them in with Refresh.",
                modifier = Modifier.fillMaxSize(),
            ) { Button(onClick = hub::afterSetupStep) { Text("Refresh tag list") } }
            else -> HubContent(hub, ui, openFindHub, findNearby, page, setPage)
        }
    }
}

@Composable
private fun SetupPanel(ui: HubUi, signIn: () -> Unit, unlock: () -> Unit, importFile: () -> Unit) {
    val acct = ui.account
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Connect Google Find Hub", style = MaterialTheme.typography.headlineSmall)
        Text(
            "See your tags on a map from anywhere, keep their full location history, and get alerts. " +
                "Two one-time steps, both on Google's own pages inside the app.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StepCard(1, "Sign in to Google", "Use the Google account your tag is on in Find Hub.", done = acct?.signedIn == true, enabled = true, action = "Sign in", onClick = signIn)
        StepCard(2, "Unlock encryption", "Google asks for your phone's screen lock PIN or pattern so the app can decrypt your tag's locations.", done = acct?.unlocked == true, enabled = acct?.signedIn == true, action = "Unlock", onClick = unlock)
        ui.busy?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(20.dp)) {
            Row(Modifier.padding(16.dp)) {
                Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(12.dp))
                Text(
                    "This uses an unofficial way into Find Hub (GoogleFindMyTools). It may break if Google changes things, " +
                        "and it may go against Google's terms, so a secondary Google account is safer if you have one. " +
                        "Your tokens and history stay on this phone; the app has no server.",
                    color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        TextButton(onClick = importFile) { Text("Or import a login file made on a computer") }
        acct?.error?.let { Text("Engine: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun StepCard(n: Int, title: String, body: String, done: Boolean, enabled: Boolean, action: String, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(50), color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    if (done) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary)
                    else Text("$n", style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            if (!done) FilledTonalButton(onClick = onClick, enabled = enabled) { Text(action) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HubContent(hub: HubViewModel, ui: HubUi, openFindHub: () -> Unit, findNearby: (String) -> Unit, page: Int, setPage: (Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // page: 0 = Map, 1 = History (kept by the caller so Home can open History directly)
    LaunchedEffect(page) { if (page == 1) hub.loadCheckInfo() }
    var showDates by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showFence by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val dev = ui.selected
    var fenceVersion by remember { mutableIntStateOf(0) }
    val fence = remember(dev?.id, fenceVersion) { dev?.let { HubSettings.geofence(ctx, it.id) } }
    // Keep the screen fresh while it's open: re-read stored data every minute (no network call).
    LaunchedEffect(Unit) { while (true) { delay(60_000); hub.refreshQuiet() } }

    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (slideInHorizontally(tween(320)) { dir * it / 4 } + fadeIn(tween(320))) togetherWith
                (slideOutHorizontally(tween(260)) { -dir * it / 4 } + fadeOut(tween(200)))
        },
        label = "hubPage",
    ) { pg ->
        if (pg == 0) {
            MapPage(
                hub, ui, fence,
                onHistory = { setPage(1) },
                onMenu = { menu = true },
                menuContent = {
                    DropdownMenu(menu, { menu = false }) {
                        if (dev != null) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; showRename = true })
                            DropdownMenuItem(text = { Text(if (fence == null) "Set area alert" else "Change area alert") }, onClick = { menu = false; showFence = true })
                        }
                        DropdownMenuItem(text = { Text("Check all tags now") }, onClick = { menu = false; hub.checkAllNow() })
                        DropdownMenuItem(text = { Text("Refresh tag list from Google") }, onClick = { menu = false; hub.refreshTagList() })
                        DropdownMenuItem(text = { Text("Open Google Find Hub") }, onClick = { menu = false; openFindHub() })
                    }
                },
                findNearby = findNearby,
            )
        } else Column(Modifier.fillMaxSize().statusBarsPadding()) {
            BackHandler { setPage(0) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { setPage(0) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to map") }
                Text("History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            if (ui.devices.size > 1) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ui.devices.forEach { d -> FilterChip(selected = d.id == ui.selectedId, onClick = { hub.select(d.id) }, label = { Text(d.name) }) }
                }
            }
            if (dev != null) HistoryPage(hub, ui, fence, onCustom = { showDates = true }, onExport = { gpx ->
                scope.launch { hub.export(gpx)?.let { share(ctx, it, if (gpx) "application/gpx+xml" else "text/csv") } }
            })
        }
    }

    if (showDates) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDates = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val st = state.selectedStartDateMillis!!
                        hub.setCustomRange(st, state.selectedEndDateMillis ?: st)
                        showDates = false
                    },
                ) { Text("Show") }
            },
            dismissButton = { TextButton(onClick = { showDates = false }) { Text("Cancel") } },
        ) { DateRangePicker(state, Modifier.height(480.dp)) }
    }
    if (showRename && dev != null) {
        NicknameDialog("Rename tag", dev.name, "Save", { showRename = false }) { hub.rename(it); showRename = false }
    }
    if (showFence && dev != null) {
        FenceDialog(
            current = fence,
            canCenter = dev.lastLat != null,
            onDismiss = { showFence = false },
            onRemove = { HubSettings.setGeofence(ctx, dev.id, null); fenceVersion++; showFence = false },
            onSave = { r ->
                val lat = fence?.lat ?: dev.lastLat!!
                val lon = fence?.lon ?: dev.lastLon!!
                val inside = dev.lastLat?.let { Geo.distanceM(lat, lon, it, dev.lastLon!!) <= r }
                HubSettings.setGeofence(ctx, dev.id, Geofence(lat, lon, r, inside))
                fenceVersion++
                showFence = false
            },
            onRecenter = { r ->
                HubSettings.setGeofence(ctx, dev.id, Geofence(dev.lastLat!!, dev.lastLon!!, r, true))
                fenceVersion++
                showFence = false
            },
        )
    }
}

@Composable
internal fun GuardButton(on: Boolean, onClick: () -> Unit) {
    if (on) Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
        Icon(Icons.Filled.Lock, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Guarding · tap to stop")
    } else OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Lock, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Park & guard (alert if it moves)")
    }
}

fun shareLocation(ctx: Context, name: String, lat: Double, lon: Double, ts: Long?) {
    val coords = String.format(Locale.US, "%.6f,%.6f", lat, lon)
    val text = "$name" + (ts?.let { " (seen ${formatAgo(it * 1000)})" } ?: "") + ": https://maps.google.com/?q=$coords"
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share location"))
}

internal fun checkSummary(ui: HubUi): String {
    if (!ui.backgroundOn) return "Background checks off"
    val last = ui.checks.firstOrNull()?.ts
    return "Checked every ${ui.intervalMin} min" + (last?.let { " · last ${formatAgo(it * 1000)}" } ?: "")
}

@Composable
internal fun BusyPill(busy: String?, modifier: Modifier) {
    busy ?: return
    Surface(modifier.padding(12.dp), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.inverseSurface) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.inverseOnSurface)
            Spacer(Modifier.width(10.dp))
            Text(busy, color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** History: how/when data is collected, then the range, path, replay, stats and every saved point. */
@Composable
private fun HistoryPage(hub: HubViewModel, ui: HubUi, fence: Geofence?, onCustom: () -> Unit, onExport: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val geo = ui.geoHistory
    val dt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val stays = remember(ui.history) { com.example.trackerfinder.findhub.Trips.stays(ui.history) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { CheckCard(hub, ui) }
        item {
            Text("Range", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HubRange.entries.forEach { r ->
                    FilterChip(
                        selected = ui.range == r,
                        onClick = { if (r == HubRange.CUSTOM) onCustom() else hub.setRange(r) },
                        label = { Text(if (r == HubRange.CUSTOM && ui.range == r) customLabel(ui) else r.label) },
                    )
                }
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !ui.showHeat, onClick = { hub.setHeat(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Path") }
                SegmentedButton(selected = ui.showHeat, onClick = { hub.setHeat(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Heatmap") }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show individual points", style = MaterialTheme.typography.bodyLarge)
                    Text("Dots for every saved location, not just the path", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = ui.showPoints, onCheckedChange = { hub.toggleShowPoints() })
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp)) {
                Box(Modifier.fillMaxWidth().height(260.dp)) {
                    TagMap(ui, fence, Modifier.fillMaxSize())
                    BusyPill(ui.busy, Modifier.align(Alignment.TopCenter))
                }
            }
        }
        if (geo.size >= 2) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = hub::toggleReplay) {
                    if (ui.replaying) Text("❚❚", style = MaterialTheme.typography.titleMedium)
                    else Icon(Icons.Filled.PlayArrow, "Replay trip")
                }
                Slider(
                    value = (ui.replayIndex ?: (geo.size - 1)).toFloat(),
                    onValueChange = { hub.setReplayIndex(it.toInt()) },
                    valueRange = 0f..(geo.size - 1).toFloat(),
                    modifier = Modifier.weight(1f),
                )
            }
            val idx = (ui.replayIndex ?: (geo.size - 1)).coerceIn(0, geo.size - 1)
            Text("Replay position: ${dt.format(Date(geo[idx].ts * 1000))}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            val st = ui.stats
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Distance", st?.let { formatKm(it.distanceM) } ?: "–", Modifier.weight(1f))
                StatTile("Time span", st?.let { formatDuration(it.durationS) } ?: "–", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Locations", st?.points?.toString() ?: "–", Modifier.weight(1f))
                StatTile("Avg speed", st?.avgSpeedKmh?.let { String.format(Locale.US, "%.1f km/h", it) } ?: "–", Modifier.weight(1f))
            }
        }
        item {
            Text("Places visited", style = MaterialTheme.typography.titleSmall)
            if (stays.isEmpty()) Text(
                "No stops of 10 minutes or more in this range yet.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(stays.size) { k ->
            val st = stays[stays.size - 1 - k] // newest first
            val addr by rememberAddress(st.lat, st.lon)
            ListItem(
                modifier = Modifier.clickable { hub.setReplayIndex(st.firstIndex) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                leadingContent = { Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary) },
                headlineContent = { Text(addr ?: String.format(Locale.US, "%.5f, %.5f", st.lat, st.lon), maxLines = 2) },
                supportingContent = {
                    Text(dt.format(Date(st.startTs * 1000)) + " → " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(st.endTs * 1000)) +
                        " · " + formatDuration(st.durationS) + " · ${st.points} reports")
                },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onExport(false) }, modifier = Modifier.weight(1f)) { Text("Export CSV") }
                OutlinedButton(onClick = { onExport(true) }, modifier = Modifier.weight(1f)) { Text("Export GPX") }
            }
        }
        item {
            Text("Saved locations (${ui.history.size})", style = MaterialTheme.typography.titleSmall)
            if (ui.history.isEmpty()) Text(
                "Nothing saved in this range yet. Each check stores whatever new locations Google has for the tag, " +
                    "so history fills in as phones pass near it. Try a longer range, or tap Check now above.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val newest = ui.history.asReversed().take(300)
        items(newest.size) { k ->
            val p = newest[k]
            val geoIndex = if (p.lat != null) geo.indexOfFirst { it.ts == p.ts && it.lat == p.lat } else -1
            ListItem(
                modifier = Modifier.clickable(enabled = geoIndex >= 0) { hub.setReplayIndex(geoIndex) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                headlineContent = { Text(dt.format(Date(p.ts * 1000))) },
                supportingContent = {
                    Text(
                        when {
                            p.semanticName != null -> "Google says: ${p.semanticName}"
                            p.lat != null -> (com.example.trackerfinder.Addresses.cached(ctx, p.lat, p.lon!!) ?: String.format(Locale.US, "%.5f, %.5f", p.lat, p.lon)) +
                                (p.accuracy?.let { " · ±${it.toInt()} m" } ?: "")
                            else -> "No coordinates"
                        },
                    )
                },
                trailingContent = { if (p.isOwnReport) Text("your phone", style = MaterialTheme.typography.labelSmall) },
            )
        }
        if (ui.history.size > 300) item {
            Text("Showing the newest 300. Export CSV to get all ${ui.history.size}.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** How often data is checked and stored, when it last happened, and the next run. */
@Composable
private fun CheckCard(hub: HubViewModel, ui: HubUi) {
    val tf = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Automatic checks", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (ui.backgroundOn) "Every ${ui.intervalMin} minutes, even when the app is closed" else "Off: history only grows when you tap Check now",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = ui.backgroundOn, onCheckedChange = hub::setBackground)
            }
            if (ui.backgroundOn) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 15, 30, 60, 120).forEach { m ->
                        FilterChip(selected = ui.intervalMin == m, onClick = { hub.setInterval(m) }, label = { Text(if (m < 60) "$m min" else "${m / 60} h") })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val last = ui.checks.firstOrNull()
            Text(
                buildString {
                    append("Last check: ")
                    append(last?.let { "${formatAgo(it.ts * 1000)} (${if (!it.ok) "failed" else if (it.added > 0) "${it.added} new saved" else "nothing new"})" } ?: "none yet")
                    if (ui.backgroundOn) append("\nNext check: " + (ui.nextCheckAt?.let { "around ${tf.format(Date(it))}" } ?: "being scheduled"))
                    ui.totalSaved?.let { append("\nStored on this phone: $it locations in total") }
                    append("\nChecks today: ${ui.checks.count { System.currentTimeMillis() / 1000 - it.ts < 86_400 }}")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            ui.avgCheckMs?.let {
                Text(
                    "A check takes about ${String.format(Locale.US, "%.1f", it / 1000.0)} s of network time, so battery use is small.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (ui.checks.size > 1) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Recent: " + ui.checks.take(6).joinToString("  ·  ") { tf.format(Date(it.ts * 1000)) + if (!it.ok) " ✕" else if (it.added > 0) " +${it.added}" else "" },
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Android may run checks a few minutes late while the phone is idle. Google only has a new location when a phone passes near the tag.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
            )
            TextButton(onClick = hub::checkAllNow, enabled = ui.busy == null) { Text("Check now") }
        }
    }
}

@Composable
private fun FenceDialog(
    current: Geofence?,
    canCenter: Boolean,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onSave: (Int) -> Unit,
    onRecenter: (Int) -> Unit,
) {
    var radius by remember { mutableIntStateOf(current?.radiusM ?: 300) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Lock, null) },
        title = { Text("Area alert") },
        text = {
            Column {
                Text(
                    if (current == null) "Get notified when the tag's location is outside this circle around where it is now. " +
                        "Find Hub positions can be off by 50 m or more, so keep the radius generous."
                    else "Notifies you when the tag leaves this circle and when it comes back.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text("Radius: $radius m", style = MaterialTheme.typography.titleSmall)
                Slider(value = radius.toFloat(), onValueChange = { radius = (it / 50).toInt() * 50 }, valueRange = 100f..3000f)
                if (current != null && canCenter) TextButton(onClick = { onRecenter(radius) }) { Text("Move center to the tag's latest location") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(radius) }, enabled = current != null || canCenter) { Text("Save") } },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = onRemove) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private fun customLabel(ui: HubUi): String {
    val f = DateFormat.getDateInstance(DateFormat.SHORT)
    val s = ui.customStart ?: return "Custom"
    val e = ui.customEnd ?: s
    return f.format(Date(s * 1000)) + " – " + f.format(Date(e * 1000))
}

fun formatKm(m: Double): String = if (m < 1000) "${m.toInt()} m" else String.format(Locale.US, "%.1f km", m / 1000)

fun formatDuration(s: Long): String = when {
    s <= 0 -> "–"
    s < 3600 -> "${s / 60} min"
    s < 86_400 -> "${s / 3600} h ${(s % 3600) / 60} min"
    else -> "${s / 86_400} d ${(s % 86_400) / 3600} h"
}

private fun share(ctx: Context, f: File, mime: String) {
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "Export ${f.name}"))
}

