package com.example.trackerfinder.findhub

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Battery level of your Find Hub tags, read from their Bluetooth signal when the phone hears them
 * (Google's location data doesn't include it). Each advert address is checked once, so this costs
 * a few milliseconds every ~15 minutes per tag while scanning.
 */
object BeaconBattery {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tried = LinkedHashSet<String>()

    fun onAdvert(ctx: Context, address: String, feaa: ByteArray) {
        if (!FindHub.isStarted || feaa.size < 21) return
        synchronized(tried) {
            if (!tried.add(address)) return
            if (tried.size > 500) tried.remove(tried.first())
        }
        val app = ctx.applicationContext
        scope.launch {
            val o = runCatching { FindHub.beaconMatch(app, feaa) }.getOrNull() ?: return@launch
            val id = o.getString("id")
            val level = o.getString("battery")
            val prev = HubSettings.battery(app, id)
            HubSettings.setBattery(app, id, level)
            if ((level == "low" || level == "critical") && (prev?.level != level || System.currentTimeMillis() - prev.notifiedAt > 24 * 3600_000L)) {
                HubNotify.battery(app, id, level)
                HubSettings.markBatteryNotified(app, id)
            }
        }
    }
}

data class TagBattery(val level: String, val at: Long, val notifiedAt: Long) {
    val label: String get() = when (level) {
        "normal" -> "Battery OK"
        "low" -> "Battery low"
        "critical" -> "Battery very low, replace soon"
        else -> "Battery level not reported by this tag"
    }
}
