package com.example.trackerfinder.ui

import com.example.trackerfinder.AppLock
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackerfinder.Proximity
import com.example.trackerfinder.ScanViewModel
import com.example.trackerfinder.findhub.HubViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow

@Composable
fun MoreScreen(
    vm: ScanViewModel,
    hub: HubViewModel,
    contentPadding: PaddingValues,
    openFindHub: () -> Unit,
    openBluetoothSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val themeMode by AppTheme.mode.collectAsStateWithLifecycle()
    val beep by vm.beep.collectAsStateWithLifecycle()
    val vibrate by vm.vibrate.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val watched = favorites.count { it.alertWhenLeft }
    val rssiAt1m by vm.rssiAt1m.collectAsStateWithLifecycle()
    val clear = ListItemDefaults.colors(containerColor = Color.Transparent)
    var lockDialog by remember { mutableStateOf(false) }
    var lockVersion by remember { mutableIntStateOf(0) }
    var help by remember { mutableStateOf(false) }
    if (lockDialog) AppLockDialog { lockDialog = false; lockVersion++ }
    if (help) HelpDialog { help = false }

    Column(
        Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text("Appearance", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = themeMode == m,
                    onClick = { AppTheme.set(ctx, m) },
                    shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                ) { Text(m.label) }
            }
        }
        Spacer(Modifier.height(12.dp))
        val mapProvider by MapTiles.provider.collectAsStateWithLifecycle()
        Text("Map", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            MapProvider.entries.forEachIndexed { i, p ->
                SegmentedButton(
                    selected = mapProvider == p,
                    onClick = { MapTiles.set(ctx, p) },
                    shape = SegmentedButtonDefaults.itemShape(i, MapProvider.entries.size),
                ) { Text(p.label, maxLines = 1) }
            }
        }
        Text(mapProvider.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        Spacer(Modifier.height(16.dp))
        Text("Privacy", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Group {
            val locked = remember(lockVersion) { AppLock.isEnabled(ctx) }
            ListItem(
                colors = clear,
                leadingContent = { Icon(Icons.Filled.Lock, null) },
                headlineContent = { Text("App lock") },
                supportingContent = { Text(if (locked) "On. Password asked on open and after a minute away." else "Off. Add a password so others can't see where your tags are.") },
                trailingContent = { TextButton(onClick = { lockDialog = true }) { Text(if (locked) "Change" else "Set up") } },
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Find my phone", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        FindPhoneSettings(vm)
        Spacer(Modifier.height(16.dp))
        Text("Quiet hours & safe places", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        AlertRulesSettings()
        Spacer(Modifier.height(16.dp))
        Text("Find Hub", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        FindHubSettings(hub)
        Spacer(Modifier.height(16.dp))
        Text("Nearby finder", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Group {
            ListItem(
                colors = clear,
                leadingContent = { Icon(Icons.Filled.LocationOn, null) },
                headlineContent = { Text("Open Google Find Hub") },
                supportingContent = { Text("See your tracker on the map, make it ring, or mark it lost") },
                modifier = Modifier.clickableRow(openFindHub),
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ListItem(
                colors = clear,
                leadingContent = { Icon(Icons.Filled.Settings, null) },
                headlineContent = { Text("Bluetooth settings") },
                modifier = Modifier.clickableRow(openBluetoothSettings),
            )
        }
        Spacer(Modifier.height(16.dp))
        Group {
            ListItem(
                colors = clear,
                leadingContent = { Icon(Icons.Filled.Notifications, null) },
                headlineContent = { Text("Beep in the finder") },
                supportingContent = { Text("Beeps faster as you get closer") },
                trailingContent = { Switch(checked = beep, onCheckedChange = vm::setBeep) },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ListItem(
                colors = clear,
                leadingContent = { Icon(Icons.Filled.Notifications, null) },
                headlineContent = { Text("Vibrate in the finder") },
                supportingContent = { Text("Pulses faster as you get closer") },
                trailingContent = { Switch(checked = vibrate, onCheckedChange = vm::setVibrate) },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ListItem(
                colors = clear,
                leadingContent = { Icon(Icons.Filled.Build, null) },
                headlineContent = { Text("Distance calibration") },
                supportingContent = {
                    Text(
                        if (rssiAt1m == Proximity.DEFAULT_RSSI_AT_1M) "Default: $rssiAt1m dBm at 1 m"
                        else "Calibrated: $rssiAt1m dBm at 1 m",
                    )
                },
                trailingContent = { TextButton(onClick = vm::resetCalibration) { Text("Reset") } },
            )
        }
        Spacer(Modifier.height(16.dp))
        Group {
            ListItem(
                colors = clear,
                leadingContent = { Icon(Icons.Filled.Warning, null) },
                headlineContent = { Text("Left-behind alerts") },
                supportingContent = {
                    Text(
                        (if (watched == 0) "Off. Turn it on per tracker from its menu in Favorites. "
                        else "Watching $watched tracker${if (watched == 1) "" else "s"} in the background. ") +
                            "You get a notification when a tracker hasn't been heard for 2 minutes after being near you. " +
                            "It can't tell your tracker apart from a look-alike nearby, and it uses some extra battery.",
                    )
                },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ListItem(
                colors = clear,
                leadingContent = { Icon(Icons.Filled.Home, null) },
                headlineContent = { Text("Widgets & Quick Settings") },
                supportingContent = {
                    Text(
                        "Long-press your home screen → Widgets → Tracker Finder: \"My trackers\" shows your favorites, \"Tag on map\" shows your Find Hub tag's last spot. " +
                            "Swipe down twice → edit (pencil) → drag \"Find tracker\" into Quick Settings to open the finder in one tap.",
                    )
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        Group {
            ListItem(
                colors = clear,
                leadingContent = { Icon(Icons.Filled.Info, null) },
                headlineContent = { Text("Help, privacy & legal") },
                modifier = Modifier.clickableRow { help = true },
                supportingContent = {
                    Text(
                        "Your tracker sends a short Bluetooth signal every second or so. This app listens for it and " +
                            "turns its strength into hotter/colder. It only listens: it can't see the tracker's location " +
                            "on a map. The Map tab gets that from Google's Find Hub network instead. Tap for the full guide.",
                    )
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        Group {
            ListItem(
                colors = clear,
                leadingContent = { Icon(Icons.Filled.Info, null) },
                headlineContent = { Text("Licenses") },
                supportingContent = {
                    Text(
                        "The Find Hub features use Tag Tracker Open (Shekh-Muhsen's Tag Tracker, modified) and " +
                            "GoogleFindMyTools by Leon Böttger, both under the GNU GPL v3.0. Because of that, this app " +
                            "is GPL-3.0 too; the full source and license texts are in the project files. " +
                            "Not affiliated with Google. Map data © OpenStreetMap contributors; optional Esri tiles © Esri.",
                    )
                },
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Tracker Finder 4.7.2",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        )
    }
}

@Composable
internal fun Group(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) { Column(Modifier.padding(vertical = 4.dp)) { content() } }
}

private fun Modifier.clickableRow(onClick: () -> Unit) = this.then(Modifier.clickable(onClick = onClick))
