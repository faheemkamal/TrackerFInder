package com.example.trackerfinder.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackerfinder.findhub.FindHub
import com.example.trackerfinder.findhub.HubSettings
import com.example.trackerfinder.findhub.HubViewModel
import com.example.trackerfinder.findhub.PollWorker
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun FindHubSettings(hub: HubViewModel) {
    val ctx = LocalContext.current
    val ui by hub.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val clear = ListItemDefaults.colors(containerColor = Color.Transparent)
    var version by remember { mutableIntStateOf(0) } // bump to re-read settings
    var drive by remember { mutableStateOf<JSONObject?>(null) }
    var dialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    var working by remember { mutableStateOf<String?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmExport by remember { mutableStateOf(false) }
    val saveLoginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            dialog = "Export login file" to try {
                ctx.contentResolver.openOutputStream(uri, "wt")!!.use { o -> FindHub.secretsFile(ctx).inputStream().use { it.copyTo(o) } }
                "Saved. Keep this file private, and delete it once you no longer need it."
            } catch (e: Exception) {
                FindHub.friendly(e)
            }
        }
    }

    LaunchedEffect(version, ui.account) { drive = runCatching { FindHub.driveStatus(ctx) }.getOrNull() }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hub.setGuard(true); version++
    }
    val statusPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { version++ }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) hub.importCsv(uri)
    }
    // Re-read things the user may have changed in system settings when they come back.
    var resumeTick by remember { mutableIntStateOf(0) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumeTick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            HubSettings.setBackupFileUri(ctx, uri.toString())
            scope.launch {
                val err = PollWorker.backupToFileIfSet(ctx)
                dialog = "Backup file" to (err ?: "Saved. The file is updated after every check that finds new locations.")
                version++
            }
        }
    }

    fun run(label: String, block: suspend () -> String) {
        working = label
        scope.launch {
            val msg = try { block() } catch (e: Exception) { FindHub.friendly(e) }
            working = null
            dialog = label to msg
            version++
        }
    }

    val acct = ui.account
    Group {
        ListItem(
            colors = clear,
            leadingContent = { Icon(Icons.Filled.AccountCircle, null) },
            headlineContent = { Text(if (acct?.signedIn == true) acct.email.ifBlank { "Signed in" } else "Not connected") },
            supportingContent = {
                Text(
                    when {
                        acct == null -> "Starting…"
                        acct.connected -> "Connected. Tokens stay on this phone."
                        acct.signedIn -> "Signed in. Unlock encryption on the Map tab to see locations."
                        else -> "Connect on the Map tab."
                    },
                )
            },
            trailingContent = { if (acct?.signedIn == true) TextButton(onClick = { confirmSignOut = true }) { Text("Sign out") } },
        )
        if (acct?.signedIn == true) {
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ListItem(
                colors = clear,
                leadingContent = { Icon(Icons.Filled.Lock, null) },
                headlineContent = { Text("Export login file") },
                supportingContent = { Text("Back up your Google sign-in, or move it to another phone with \"Import a login file\".") },
                trailingContent = { TextButton(onClick = { confirmExport = true }) { Text("Export") } },
            )
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        val bg = remember(version) { HubSettings.backgroundOn(ctx) }
        val interval = remember(version) { HubSettings.interval(ctx) }
        val last = remember(version) { HubSettings.lastCheck(ctx) }
        val lastErr = remember(version) { HubSettings.lastCheckError(ctx) }
        ListItem(
            colors = clear,
            leadingContent = { Icon(Icons.Filled.Refresh, null) },
            headlineContent = { Text("Background checks") },
            supportingContent = {
                Column {
                    Text(
                        (if (bg) "Every $interval min, even when the app is closed. " else "Off. History only grows while you use the Map tab. ") +
                            "Last check: ${if (last == 0L) "never" else formatAgo(last)}" + (lastErr?.let { " ($it)" } ?: ""),
                    )
                    if (bg) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 15, 30, 60, 120).forEach { m ->
                            FilterChip(
                                selected = interval == m,
                                onClick = { hub.setInterval(m); version++ },
                                label = { Text(if (m < 60) "$m min" else "${m / 60} h") },
                            )
                        }
                    }
                }
            },
            trailingContent = {
                Switch(checked = bg, onCheckedChange = {
                    HubSettings.setBackgroundOn(ctx, it)
                    if (it) PollWorker.restart(ctx) else PollWorker.stop(ctx)
                    version++
                })
            },
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        val moves = remember(version) { HubSettings.moveAlerts(ctx) }
        ListItem(
            colors = clear,
            leadingContent = { Icon(Icons.Filled.Warning, null) },
            headlineContent = { Text("Movement alerts") },
            supportingContent = {
                Text("Notifies you when a tag's new location is more than 150 m from its previous one. Area alerts are set per tag from the map's menu.")
            },
            trailingContent = {
                Switch(checked = moves, onCheckedChange = { on ->
                    if (on && Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        hub.setGuard(on); version++
                    }
                })
            },
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        val status = remember(version) { HubSettings.statusNotify(ctx) }
        ListItem(
            colors = clear,
            leadingContent = { Icon(Icons.Filled.Notifications, null) },
            headlineContent = { Text("Check status notification") },
            supportingContent = { Text("A quiet notification after each background check saying how many new locations were saved.") },
            trailingContent = {
                Switch(checked = status, onCheckedChange = { on ->
                    if (on && Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) statusPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    HubSettings.setStatusNotify(ctx, on); version++
                })
            },
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        val pm = ctx.getSystemService(android.os.PowerManager::class.java)
        val unrestricted = remember(version, resumeTick) { pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true }
        ListItem(
            colors = clear,
            leadingContent = { Icon(Icons.Filled.Info, null) },
            headlineContent = { Text("Unrestricted battery use") },
            supportingContent = {
                Text(
                    if (unrestricted) "On. Android won't delay background checks to save battery."
                    else "Recommended. Without it Android may delay or skip checks while the phone is idle.",
                )
            },
            trailingContent = {
                if (!unrestricted) TextButton(onClick = {
                    runCatching {
                        ctx.startActivity(
                            Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                .setData(android.net.Uri.parse("package:" + ctx.packageName)),
                        )
                    }.onFailure { ctx.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                }) { Text("Allow") }
            },
        )
    }
    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 16.dp))
    Group {
        val d = drive
        val auto = remember(version) { HubSettings.driveAuto(ctx) }
        ListItem(
            colors = clear,
            leadingContent = { Icon(Icons.Filled.Share, null) },
            headlineContent = { Text("Google Drive backup") },
            supportingContent = {
                Text(
                    buildString {
                        append("Copies the full history to \"${d?.optString("folder") ?: "TagTracker Open / history"}\" in your Drive after each check with new locations. ")
                        if (d != null) {
                            append("${d.optInt("locations")} locations stored. ")
                            val ls = d.optLong("last_sync")
                            if (ls > 0) append("Last backup ${formatAgo(ls * 1000)}. ")
                            d.optString("error").takeIf { it.isNotBlank() && it != "null" }?.let { append("Problem: $it") }
                        }
                    },
                )
            },
            trailingContent = {
                Switch(checked = auto, enabled = acct?.signedIn == true, onCheckedChange = { on ->
                    HubSettings.setDriveAuto(ctx, on)
                    scope.launch { runCatching { FindHub.setDriveAuto(ctx, on) }; version++ }
                    if (on) run("Back up now") { FindHub.driveUploadNow(ctx).replaceFirstChar { it.uppercase() } }
                })
            },
        )
        Row(Modifier.padding(start = 56.dp, bottom = 8.dp)) {
            TextButton(enabled = working == null && acct?.signedIn == true, onClick = { run("Back up now") { FindHub.driveUploadNow(ctx).replaceFirstChar { it.uppercase() } } }) { Text("Back up now") }
            TextButton(enabled = working == null && acct?.signedIn == true, onClick = {
                run("Check backup") {
                    val o = FindHub.driveVerify(ctx)
                    if (o.optBoolean("ok")) "${o.optInt("count")} file(s), ${o.optLong("bytes") / 1024} KB in ${o.optString("folder")}." else o.optString("error")
                }
            }) { Text("Check") }
            TextButton(enabled = working == null && acct?.signedIn == true, onClick = {
                run("Restore") { "Imported ${FindHub.driveRestore(ctx)} location(s) from Drive." }
            }) { Text("Restore") }
        }
        working?.let { Text("$it…", Modifier.padding(start = 72.dp, bottom = 8.dp)) }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        val fileUri = remember(version) { HubSettings.backupFileUri(ctx) }
        ListItem(
            colors = clear,
            leadingContent = { Icon(Icons.Filled.Share, null) },
            headlineContent = { Text("Backup file (alternative)") },
            supportingContent = {
                Text(
                    if (fileUri == null) "If Drive backup doesn't work with your sign-in, pick a CSV file instead, for example inside the Google Drive app."
                    else "Writing to your chosen file. Last saved ${formatAgo(HubSettings.lastFileBackup(ctx))}.",
                )
            },
            trailingContent = {
                if (fileUri == null) TextButton(onClick = { fileLauncher.launch("tracker-history.csv") }) { Text("Choose") }
                else TextButton(onClick = { HubSettings.setBackupFileUri(ctx, null); version++ }) { Text("Stop") }
            },
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        ListItem(
            colors = clear,
            leadingContent = { Icon(Icons.Filled.Refresh, null) },
            headlineContent = { Text("Import history from CSV") },
            supportingContent = { Text("Restore from a backup file or a CSV export. Locations already stored are skipped.") },
            trailingContent = {
                TextButton(enabled = acct?.signedIn == true, onClick = { importLauncher.launch(arrayOf("text/*", "application/octet-stream")) }) { Text("Import") }
            },
        )
    }

    dialog?.let { (t, m) ->
        AlertDialog(onDismissRequest = { dialog = null }, title = { Text(t) }, text = { Text(m) },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("OK") } })
    }
    if (confirmExport) {
        AlertDialog(
            onDismissRequest = { confirmExport = false },
            icon = { Icon(Icons.Filled.Warning, null) },
            title = { Text("Keep this file private") },
            text = {
                Text(
                    "The login file holds your Google tokens and encryption key. Anyone who has it can see your " +
                        "tags' locations and use Find Hub as you, without your password. Don't email it, post it or " +
                        "leave it in a shared folder. If it leaks, sign out here and remove this device from your " +
                        "Google account's security page.",
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { confirmExport = false; saveLoginLauncher.launch("tracker-finder-login.json") }) { Text("Save to Files") }
                    TextButton(onClick = {
                        confirmExport = false
                        try {
                            val dir = java.io.File(ctx.cacheDir, "exports").apply { mkdirs() }
                            val f = java.io.File(dir, "tracker-finder-login.json")
                            FindHub.secretsFile(ctx).copyTo(f, overwrite = true)
                            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                            ctx.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                    "Share login file",
                                ),
                            )
                        } catch (e: Exception) {
                            dialog = "Export login file" to FindHub.friendly(e)
                        }
                    }) { Text("Share") }
                }
            },
            dismissButton = { TextButton(onClick = { confirmExport = false }) { Text("Cancel") } },
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out of Find Hub?") },
            text = { Text("This removes the Google tokens from this phone and stops background checks. Your saved history stays.") },
            confirmButton = { TextButton(onClick = { hub.signOut(); confirmSignOut = false }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
}
