package com.example.trackerfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackerfinder.Favorite
import com.example.trackerfinder.FavoriteStatus
import com.example.trackerfinder.Proximity
import com.example.trackerfinder.ScanViewModel
import com.example.trackerfinder.status

@Composable
fun FavoritesScreen(
    vm: ScanViewModel,
    contentPadding: PaddingValues,
    openFindHub: () -> Unit,
    goToNearby: () -> Unit,
    setAlert: (String, Boolean) -> Unit,
) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<Favorite?>(null) }
    var deleting by remember { mutableStateOf<Favorite?>(null) }

    if (favorites.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(contentPadding), verticalArrangement = Arrangement.Center) {
            EmptyState(
                icon = Icons.Filled.Star,
                title = "No favorites yet",
                body = "Find your tracker (or a Wi-Fi network) in Nearby, open it, and tap the star. It'll live here so you can find it again with one tap.",
            ) {
                Button(onClick = goToNearby) { Text("Go to Nearby") }
            }
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(favorites, key = { it.id }) { fav ->
            FavoriteCard(
                fav = fav,
                status = fav.status(devices.values),
                onFind = { vm.find(fav.id) },
                onRename = { renaming = fav },
                onDelete = { deleting = fav },
                onFindHub = openFindHub,
                onToggleAlert = { setAlert(fav.id, !fav.alertWhenLeft) },
            )
        }
        item {
            Text(
                "Trackers hide their identity and change it every ~15 minutes, so \"Possibly nearby\" means a tracker " +
                    "of the same type is in range. If you own several of the same model, the app can't always tell them apart. " +
                    "Wi-Fi favorites are only checked while you're finding them, to save battery.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    renaming?.let { fav ->
        NicknameDialog(
            title = "Rename favorite",
            initial = fav.nickname,
            confirm = "Save",
            onDismiss = { renaming = null },
            onConfirm = { vm.renameFavorite(fav.id, it); renaming = null },
        )
    }
    deleting?.let { fav ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove ${fav.nickname}?") },
            text = { Text("It will be removed from Favorites. The device itself isn't affected.") },
            confirmButton = { TextButton(onClick = { vm.removeFavorite(fav.id); deleting = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FavoriteCard(
    fav: Favorite,
    status: FavoriteStatus,
    onFind: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onFindHub: () -> Unit,
    onToggleAlert: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val rssi = when (status) {
        is FavoriteStatus.Here -> status.device.rssi
        is FavoriteStatus.Maybe -> status.strongest.rssi
        FavoriteStatus.Away -> null
    }
    val accent = rssi?.let { heatColor(Proximity.heat(it.toDouble())) } ?: MaterialTheme.colorScheme.outline

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(16.dp), color = accent.copy(alpha = 0.18f)) {
                    Icon(Icons.Filled.Star, null, Modifier.padding(10.dp).size(24.dp), tint = accent)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(fav.nickname, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    Text(
                        if (fav.alertWhenLeft) "${fav.kind.label} · Left-behind alert on" else fav.kind.label,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (fav.alertWhenLeft) {
                        Icon(Icons.Filled.Notifications, "Alert on", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        // Left-behind alerts use background Bluetooth scans; Wi-Fi can't be watched that way.
                        if (fav.kind != com.example.trackerfinder.TrackerKind.WIFI || fav.alertWhenLeft) DropdownMenuItem(
                            text = { Text(if (fav.alertWhenLeft) "Turn off left-behind alert" else "Alert me if left behind") },
                            leadingIcon = { Icon(Icons.Filled.Notifications, null) },
                            onClick = { menu = false; onToggleAlert() },
                        )
                        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; onRename() })
                        if (fav.kind != com.example.trackerfinder.TrackerKind.WIFI) DropdownMenuItem(text = { Text("Open in Find Hub") }, leadingIcon = { Icon(Icons.Filled.LocationOn, null) }, onClick = { menu = false; onFindHub() })
                        DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                val (label, container, content) = when (status) {
                    is FavoriteStatus.Here -> Triple("In range", accent.copy(alpha = 0.18f), MaterialTheme.colorScheme.onSurface)
                    is FavoriteStatus.Maybe -> Triple(
                        if (status.count > 1) "Possibly nearby · ${status.count} similar" else "Possibly nearby",
                        accent.copy(alpha = 0.18f), MaterialTheme.colorScheme.onSurface,
                    )
                    FavoriteStatus.Away -> Triple("Last seen ${formatAgo(fav.lastSeenAt)}", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Pill(label, container, content)
                if (rssi != null) {
                    Spacer(Modifier.width(10.dp))
                    SignalBars(rssi, color = accent)
                    Spacer(Modifier.width(6.dp))
                    Text("$rssi dBm", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClick = onFind) {
                    Icon(Icons.Filled.Search, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Find")
                }
            }
        }
    }
}

@Composable
fun NicknameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(40) }, singleLine = true,
                label = { Text("Nickname") }, placeholder = { Text("e.g. Keys, Wallet, Bag") },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
