package com.example.trackerfinder.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val sections = listOf(
    "Home" to "Your dashboard: every tag's last known place, which favorites are in Bluetooth range right now, " +
        "when Find Hub last checked, and shortcuts for the things you do most.",
    "Nearby & finder" to "Lists Bluetooth devices around you. Tap one to open the hot/cold finder, which turns signal " +
        "strength into an estimated distance, with beeps, vibration and a compass mode. Star a tracker to keep it in Favorites.",
    "Map" to "Uses Google's Find Hub network: phones that pass near your tag report its location, encrypted, to Google. " +
        "This app downloads and decrypts those reports with your own keys. Locate asks for a fresh report; Ring plays the tag's sound.",
    "History" to "Each automatic check (every 10 min by default, or what you choose) saves any new locations on this phone. " +
        "Pick a range to see the path, replay it, read trip stats, or export CSV/GPX. A location only appears when a phone " +
        "passed near the tag, so gaps are normal.",
    "Alerts" to "Park & guard / movement alerts: notifies you if a tag moves more than 150 m. Area alerts: notifies you when a tag " +
        "leaves a circle you set. Left-behind alerts (Favorites): notifies you when a tracker you had nearby goes out of Bluetooth range.",
    "Find my phone" to "Teach the app your tracker's button in More → Find my phone. After that, pressing the button near the " +
        "phone makes it ring loudly, even on silent. Not every tracker sends a signal on a button press; the app tells you if yours doesn't.",
    "Quiet hours & safe places" to "Quiet hours silence alerts at night. Safe places (like home) keep left-behind alerts quiet while " +
        "your phone is there; once you leave without the tracker, you're still alerted.",
    "Battery" to "Google doesn't send tag battery levels, but Find Hub tags hide one in their Bluetooth signal. When your phone hears " +
        "the tag nearby, the app reads it with your tag's key and warns you when it's low.",
    "Backups" to "Google Drive backup copies your history into your own Drive. You can also write to a CSV file you choose, " +
        "and import a CSV back in. Export login file lets you move your sign-in to another phone; keep it private.",
    "Phone battery" to "Checks are short network calls. If Android delays them, allow unrestricted battery use in More → Find Hub.",
    "Privacy" to "Everything is stored on this phone. Your Google tokens and location history are only sent to Google " +
        "(Find Hub, and Drive if you turn backup on). Map tiles come from OpenStreetMap or Esri, which see which map area you view. " +
        "Street addresses come from your phone's built-in geocoder, which usually asks Google. " +
        "There are no ads or analytics. App lock hides the app behind a password but doesn't encrypt the files.",
    "Legal" to "For finding your own belongings only. Tracking a person without their consent is illegal in many places. " +
        "This app uses unofficial Google interfaces that can change or stop working at any time, and it isn't affiliated with Google. " +
        "Provided as-is under the GNU GPL v3.0, with no warranty.",
)

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How Tracker Finder works") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                sections.forEach { (h, body) ->
                    Text(h, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
