package com.example.trackerfinder

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** A place where left-behind alerts stay quiet (e.g. home, where the tracker sits in a drawer). */
data class SafePlace(val name: String, val lat: Double, val lon: Double, val radiusM: Int)

/** Quiet hours (all alerts) and safe places (left-behind alerts). */
object AlertRules {
    private fun p(ctx: Context) = ctx.getSharedPreferences("alert_rules", Context.MODE_PRIVATE)

    fun quietOn(ctx: Context) = p(ctx).getBoolean("quiet", false)
    /** Minutes after midnight. Default 23:00 to 07:00. */
    fun quietStart(ctx: Context) = p(ctx).getInt("quiet_start", 23 * 60)
    fun quietEnd(ctx: Context) = p(ctx).getInt("quiet_end", 7 * 60)
    fun setQuiet(ctx: Context, on: Boolean, start: Int, end: Int) =
        p(ctx).edit().putBoolean("quiet", on).putInt("quiet_start", start).putInt("quiet_end", end).apply()

    fun inQuietHours(ctx: Context, now: Calendar = Calendar.getInstance()): Boolean =
        quietOn(ctx) && isWithin(now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE), quietStart(ctx), quietEnd(ctx))

    /** True if [minute] falls in [start, end), where the window may wrap past midnight. */
    fun isWithin(minute: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> minute in start until end
        else -> minute >= start || minute < end
    }

    fun safePlaces(ctx: Context): List<SafePlace> = runCatching {
        val a = JSONArray(p(ctx).getString("safe", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            SafePlace(o.getString("name"), o.getDouble("lat"), o.getDouble("lon"), o.getInt("r"))
        }
    }.getOrDefault(emptyList())

    fun setSafePlaces(ctx: Context, list: List<SafePlace>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("name", it.name).put("lat", it.lat).put("lon", it.lon).put("r", it.radiusM)) }
        p(ctx).edit().putString("safe", a.toString()).apply()
    }

    /** The phone's most recent known position from any provider, if it's under 15 minutes old. */
    @SuppressLint("MissingPermission")
    fun lastPhoneLocation(ctx: Context): Location? {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        return runCatching {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }
                .filter { System.currentTimeMillis() - it.time < 15 * 60_000L }
                .maxByOrNull { it.time }
        }.getOrNull()
    }

    fun safePlaceAt(ctx: Context, loc: Location? = lastPhoneLocation(ctx)): SafePlace? {
        loc ?: return null
        return safePlaces(ctx).firstOrNull { sp ->
            val out = FloatArray(1)
            Location.distanceBetween(loc.latitude, loc.longitude, sp.lat, sp.lon, out)
            out[0] <= sp.radiusM + minOf(loc.accuracy, 100f)
        }
    }

    /** Left-behind alerts are held back during quiet hours and while the phone is at a safe place. */
    fun suppressLeftBehind(ctx: Context): Boolean = inQuietHours(ctx) || safePlaceAt(ctx) != null
}
