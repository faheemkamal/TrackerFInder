package com.example.trackerfinder.findhub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "Ring tag" button on alert notifications. */
class RingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val msg = try {
                FindHub.ring(app, id, true); "Ringing the tag…"
            } catch (e: Exception) {
                "Couldn't ring: " + FindHub.friendly(e)
            }
            withContext(Dispatchers.Main) { Toast.makeText(app, msg, Toast.LENGTH_LONG).show() }
            pending.finish()
        }
    }

    companion object {
        const val EXTRA_ID = "id"
    }
}
