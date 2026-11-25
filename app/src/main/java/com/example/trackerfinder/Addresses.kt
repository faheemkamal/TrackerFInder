package com.example.trackerfinder

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Street addresses for coordinates, using Android's built-in geocoder (on most phones this asks
 * Google). Results are cached in memory and on disk by ~10 m grid cell, so each spot is looked up once.
 */
object Addresses {
    private val mem = HashMap<String, String>()
    private val gate = Semaphore(2)

    private fun key(lat: Double, lon: Double) = String.format(Locale.US, "%.4f,%.4f", lat, lon)
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("addresses", Context.MODE_PRIVATE)

    fun cached(ctx: Context, lat: Double, lon: Double): String? {
        val k = key(lat, lon)
        synchronized(mem) { mem[k] }?.let { return it }
        return prefs(ctx).getString(k, null)?.also { synchronized(mem) { mem[k] = it } }
    }

    suspend fun lookup(ctx: Context, lat: Double, lon: Double): String? {
        cached(ctx, lat, lon)?.let { return it }
        if (!Geocoder.isPresent()) return null
        val text = gate.withPermit { fetch(ctx.applicationContext, lat, lon) }?.let(::format) ?: return null
        val k = key(lat, lon)
        synchronized(mem) { mem[k] = text }
        val p = prefs(ctx)
        if (p.all.size > 2000) p.edit().clear().apply() // keep the disk cache small
        p.edit().putString(k, text).apply()
        return text
    }

    private suspend fun fetch(ctx: Context, lat: Double, lon: Double): Address? {
        val g = Geocoder(ctx, Locale.getDefault())
        return if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { c ->
                g.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(list: MutableList<Address>) { if (c.isActive) c.resume(list.firstOrNull()) }
                    override fun onError(errorMessage: String?) { if (c.isActive) c.resume(null) }
                })
            }
        } else withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            runCatching { g.getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull()
        }
    }

    /** A place found by [search]: a readable name and its coordinates. */
    data class Place(val label: String, val lat: Double, val lon: Double)

    /** Find places by name or address (forward geocoding), best matches first. Empty if none or offline. */
    suspend fun search(ctx: Context, query: String, max: Int = 5): List<Place> {
        if (query.isBlank() || !Geocoder.isPresent()) return emptyList()
        val g = Geocoder(ctx.applicationContext, Locale.getDefault())
        val list: List<Address> = if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { c ->
                g.getFromLocationName(query, max, object : Geocoder.GeocodeListener {
                    override fun onGeocode(list: MutableList<Address>) { if (c.isActive) c.resume(list.toList()) }
                    override fun onError(errorMessage: String?) { if (c.isActive) c.resume(emptyList()) }
                })
            }
        } else withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            runCatching { g.getFromLocationName(query, max)?.toList() }.getOrNull() ?: emptyList()
        }
        return list.filter { it.hasLatitude() && it.hasLongitude() }
            .map { Place(it.getAddressLine(0)?.takeIf { s -> s.isNotBlank() } ?: format(it), it.latitude, it.longitude) }
    }

    /** Short, readable form: "12 Road Name, Area" rather than the full postal address. */
    private fun format(a: Address): String {
        val street = listOfNotNull(a.subThoroughfare, a.thoroughfare).joinToString(" ").ifBlank { null }
        val parts = listOfNotNull(street ?: a.featureName, a.subLocality, a.locality ?: a.subAdminArea).distinct()
        return parts.joinToString(", ").ifBlank { a.getAddressLine(0) ?: "" }
    }
}

/** The address for a spot, filled in once looked up (null until then, or if there is none). */
@Composable
fun rememberAddress(lat: Double?, lon: Double?): State<String?> {
    val ctx = LocalContext.current
    val state = remember(lat, lon) { mutableStateOf(if (lat != null && lon != null) Addresses.cached(ctx, lat, lon) else null) }
    LaunchedEffect(lat, lon) {
        if (lat != null && lon != null && state.value == null) state.value = runCatching { Addresses.lookup(ctx, lat, lon) }.getOrNull()
    }
    return state
}
