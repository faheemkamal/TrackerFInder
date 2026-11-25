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
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString(KEY, p.name).apply()
        provider.value = p
        applyHttp(p)
    }

    private var httpFor: Int = -1

    private fun applyHttp(p: MapProvider) {
        // One client per connection policy; making a new one for every map leaked thread pools.
        val perHost = if (p == MapProvider.OSM) 2 else 6
        if (perHost == httpFor) return
        httpFor = perHost
        val client = OkHttpClient.Builder()
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = perHost })
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

    /** Called once a style has loaded, before our layers go on top. */
    fun styleLoaded(s: Style, p: MapProvider, dark: Boolean) {
        if (dark && p == MapProvider.OPENFREEMAP) NightMap.apply(s)
    }

    /** OSM asks apps not to pre-fetch tiles that aren't on screen. */
    fun allowPrefetch(p: MapProvider) = p != MapProvider.OSM

    fun style(p: MapProvider, dark: Boolean): Style.Builder = when (p) {
        MapProvider.OPENFREEMAP -> Style.Builder().fromUri(
            // Dark mode loads the same detailed "liberty" style and recolours it (see NightMap).
            "https://tiles.openfreemap.org/styles/liberty",
        )
        MapProvider.OSM -> Style.Builder().fromJson(
            raster("https://tile.openstreetmap.org/{z}/{x}/{y}.png", attribution(p), invert = dark, bg = if (dark) "#1b1b1f" else "#f2efe9"),
        )
        MapProvider.ESRI -> Style.Builder().fromJson(
            if (dark) raster(
                // Esri's dark canvas only has tiles up to zoom 16 (deeper it sends "Map data not yet
                // available" images), so MapLibre enlarges zoom-16 tiles beyond that. Street and place
                // names come from its matching label layer on top.
                "$ESRI/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}",
                attribution(p), invert = false, bg = "#242424", maxZoom = 16,
                labels = "$ESRI/Canvas/World_Dark_Gray_Reference/MapServer/tile/{z}/{y}/{x}",
            ) else raster(
                "$ESRI/World_Street_Map/MapServer/tile/{z}/{y}/{x}",
                attribution(p), invert = false, bg = "#f2efe9",
            ),
        )
    }

    private const val ESRI = "https://server.arcgisonline.com/ArcGIS/rest/services"

    /**
     * A one-layer style for a raster tile server (plus an optional label layer on top). Dark mode on
     * OSM flips brightness and hue. [maxZoom] is the deepest zoom the server has tiles for.
     */
    private fun raster(url: String, credit: String, invert: Boolean, bg: String, maxZoom: Int = 19, labels: String? = null): String {
        val paint = JSONObject()
        if (invert) {
            paint.put("raster-brightness-min", 1).put("raster-brightness-max", 0)
                .put("raster-hue-rotate", 180).put("raster-saturation", -0.3)
        }
        fun source(u: String) = JSONObject().put("type", "raster").put("tiles", JSONArray().put(u))
            .put("tileSize", 256).put("maxzoom", maxZoom).put("attribution", credit)
        val sources = JSONObject().put("base", source(url))
        val layers = JSONArray()
            .put(JSONObject().put("id", "bg").put("type", "background").put("paint", JSONObject().put("background-color", bg)))
            .put(JSONObject().put("id", "base").put("type", "raster").put("source", "base").put("paint", paint))
        if (labels != null) {
            sources.put("labels", source(labels))
            layers.put(JSONObject().put("id", "labels").put("type", "raster").put("source", "labels"))
        }
        return JSONObject().put("version", 8).put("sources", sources).put("layers", layers).toString()
    }
}
