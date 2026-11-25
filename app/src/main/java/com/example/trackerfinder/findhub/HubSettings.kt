package com.example.trackerfinder.findhub

import android.content.Context
import org.json.JSONObject

/** An "area alert": notify when the tag's newest location is outside (or back inside) this circle. */
/** An area alert: a circle around a place ([label] = its address or name, if known). */
data class Geofence(val lat: Double, val lon: Double, val radiusM: Int, val wasInside: Boolean?, val label: String? = null)

/** Find Hub settings kept on this phone only. */
object HubSettings {
    private fun p(ctx: Context) = ctx.getSharedPreferences("findhub", Context.MODE_PRIVATE)

    fun backgroundOn(ctx: Context) = p(ctx).getBoolean("bg", true)
    fun setBackgroundOn(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("bg", on).apply()

    /** Minutes between background checks. */
    fun showHeat(ctx: Context) = p(ctx).getBoolean("show_heat", false)
    fun setShowHeat(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("show_heat", on).apply()
    fun showPoints(ctx: Context) = p(ctx).getBoolean("show_points", false)
    fun setShowPoints(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("show_points", on).apply()

    fun interval(ctx: Context) = p(ctx).getInt("interval", 10)
    fun setInterval(ctx: Context, m: Int) = p(ctx).edit().putInt("interval", m).apply()

    fun moveAlerts(ctx: Context) = p(ctx).getBoolean("move_alerts", false)
    fun setMoveAlerts(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("move_alerts", on).apply()
    /** How far (metres) a tag must move to trigger a movement alert: 150 m to 1 km, default 150. */
    fun moveDistance(ctx: Context) = p(ctx).getInt("move_distance", 150).coerceIn(150, 1000)
    fun setMoveDistance(ctx: Context, m: Int) = p(ctx).edit().putInt("move_distance", m.coerceIn(150, 1000)).apply()

    /** A quiet notification after each background check ("last check 10:42 · 2 new"). */
    fun statusNotify(ctx: Context) = p(ctx).getBoolean("status_notify", false)
    fun setStatusNotify(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("status_notify", on).apply()

    fun driveAuto(ctx: Context) = p(ctx).getBoolean("drive_auto", false)
    fun setDriveAuto(ctx: Context, on: Boolean) = p(ctx).edit().putBoolean("drive_auto", on).apply()

    /** Optional: a CSV file picked through the system file picker (e.g. in the Drive app). */
    fun backupFileUri(ctx: Context): String? = p(ctx).getString("saf_uri", null)
    fun setBackupFileUri(ctx: Context, uri: String?) = p(ctx).edit().putString("saf_uri", uri).apply()
    fun lastFileBackup(ctx: Context) = p(ctx).getLong("saf_last", 0)
    fun setLastFileBackup(ctx: Context, t: Long) = p(ctx).edit().putLong("saf_last", t).apply()

    fun lastCheck(ctx: Context) = p(ctx).getLong("last_check", 0)
    fun lastCheckError(ctx: Context): String? = p(ctx).getString("last_check_error", null)
    fun recordCheck(ctx: Context, error: String?) =
        p(ctx).edit().putLong("last_check", System.currentTimeMillis()).putString("last_check_error", error).apply()

    /** Links a Find Hub tag to a favorite in the nearby finder. */
    fun linkedFavorite(ctx: Context, deviceId: String): String? = p(ctx).getString("link.$deviceId", null)
    fun linkFavorite(ctx: Context, deviceId: String, favoriteId: String?) =
        p(ctx).edit().apply { if (favoriteId == null) remove("link.$deviceId") else putString("link.$deviceId", favoriteId) }.apply()

    fun geofence(ctx: Context, deviceId: String): Geofence? {
        val s = p(ctx).getString("fence.$deviceId", null) ?: return null
        return runCatching {
            val o = JSONObject(s)
            Geofence(o.getDouble("lat"), o.getDouble("lon"), o.getInt("r"),
                if (o.has("in")) o.getBoolean("in") else null,
                o.optString("label").ifBlank { null })
        }.getOrNull()
    }

    fun setGeofence(ctx: Context, deviceId: String, g: Geofence?) {
        val e = p(ctx).edit()
        if (g == null) e.remove("fence.$deviceId") else e.putString("fence.$deviceId", JSONObject().apply {
            put("lat", g.lat); put("lon", g.lon); put("r", g.radiusM)
            g.wasInside?.let { put("in", it) }
            g.label?.let { put("label", it) }
        }.toString())
        e.apply()
    }

    /** Last battery level read from the tag's Bluetooth signal (see BeaconBattery). */
    fun battery(ctx: Context, deviceId: String): TagBattery? {
        val lv = p(ctx).getString("batt.$deviceId", null) ?: return null
        return TagBattery(lv, p(ctx).getLong("batt_at.$deviceId", 0), p(ctx).getLong("batt_n.$deviceId", 0))
    }
    fun setBattery(ctx: Context, deviceId: String, level: String) =
        p(ctx).edit().putString("batt.$deviceId", level).putLong("batt_at.$deviceId", System.currentTimeMillis()).apply()
    fun markBatteryNotified(ctx: Context, deviceId: String) =
        p(ctx).edit().putLong("batt_n.$deviceId", System.currentTimeMillis()).apply()

    /** Last known spot of each tag, for the map widget (no engine start needed to draw it). */
    fun saveWidgetTags(ctx: Context, devices: List<HubDevice>) {
        val arr = org.json.JSONArray()
        devices.forEach { d ->
            arr.put(JSONObject().apply {
                put("id", d.id); put("name", d.name)
                d.lastLat?.let { put("lat", it) }; d.lastLon?.let { put("lon", it) }
                d.lastTs?.let { put("ts", it) }; d.lastAccuracy?.let { put("acc", it) }
            })
        }
        p(ctx).edit().putString("widget_tags", arr.toString()).apply()
    }
    fun widgetTags(ctx: Context): org.json.JSONArray =
        runCatching { org.json.JSONArray(p(ctx).getString("widget_tags", "[]")) }.getOrDefault(org.json.JSONArray())
}
