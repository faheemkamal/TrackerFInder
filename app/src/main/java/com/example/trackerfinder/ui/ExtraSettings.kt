package com.example.trackerfinder.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackerfinder.AlertRules
import com.example.trackerfinder.FindPhone
import com.example.trackerfinder.LearnState
import com.example.trackerfinder.PhoneAlarm
import com.example.trackerfinder.SafePlace
import com.example.trackerfinder.ScanViewModel

@Composable
fun FindPhoneSettings(vm: ScanViewModel) {
    val ctx = LocalContext.current
    val clear = ListItemDefaults.colors(containerColor = Color.Transparent)
    var version by remember { mutableIntStateOf(0) }
    var learning by remember { mutableStateOf(false) }
    val token = remember(version) { FindPhone.token(ctx) }
    val on = remember(version) { FindPhone.enabled(ctx) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Group {
        ListItem(
            colors = clear,
            leadingContent = { Icon(Icons.Filled.Phone, null) },
            headlineContent = { Text("Find my phone") },
            supportingContent = {
                Text(
                    if (token == null) "Press your tracker's button to make this phone ring loudly, even on silent. First, teach the app what your button press looks like."
                    else "Learned: ${FindPhone.describe(token)}. Hold the button near the phone (a few metres) and it rings for up to 45 s.",
                )
            },
            trailingContent = {
                if (token != null) Switch(checked = on, onCheckedChange = { FindPhone.setEnabled(ctx, it); version++ })
            },
        )
        Row(Modifier.padding(start = 56.dp, bottom = 8.dp)) {
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                learning = true
            }) { Text(if (token == null) "Learn my button" else "Learn again") }
            if (token != null) {
                TextButton(onClick = { PhoneAlarm.ring(ctx) }) { Text("Test ring") }
                TextButton(onClick = { FindPhone.forget(ctx); version++ }) { Text("Forget") }
            }
        }
    }
    if (learning) LearnDialog(vm) { learning = false; version++ }
}

@Composable
private fun LearnDialog(vm: ScanViewModel, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val state by FindPhone.learn.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        vm.setBoost(true)
        FindPhone.startLearning(ctx)
        onDispose { vm.setBoost(false); FindPhone.cancelLearning() }
    }
    AlertDialog(
        onDismissRequest = onDone,
        icon = { Icon(Icons.Filled.Phone, null) },
        title = { Text("Learn your tracker's button") },
        text = {
            Column {
                when (val s = state) {
                    LearnState.Baseline, LearnState.Idle -> {
                        Text("Hold the tracker next to the phone and don't press anything yet. Listening to what's normal…")
                        Spacer(Modifier.height(12.dp)); LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    is LearnState.Press -> {
                        Text("Now press the tracker's button (once or twice).", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp)); Text("${s.secondsLeft} s left")
                        Spacer(Modifier.height(12.dp)); LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    is LearnState.Found -> Text(
                        "Got it: your button sends ${FindPhone.describe(s.token)}. Find my phone is on. " +
                            "Try it: press the button again and the phone should ring (it waits 20 s after you stop a ring).",
                    )
                    LearnState.NotFound -> Text(
                        "No new signal appeared when the button was pressed. Some trackers (including many Find Hub tags) " +
                            "don't send anything the phone can hear when you press the button, so this feature can't work with them. " +
                            "You can try again with the tracker right next to the phone.",
                    )
                }
            }
        },
        confirmButton = {
            when (state) {
                LearnState.NotFound -> TextButton(onClick = { FindPhone.startLearning(ctx) }) { Text("Try again") }
                is LearnState.Found -> TextButton(onClick = onDone) { Text("Done") }
                else -> {}
            }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Close") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertRulesSettings() {
    val ctx = LocalContext.current
    val clear = ListItemDefaults.colors(containerColor = Color.Transparent)
    var version by remember { mutableIntStateOf(0) }
    val quiet = remember(version) { AlertRules.quietOn(ctx) }
    val qs = remember(version) { AlertRules.quietStart(ctx) }
    val qe = remember(version) { AlertRules.quietEnd(ctx) }
    val places = remember(version) { AlertRules.safePlaces(ctx) }
    var pick by remember { mutableStateOf<Int?>(null) } // 0 = start, 1 = end
    var addPlace by remember { mutableStateOf(false) }
    fun hm(m: Int) = String.format("%02d:%02d", m / 60, m % 60)

    Group {
        ListItem(
            colors = clear,
            leadingContent = { Icon(Icons.Filled.Notifications, null) },
            headlineContent = { Text("Quiet hours") },
            supportingContent = {
                Column {
                    Text(if (quiet) "No alerts from ${hm(qs)} to ${hm(qe)} (Find my phone still rings)." else "Off. Silence all tracker alerts at night.")
                    if (quiet) Row {
                        TextButton(onClick = { pick = 0 }) { Text("From ${hm(qs)}") }
                        TextButton(onClick = { pick = 1 }) { Text("To ${hm(qe)}") }
                    }
                }
            },
            trailingContent = { Switch(checked = quiet, onCheckedChange = { AlertRules.setQuiet(ctx, it, qs, qe); version++ }) },
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        ListItem(
            colors = clear,
            leadingContent = { Icon(Icons.Filled.Home, null) },
            headlineContent = { Text("Safe places") },
            supportingContent = {
                Text(
                    if (places.isEmpty()) "Places like home where left-behind alerts stay quiet. If you leave without the tracker, you're still alerted once you're away."
                    else "Left-behind alerts stay quiet while your phone is at one of these.",
                )
            },
            trailingContent = { TextButton(onClick = { addPlace = true }) { Text("Add here") } },
        )
        places.forEachIndexed { i, sp ->
            ListItem(
                colors = clear,
                modifier = Modifier.padding(start = 40.dp),
                headlineContent = { Text(sp.name) },
                supportingContent = { Text("Within ${sp.radiusM} m") },
                trailingContent = {
                    IconButton(onClick = { AlertRules.setSafePlaces(ctx, places.filterIndexed { j, _ -> j != i }); version++ }) {
                        Icon(Icons.Filled.Delete, "Remove ${sp.name}")
                    }
                },
            )
        }
    }

    pick?.let { which ->
        val init = if (which == 0) qs else qe
        val st = rememberTimePickerState(init / 60, init % 60, is24Hour = android.text.format.DateFormat.is24HourFormat(ctx))
        AlertDialog(
            onDismissRequest = { pick = null },
            title = { Text(if (which == 0) "Quiet from" else "Quiet until") },
            text = { TimePicker(st) },
            confirmButton = {
                TextButton(onClick = {
                    val m = st.hour * 60 + st.minute
                    AlertRules.setQuiet(ctx, true, if (which == 0) m else qs, if (which == 1) m else qe)
                    pick = null; version++
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pick = null }) { Text("Cancel") } },
        )
    }
    if (addPlace) {
        val loc = remember { AlertRules.lastPhoneLocation(ctx) }
        var name by remember { mutableStateOf("Home") }
        var radius by remember { mutableIntStateOf(150) }
        AlertDialog(
            onDismissRequest = { addPlace = false },
            title = { Text("Add a safe place") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (loc == null) Text("Your phone's location isn't known yet. Open the Map tab for a moment (it shows your phone), then try again.")
                    else {
                        Text("Uses where your phone is now (±${loc.accuracy.toInt()} m).")
                        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(100, 150, 300, 500).forEach { r -> FilterChip(selected = radius == r, onClick = { radius = r }, label = { Text("$r m") }) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = loc != null && name.isNotBlank(), onClick = {
                    AlertRules.setSafePlaces(ctx, places + SafePlace(name.trim(), loc!!.latitude, loc.longitude, radius))
                    addPlace = false; version++
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { addPlace = false }) { Text("Cancel") } },
        )
    }
}
