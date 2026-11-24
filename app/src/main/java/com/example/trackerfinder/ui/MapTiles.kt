package com.example.trackerfinder.ui

import android.content.Context
import com.example.trackerfinder.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

enum class MapProvider(val label: String, val note: String) {
    OSM("OpenStreetMap", "Free, no account. The default."),
    ESRI("Esri streets", "Backup option if OpenStreetMap won't load."),
}

/**
 * Base map tiles, keyless. OpenStreetMap's own servers allow apps that identify themselves with a
 * unique User-Agent, cache tiles, show attribution and never pre-download areas
 * (operations.osmfoundation.org/policies/tiles). osmdroid caches tiles and only loads what's on screen.
 */
object MapTiles {
    /** Unique, stable app identity (no library default), as the OSM tile policy requires. */
    val USER_AGENT = "TrackerFinder/${BuildConfig.VERSION_NAME} (personal Android tracker app)"

    val provider = MutableStateFlow(MapProvider.OSM)
    private var loaded = false

    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        val v = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("map_tiles", null)
        provider.value = MapProvider.entries.firstOrNull { it.name == v } ?: MapProvider.OSM
    }

    fun set(ctx: Context, p: MapProvider) {
        provider.value = p
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("map_tiles", p.name).apply()
    }

    val osm: OnlineTileSourceBase = XYTileSource(
        "OSMStandard", 0, 19, 256, ".png",
        arrayOf("https://tile.openstreetmap.org/"),
        "© OpenStreetMap contributors",
        TileSourcePolicy(
            2, // at most 2 concurrent downloads
            TileSourcePolicy.FLAG_NO_BULK or TileSourcePolicy.FLAG_NO_PREVENTIVE or
                TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL or TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED,
        ),
    )

    /** Esri's ArcGIS tile URLs are /tile/{z}/{y}/{x} (row before column). */
    private class EsriSource(name: String, service: String) : OnlineTileSourceBase(
        name, 0, 19, 256, "",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/$service/MapServer/tile/"),
        "Tiles © Esri — Esri, HERE, Garmin, © OpenStreetMap contributors",
    ) {
        override fun getTileURLString(index: Long): String =
            baseUrl + MapTileIndex.getZoom(index) + "/" + MapTileIndex.getY(index) + "/" + MapTileIndex.getX(index)
    }

    val esriLight: OnlineTileSourceBase = EsriSource("EsriStreets", "World_Street_Map")
    val esriDark: OnlineTileSourceBase = EsriSource("EsriDarkGray", "Canvas/World_Dark_Gray_Base")

    /** Returns the tile source and whether it needs colour inversion for dark mode. */
    fun sourceFor(p: MapProvider, dark: Boolean): Pair<OnlineTileSourceBase, Boolean> = when (p) {
        MapProvider.OSM -> osm to dark
        MapProvider.ESRI -> (if (dark) esriDark else esriLight) to false
    }
}
