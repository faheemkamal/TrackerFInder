package com.example.trackerfinder

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A tracker the user starred, remembered across launches. */
data class Favorite(
    val id: String = UUID.randomUUID().toString(),
    val nickname: String,
    val fingerprint: String,
    val kind: TrackerKind,
    val name: String?,
    val lastAddress: String,
    val lastSeenAt: Long, // wall clock millis
    /** Notify when this tracker drops out of range (background monitor). */
    val alertWhenLeft: Boolean = false,
) {
    /** A nameless generic device has no usable signature, so only its address can match. */
    val canMatchByType: Boolean get() = kind != TrackerKind.WIFI && (kind.isTracker || name != null)

    fun matchesExactly(d: SeenDevice) = d.address == lastAddress
    fun matchesByType(d: SeenDevice) = canMatchByType && d.fingerprint == fingerprint
    fun matches(d: SeenDevice) = matchesExactly(d) || matchesByType(d)
}

/** How a favorite relates to what the phone can hear right now. */
sealed interface FavoriteStatus {
    data class Here(val device: SeenDevice) : FavoriteStatus
    data class Maybe(val strongest: SeenDevice, val count: Int) : FavoriteStatus
    data object Away : FavoriteStatus
}

fun Favorite.status(devices: Collection<SeenDevice>): FavoriteStatus {
    devices.firstOrNull { matchesExactly(it) }?.let { return FavoriteStatus.Here(it) }
    val similar = devices.filter { matchesByType(it) }
    val best = similar.maxByOrNull { it.rssi } ?: return FavoriteStatus.Away
    return FavoriteStatus.Maybe(best, similar.size)
}

/** Picks the device to open for a favorite: exact address first, then the strongest look-alike. */
fun Favorite.bestMatch(devices: Collection<SeenDevice>): SeenDevice? =
    devices.firstOrNull { matchesExactly(it) } ?: devices.filter { matchesByType(it) }.maxByOrNull { it.rssi }

object FavoritesStore {
    private const val KEY = "favorites.v1"

    fun load(prefs: SharedPreferences): List<Favorite> {
        val list = runCatching { decode(prefs.getString(KEY, null) ?: "[]") }.getOrDefault(emptyList())
        if (list.isNotEmpty()) return list
        // Migrate the single "saved tracker" from the previous version.
        val fp = prefs.getString("saved.fingerprint", null) ?: return emptyList()
        val kind = runCatching { TrackerKind.valueOf(prefs.getString("saved.kind", "")!!) }.getOrNull()
            ?: return emptyList()
        val migrated = listOf(
            Favorite(
                nickname = "My tracker", fingerprint = fp, kind = kind,
                name = prefs.getString("saved.name", null),
                lastAddress = prefs.getString("saved.address", "") ?: "", lastSeenAt = 0,
            )
        )
        save(prefs, migrated)
        return migrated
    }

    fun save(prefs: SharedPreferences, list: List<Favorite>) {
        prefs.edit().putString(KEY, encode(list)).apply()
    }

    fun encode(list: List<Favorite>): String = JSONArray().apply {
        list.forEach { f ->
            put(JSONObject().apply {
                put("id", f.id); put("nickname", f.nickname); put("fingerprint", f.fingerprint)
                put("kind", f.kind.name); put("name", f.name ?: JSONObject.NULL)
                put("lastAddress", f.lastAddress); put("lastSeenAt", f.lastSeenAt)
                put("alertWhenLeft", f.alertWhenLeft)
            })
        }
    }.toString()

    fun decode(json: String): List<Favorite> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val kind = runCatching { TrackerKind.valueOf(o.getString("kind")) }.getOrNull() ?: return@mapNotNull null
            Favorite(
                id = o.getString("id"), nickname = o.getString("nickname"),
                fingerprint = o.getString("fingerprint"), kind = kind,
                name = if (o.isNull("name")) null else o.getString("name"),
                lastAddress = o.optString("lastAddress"), lastSeenAt = o.optLong("lastSeenAt"),
                alertWhenLeft = o.optBoolean("alertWhenLeft", false),
            )
        }
    }
}
