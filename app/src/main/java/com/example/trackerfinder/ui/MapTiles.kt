package com.example.trackerfinder.ui

import android.content.Context
import com.example.trackerfinder.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil

enum class MapProvider(val label: String, val note: String) {
    OPENFREEMAP("OpenFreeMap", "Clean, sharp map with a proper dark version. Free, no account. The default."),
    OSM("OpenStreetMap", "The classic OpenStreetMap look. Dark mode just inverts its colours."),
    ESRI("Esri", "Backup option if the others won't load."),
}

/**
 * Base maps, all keyless, drawn by MapLibre.
 *
 * - OpenFreeMap (openfreemap.org): vector tiles with ready-made styles. "liberty" is a bright,
 *   colourful street map; "dark" is its night version. Free with no key or limits; attribution to
 *   OpenFreeMap, OpenMapTiles and OpenStreetMap must stay visible (see [attribution]).
 * - OpenStreetMap: raster tiles. Their policy (operations.osmfoundation.org/policies/tiles) asks
 *   for a unique User-Agent, at most 2 connections, no pre-fetching and visible attribution.
 * - Esri: raster tiles; ArcGIS URLs are /tile/{z}/{y}/{x} (row before column).
 *
 * The choice is saved in prefs `settings` under `map_style` and read when the app starts.
 */
object MapTiles {
    /** Unique, stable app identity (no library default, no personal data), as tile policies require. */
    val USER_AGENT = "TrackerFinder/${BuildConfig.VERSION_NAME} (personal Android tracker app)"

    private const val KEY = "map_style"
    val provider = MutableStateFlow(MapProvider.OPENFREEMAP)
    private var started = false

    /** Called from MainActivity.onCreate before anything draws a map. Safe to call repeatedly. */
    fun init(ctx: Context) {
        provider.value = load(ctx)
        if (!started) {
            started = true
            MapLibre.getInstance(ctx.applicationContext)
        }
        applyHttp(provider.value)
    }

    /** Read straight from disk every time, so the saved choice always wins over any in-memory default. */
    fun load(ctx: Context): MapProvider {
        val v = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getString(KEY, null)
        return MapProvider.entries.firstOrNull { it.name == v } ?: MapProvider.OPENFREEMAP
    }

    fun set(ctx: Context, p: MapProvider) {
        // commit() (not apply()) so the choice is on disk even if the app is swiped away right after.
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString(KEY, p.name).commit()
        provider.value = p
        applyHttp(p)
    }

    private fun applyHttp(p: MapProvider) {
        val client = OkHttpClient.Builder()
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = if (p == MapProvider.OSM) 2 else 6 })
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build()) }
            .build()
        HttpRequestUtil.setOkHttpClient(client)
    }

    /** Short credit line shown on every map. */
    fun attribution(p: MapProvider): String = when (p) {
        MapProvider.OPENFREEMAP -> "OpenFreeMap © OpenMapTiles · Data © OpenStreetMap"
        MapProvider.OSM -> "© OpenStreetMap contributors"
        MapProvider.ESRI -> "Tiles © Esri · HERE, Garmin, © OpenStreetMap"
    }

    fun attributionUrl(p: MapProvider): String = when (p) {
        MapProvider.OPENFREEMAP -> "https://openfreemap.org"
        MapProvider.OSM -> "https://www.openstreetmap.org/copyright"
        MapProvider.ESRI -> "https://www.esri.com"
    }

    /** OSM asks apps not to pre-fetch tiles that aren't on screen. */
    fun allowPrefetch(p: MapProvider) = p != MapProvider.OSM

    fun style(p: MapProvider, dark: Boolean): Style.Builder = when (p) {
        MapProvider.OPENFREEMAP -> Style.Builder().fromUri(
            if (dark) "https://tiles.openfreemap.org/styles/dark" else "https://tiles.openfreemap.org/styles/liberty",
        )
        MapProvider.OSM -> Style.Builder().fromJson(
            raster("https://tile.openstreetmap.org/{z}/{x}/{y}.png", attribution(p), invert = dark, bg = if (dark) "#1b1b1f" else "#f2efe9"),
        )
        MapProvider.ESRI -> Style.Builder().fromJson(
            raster(
                if (dark) "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}"
                else "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}",
                attribution(p), invert = false, bg = if (dark) "#242424" else "#f2efe9",
            ),
        )
    }

    /** A one-layer style for a raster tile server. Dark mode on OSM flips brightness and hue. */
    private fun raster(url: String, credit: String, invert: Boolean, bg: String): String {
        val paint = JSONObject()
        if (invert) {
            paint.put("raster-brightness-min", 1).put("raster-brightness-max", 0)
                .put("raster-hue-rotate", 180).put("raster-saturation", -0.3)
        }
        return JSONObject()
            .put("version", 8)
            .put("sources", JSONObject().put("base", JSONObject()
                .put("type", "raster").put("tiles", JSONArray().put(url))
                .put("tileSize", 256).put("maxzoom", 19).put("attribution", credit)))
            .put("layers", JSONArray()
                .put(JSONObject().put("id", "bg").put("type", "background").put("paint", JSONObject().put("background-color", bg)))
                .put(JSONObject().put("id", "base").put("type", "raster").put("source", "base").put("paint", paint)))
            .toString()
    }
}
