package com.example.trackerfinder.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.trackerfinder.findhub.Geo
import com.example.trackerfinder.findhub.Geofence
import com.example.trackerfinder.findhub.HubDevice
import com.example.trackerfinder.findhub.HubUi
import com.example.trackerfinder.findhub.HubViewModel
import kotlinx.coroutines.launch
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.util.Locale

/** Our own "you are here" blue. */
val MeBlue = Color(0xFF2E7CF6)

/** Full-screen map with a draggable sheet: tag list, or one tag's details and actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapPage(
    hub: HubViewModel,
    ui: HubUi,
    fence: Geofence?,
    onHistory: () -> Unit,
    onMenu: () -> Unit,
    menuContent: @Composable () -> Unit,
    findNearby: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheet = rememberBottomSheetScaffoldState()
    var detail by rememberSaveable { mutableStateOf(ui.devices.size == 1) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var meOverlay by remember { mutableStateOf<MeOverlay?>(null) }
    var meAt by remember { mutableStateOf<GeoPoint?>(null) }
    val dev = ui.selected
    // Stable callbacks, so a GPS fix doesn't rebuild every map overlay.
    val onMe = remember { { p: GeoPoint -> meAt = p } }
    val onMap2 = remember { { m: MapView, me: MeOverlay? -> mapView = m; meOverlay = me } }
    if (detail && ui.devices.size > 1) BackHandler { detail = false }

    fun flyTo(p: GeoPoint, zoom: Double = 16.5) = mapView?.controller?.animateTo(p, zoom, 900L)
    fun fitAll() {
        val m = mapView ?: return
        val pts = ui.devices.mapNotNull { d -> d.lastLat?.let { GeoPoint(it, d.lastLon!!) } } + listOfNotNull(meAt)
        when {
            pts.size >= 2 -> m.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(pts).increaseByScale(1.5f), true, 96)
            pts.size == 1 -> flyTo(pts[0], 15.0)
        }
    }

    BottomSheetScaffold(
        scaffoldState = sheet,
        sheetPeekHeight = if (detail && dev != null) 300.dp else 220.dp,
        sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        sheetShadowElevation = 8.dp,
        sheetContent = {
            AnimatedContent(
                targetState = detail && dev != null,
                transitionSpec = {
                    val dir = if (targetState) 1 else -1
                    ((slideInHorizontally(tween(300)) { dir * it / 3 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(250)) { -dir * it / 3 } + fadeOut(tween(180))))
                        .using(SizeTransform(clip = false))
                },
                label = "sheet",
            ) { showDetail ->
                if (showDetail && dev != null) {
                    DeviceDetail(
                        hub, ui, dev, fence, meAt,
                        canGoBack = ui.devices.size > 1,
                        onBack = { detail = false },
                        onMenu = onMenu,
                        menuContent = menuContent,
                        onHistory = onHistory,
                        findNearby = findNearby,
                    )
                } else {
                    DeviceList(ui, meAt, onCheckAll = hub::checkAllNow, onMenu = onMenu, menuContent = menuContent) { d ->
                        hub.select(d.id)
                        detail = true
                        d.lastLat?.let { flyTo(GeoPoint(it, d.lastLon!!)) }
                        scope.launch { runCatching { sheet.bottomSheetState.partialExpand() } }
                    }
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            OsmMap(
                ui, fence, Modifier.fillMaxSize(),
                main = true,
                showMe = true,
                onMe = onMe,
                onMap2 = onMap2,
            )
            Column(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        val p = meOverlay?.myLocation ?: meAt
                        if (p != null) flyTo(p, 17.0) else hub.toast("Waiting for your location… make sure Location is on.")
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) { MyLocationGlyph(MeBlue) }
                SmallFloatingActionButton(onClick = ::fitAll, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Icon(Icons.Filled.LocationOn, "Show all tags")
                }
            }
            Surface(
                Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 3.dp,
            ) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Find Hub", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = onHistory) {
                        Icon(Icons.Filled.DateRange, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("History")
                    }
                }
            }
            BusyPill(ui.busy, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp))
        }
    }
}

@Composable
private fun DeviceList(
    ui: HubUi,
    meAt: GeoPoint?,
    onCheckAll: () -> Unit,
    onMenu: () -> Unit,
    menuContent: @Composable () -> Unit,
    onPick: (HubDevice) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your tags", style = MaterialTheme.typography.titleLarge)
                Text(checkSummary(ui), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onCheckAll, enabled = ui.busy == null) { Icon(Icons.Filled.Refresh, "Check all now") }
            Box {
                IconButton(onClick = onMenu) { Icon(Icons.Filled.MoreVert, "More") }
                menuContent()
            }
        }
        Spacer(Modifier.height(8.dp))
        ui.devices.forEachIndexed { i, d ->
            if (i > 0) HorizontalDivider(Modifier.padding(start = 80.dp, end = 20.dp))
            Row(
                Modifier.fillMaxWidth().clickable { onPick(d) }.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TagAvatar(d.name, selected = d.id == ui.selectedId)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(seenLine(d, meAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DeviceDetail(
    hub: HubViewModel,
    ui: HubUi,
    dev: HubDevice,
    fence: Geofence?,
    meAt: GeoPoint?,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    menuContent: @Composable () -> Unit,
    onHistory: () -> Unit,
    findNearby: (String) -> Unit,
) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = if (canGoBack) 4.dp else 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (canGoBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "All tags") }
            TagAvatar(dev.name, selected = true, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(dev.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(seenLine(dev, meAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = onMenu) { Icon(Icons.Filled.MoreVert, "More") }
                menuContent()
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundAction(Icons.Filled.Notifications, if (ui.ringing) "Stop" else "Play sound", active = ui.ringing) { hub.ring(!ui.ringing) }
            RoundAction(Icons.Filled.Refresh, "Locate", enabled = ui.busy == null) { hub.locateNow() }
            RoundAction(Icons.Filled.Search, "Find nearby") { findNearby(dev.id) }
            RoundAction(Icons.Filled.Place, "Directions", enabled = dev.lastLat != null) {
                val q = String.format(Locale.US, "%.6f,%.6f", dev.lastLat, dev.lastLon)
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$q?q=$q(${Uri.encode(dev.name)})"))) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundAction(Icons.Filled.DateRange, "History", onClick = onHistory)
            RoundAction(Icons.Filled.Share, "Share", enabled = dev.lastLat != null) {
                shareLocation(ctx, dev.name, dev.lastLat!!, dev.lastLon!!, dev.lastTs)
            }
            Spacer(Modifier.width(72.dp)); Spacer(Modifier.width(72.dp))
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.padding(horizontal = 20.dp)) {
            GuardButton(ui.guard) { hub.setGuard(!ui.guard) }
            Spacer(Modifier.height(10.dp))
            Text(checkSummary(ui), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            val addr by com.example.trackerfinder.rememberAddress(dev.lastLat, dev.lastLon)
            addr?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            val batt = remember(dev.id, dev.lastTs) { com.example.trackerfinder.findhub.HubSettings.battery(ctx, dev.id) }
            batt?.let {
                Text(
                    it.label + " (read from its Bluetooth signal " + formatAgo(it.at) + ")",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (it.level == "low" || it.level == "critical") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            dev.lastAccuracy?.let { Text("Accuracy about ±${it.toInt()} m (shaded circle)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (fence != null) Text("Area alert: ${fence.radiusM} m", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            AnimatedVisibility(dev.lastError != null) {
                Text("Last check problem: ${dev.lastError}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun seenLine(d: HubDevice, meAt: GeoPoint?): String {
    if (d.lastTs == null) return "No location yet"
    val away = if (meAt != null && d.lastLat != null) " · " + formatKm(Geo.distanceM(meAt.latitude, meAt.longitude, d.lastLat, d.lastLon!!)) + " away" else ""
    return "Seen ${formatAgo(d.lastTs * 1000)}$away"
}

@Composable
private fun RoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        spring(stiffness = Spring.StiffnessMediumLow), label = "actionBg",
    )
    val fg by animateColorAsState(
        if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer, label = "actionFg",
    )
    Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = bg, contentColor = fg),
        ) {
            AnimatedContent(active, transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() }, label = "icon") { on ->
                Icon(if (on) Icons.Filled.Close else icon, null)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun TagAvatar(name: String, selected: Boolean, size: Dp = 40.dp) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer, label = "avatar",
    )
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    Box(Modifier.size(size).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        Text(name.trim().firstOrNull()?.uppercase() ?: "•", color = fg, style = MaterialTheme.typography.titleMedium)
    }
}

/** A simple crosshair, drawn so we don't need an icon pack. */
@Composable
private fun MyLocationGlyph(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension * 0.30f
        drawCircle(color, r, c, style = Stroke(width = size.minDimension * 0.1f))
        drawCircle(color, r * 0.45f, c)
        val t = size.minDimension * 0.5f
        val w = size.minDimension * 0.1f
        drawLine(color, Offset(c.x, c.y - t), Offset(c.x, c.y - r), w)
        drawLine(color, Offset(c.x, c.y + r), Offset(c.x, c.y + t), w)
        drawLine(color, Offset(c.x - t, c.y), Offset(c.x - r, c.y), w)
        drawLine(color, Offset(c.x + r, c.y), Offset(c.x + t, c.y), w)
    }
}
