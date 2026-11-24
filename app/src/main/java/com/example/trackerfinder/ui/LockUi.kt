package com.example.trackerfinder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.trackerfinder.AppLock

@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    var pw by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }
    val hint = remember { AppLock.hint(ctx) }
    fun submit() { if (AppLock.check(ctx, pw)) onUnlocked() else { wrong = true; pw = "" } }
    Column(
        Modifier.fillMaxSize().imePadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Lock, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Tracker Finder is locked", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = pw,
            onValueChange = { pw = it; wrong = false },
            label = { Text("Password") },
            singleLine = true,
            isError = wrong,
            supportingText = { if (wrong) Text("Wrong password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = ::submit, enabled = pw.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Unlock") }
        if (hint != null) {
            TextButton(onClick = { showHint = !showHint }) { Text(if (showHint) "Hint: $hint" else "Show hint") }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Forgot it? Clear the app's storage in Android settings. That also removes saved trackers, history and your Find Hub sign-in.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Set, change or remove the app password. */
@Composable
fun AppLockDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val enabled = remember { AppLock.isEnabled(ctx) }
    var current by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf(AppLock.hint(ctx) ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    fun field(v: String, label: String, onChange: (String) -> Unit) = @Composable {
        OutlinedTextField(
            v, onChange, label = { Text(label) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Lock, null) },
        title = { Text(if (enabled) "Change app lock" else "Set app lock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Asks for a password when you open the app, and again after it's been in the background for a minute. " +
                        "Background checks and alerts keep working while locked.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (enabled) field(current, "Current password") { current = it; error = null }()
                field(pw, if (enabled) "New password" else "Password") { pw = it; error = null }()
                field(pw2, "Repeat password") { pw2 = it; error = null }()
                OutlinedTextField(hint, { hint = it }, label = { Text("Hint (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    enabled && !AppLock.check(ctx, current) -> "Current password is wrong"
                    pw.length < 4 -> "Use at least 4 characters"
                    pw != pw2 -> "Passwords don't match"
                    hint.isNotBlank() && hint.contains(pw) -> "The hint can't contain the password"
                    else -> null
                }
                if (error == null) { AppLock.set(ctx, pw, hint); onDismiss() }
            }) { Text("Save") }
        },
        dismissButton = {
            if (enabled) TextButton(onClick = {
                if (AppLock.check(ctx, current)) { AppLock.disable(ctx); onDismiss() } else error = "Enter your current password to turn the lock off"
            }) { Text("Turn off") }
            else TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
