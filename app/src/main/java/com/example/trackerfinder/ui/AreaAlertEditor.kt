package com.example.trackerfinder.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.trackerfinder.Addresses
import com.example.trackerfinder.findhub.Geo
import com.example.trackerfinder.findhub.Geofence
import com.example.trackerfinder.findhub.HubDevice
import com.example.trackerfinder.findhub.HubUi
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import kotlin.math.cos

/**
 * Area alert set-up, the Find Hub way: pick a place (search an address, tap the map, or use the
 * tag's or your own location), choose a radius, and get notified when the tag is found outside the
 * circle (and again when it's back). The circle is previewed live on the map.
 */
@Composable
internal fun AreaAlertEditor(
    ui: HubUi,
    dev: HubDevice,
    current: Geofence?,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onSave: (Geofence) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var center by remember {
        mutableStateOf(
            current?.let { LatLng(it.lat, it.lon) } ?: dev.lastLat?.let { LatLng(it, dev.lastLon!!) },
        )
    }
    var label by remember { mutableStateOf(current?.label) }
    var radius by remember { mutableIntStateOf(current?.radiusM ?: 300) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Addresses.Place>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var handle by remember { mutableStateOf<MapHandle?>(null) }

    // Name the spot when it came from a tap or a location (search results already have a name).
    LaunchedEffect(center) {
        val c = center ?: return@LaunchedEffect
        if (label == null) label = Addresses.lookup(ctx, c.latitude, c.longitude)
    }
    // Keep the whole circle in view.
    LaunchedEffect(center, radius, handle) {
        val c = center ?: return@LaunchedEffect
        val h = handle ?: return@LaunchedEffect
        val dLat = radius / 111_320.0
        val dLon = radius / (111_320.0 * cos(Math.toRadians(c.latitude)).coerceAtLeast(0.01))
        h.fit(listOf(LatLng(c.latitude - dLat, c.longitude - dLon), LatLng(c.latitude + dLat, c.longitude + dLon)), 28)
    }

    fun moveTo(p: LatLng, name: String?) { center = p; label = name; results = null }
    fun search() {
        focus.clearFocus()
        if (query.isBlank()) return
        searching = true
        scope.launch {
            results = runCatching { Addresses.search(ctx, query) }.getOrDefault(emptyList())
            searching = false
        }
    }

    // Map preview: just this tag's dot and the circle; no history, no automatic camera moves.
    val previewUi = remember(ui.devices, dev.id) { HubUi(loading = false, devices = ui.devices.filter { it.id == dev.id }) }
    val preview = center?.let { Geofence(it.latitude, it.longitude, radius, null) }
    val onTap = remember { { p: LatLng -> moveTo(p, null) } }
    val onReady = remember { { h: MapHandle -> handle = h } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Close") }
                    Text("Area alert · ${dev.name}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (current != null) TextButton(onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Search an address or place") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else if (query.isNotEmpty()) IconButton(onClick = { query = ""; results = null }) { Icon(Icons.Filled.Close, "Clear") }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { search() }),
                )
                results?.let { list ->
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        if (list.isEmpty()) Text("No places found. Try a fuller address, or tap the map.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                        list.forEach { p ->
                            Row(
                                Modifier.fillMaxWidth().clickable { moveTo(LatLng(p.lat, p.lon), p.label) }.padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Text(p.label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            HorizontalDivider()
                        }
                    }
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (dev.lastLat != null) AssistChip(
                        onClick = { moveTo(LatLng(dev.lastLat, dev.lastLon!!), null) },
                        label = { Text("Tag's location") },
                        leadingIcon = { Icon(Icons.Filled.LocationOn, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    )
                    AssistChip(
                        onClick = { myLocation(ctx)?.let { moveTo(it, null) } ?: android.widget.Toast.makeText(ctx, "Your location isn't known yet. Turn on Location and try again.", android.widget.Toast.LENGTH_SHORT).show() },
                        label = { Text("My location") },
                        leadingIcon = { MyLocationGlyph(MeBlue, Modifier.size(AssistChipDefaults.IconSize)) },
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Surface(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxSize()) {
                        TagMap(previewUi, preview, Modifier.fillMaxSize(), onReady = onReady, onMapTap = onTap)
                    }
                    Surface(
                        Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                    ) {
                        Text(
                            if (center == null) "Search a place or tap the map" else "Tap the map to move the center",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    val c = center
                    Text(
                        when {
                            c == null -> "No place chosen yet"
                            label != null -> label!!
                            else -> "Finding the address…"
                        },
                        style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    if (c != null && dev.lastLat != null) {
                        val d = Geo.distanceM(c.latitude, c.longitude, dev.lastLat, dev.lastLon!!)
                        Text(
                            "${dev.name} is ${formatKm(d)} from here · " + if (d <= radius) "inside the area now" else "outside the area now",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Radius: ${formatKm(radius.toDouble())}", style = MaterialTheme.typography.titleSmall)
                    Slider(value = radius.toFloat(), onValueChange = { radius = (Math.round(it / 50f) * 50).coerceIn(100, 3000) }, valueRange = 100f..3000f)
                    Text(
                        "You'll be notified when ${dev.name} is found outside this circle, and again when it's back. " +
                            "Find Hub locations can be off by 50 m or more, so keep the radius generous.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val p = center ?: return@Button
                            val inside = dev.lastLat?.let { Geo.distanceM(p.latitude, p.longitude, it, dev.lastLon!!) <= radius }
                            onSave(Geofence(p.latitude, p.longitude, radius, inside, label))
                        },
                        enabled = center != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (current == null) "Turn on area alert" else "Save area alert") }
                }
            }
        }
    }
}

/** The phone's last known position, if location permission is granted and one is available. */
@SuppressLint("MissingPermission")
private fun myLocation(ctx: Context): LatLng? {
    val ok = listOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    if (!ok) return null
    val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
    return lm.allProviders.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }?.let { LatLng(it.latitude, it.longitude) }
}
