package com.example.trackerfinder

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import org.json.JSONArray

/** One spot where the phone heard a favorite tag over Bluetooth. [at] is wall-clock millis. */
data class Crumb(val lat: Double, val lon: Double, val at: Long, val accM: Float)

/**
 * Bluetooth breadcrumbs: where the phone was when it last heard each favorite tag over Bluetooth,
 * shown on the map. Recorded whenever [Presence] notes a favorite (at most every 10 s), from the
 * phone's last known position. That's passive: it never turns on GPS itself, so a spot is only
 * saved when some app (or our map) recently got a fresh fix. Prefs `breadcrumbs`: one JSON array
 * per favorite id, newest last, [lat, lon, at, acc].
 */
object Breadcrumbs {
    private const val PREFS = "breadcrumbs"
    private const val KEEP = 40
    /** Closer than this to the last spot: just refresh its time instead of adding a new one. */
    private const val SAME_SPOT_M = 30f
    private const val MAX_FIX_AGE_MS = 2 * 60_000L
    private const val MAX_ACCURACY_M = 150f

    @SuppressLint("MissingPermission")
    fun record(ctx: Context, favoriteId: String, now: Long = System.currentTimeMillis()) {
        val fix = freshFix(ctx) ?: return
        val list = load(ctx, favoriteId).toMutableList()
        val last = list.lastOrNull()
        val d = FloatArray(1)
        if (last != null) Location.distanceBetween(last.lat, last.lon, fix.latitude, fix.longitude, d)
        val crumb = Crumb(fix.latitude, fix.longitude, now, if (fix.hasAccuracy()) fix.accuracy else 0f)
        if (last != null && d[0] < SAME_SPOT_M) {
            // Same place: keep the more accurate position, update the time.
            list[list.lastIndex] = if (crumb.accM > 0f && crumb.accM < last.accM) crumb else last.copy(at = now)
        } else {
            list += crumb
        }
        val arr = JSONArray()
        list.takeLast(KEEP).forEach { arr.put(JSONArray().put(it.lat).put(it.lon).put(it.at).put(it.accM.toDouble())) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(favoriteId, arr.toString()).apply()
    }

    fun load(ctx: Context, favoriteId: String): List<Crumb> {
        val s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(favoriteId, null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(s)
            (0 until a.length()).map { i ->
                val c = a.getJSONArray(i)
                Crumb(c.getDouble(0), c.getDouble(1), c.getLong(2), c.optDouble(3, 0.0).toFloat())
            }
        }.getOrDefault(emptyList())
    }

    fun forget(ctx: Context, favoriteId: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(favoriteId).apply()
    }

    /** The newest reasonably accurate position any provider has, if it's recent; never starts GPS. */
    @SuppressLint("MissingPermission")
    private fun freshFix(ctx: Context): Location? {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val nowRt = SystemClock.elapsedRealtimeNanos()
        return runCatching { lm.allProviders }.getOrDefault(emptyList())
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { (nowRt - it.elapsedRealtimeNanos) / 1_000_000 < MAX_FIX_AGE_MS && (!it.hasAccuracy() || it.accuracy <= MAX_ACCURACY_M) }
            .maxByOrNull { it.elapsedRealtimeNanos }
    }
}
