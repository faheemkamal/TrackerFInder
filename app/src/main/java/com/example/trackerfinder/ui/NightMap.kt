package com.example.trackerfinder.ui

import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory as PF
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer

/**
 * Our own night palette for OpenFreeMap: a dark blue-grey map with clearly visible grey roads
 * (brighter for bigger roads), dark teal water, muted green parks and soft grey labels, in the
 * spirit of the dark mode popular map apps use. Colours are our own; tiles and icons stay
 * OpenFreeMap's.
 *
 * It recolours the bright "liberty" style after it loads (OpenFreeMap's own "dark" style is
 * near-black with faint roads). Layers are matched by their OpenMapTiles source layer and id,
 * so it keeps working if OpenFreeMap adds or renames a layer; anything unknown gets a safe dark colour.
 */
internal object NightMap {
    private const val BG = "#1f2633"
    private const val RESIDENTIAL = "#232a37"
    private const val LANDUSE = "#242b38"
    private const val BUILDING = "#2b3240"
    private const val BUILDING_EDGE = "#343c4b"
    private const val WATER = "#0e2a33"
    private const val WATERWAY = "#164049"
    private const val PARK = "#1f3a31"
    private const val WOOD = "#1c342c"
    private const val ICE = "#2c3441"
    private const val SAND = "#2b2d2c"
    private const val CASING = "#171c25"
    private const val MOTORWAY = "#7b8291"
    private const val MAJOR = "#646b7a"
    private const val MEDIUM = "#525969"
    private const val MINOR = "#3e4554"
    private const val PATH = "#363d4a"
    private const val RAIL = "#4c525e"
    private const val BOUNDARY = "#5f6675"
    private const val HALO = "#161b24"
    private const val TEXT = "#a6adb8"
    private const val CITY_TEXT = "#d6dae0"
    private const val PLACE_TEXT = "#b8bec8"
    private const val REGION_TEXT = "#8e95a1"
    private const val ROAD_TEXT = "#9fa6b2"
    private const val WATER_TEXT = "#6f97a8"
    private const val FAINT_TEXT = "#6f7683"

    fun apply(s: Style) {
        s.layers.forEach { l -> runCatching { recolor(l) }.onFailure { android.util.Log.w("NightMap", "skip ${l.id}", it) } }
    }

    private fun recolor(l: Layer) {
        val id = l.id.lowercase()
        when (l) {
            is BackgroundLayer -> l.setProperties(PF.backgroundColor(BG))
            is RasterLayer -> l.setProperties(PF.rasterOpacity(0.12f), PF.rasterBrightnessMax(0.35f), PF.rasterSaturation(-0.6f))
            is FillExtrusionLayer -> l.setProperties(PF.fillExtrusionColor(BUILDING))
            is FillLayer -> {
                val c = fillColor(l.sourceLayer, id)
                l.setProperties(PF.fillColor(c), PF.fillOutlineColor(if (l.sourceLayer == "building") BUILDING_EDGE else c))
            }
            is LineLayer -> l.setProperties(PF.lineColor(lineColor(l.sourceLayer, id)))
            // Road shields keep their own dark-on-light text.
            is SymbolLayer -> if ("shield" !in id) l.setProperties(PF.textColor(textColor(l.sourceLayer, id)), PF.textHaloColor(HALO))
        }
    }

    private fun fillColor(src: String, id: String): String = when (src) {
        "water" -> WATER
        "park" -> PARK
        "building" -> BUILDING
        "aeroway" -> LANDUSE
        "landcover" -> when {
            "wood" in id || "forest" in id -> WOOD
            "ice" in id || "glacier" in id -> ICE
            "sand" in id || "beach" in id -> SAND
            else -> PARK
        }
        "landuse" -> when {
            "residential" in id -> RESIDENTIAL
            "cemetery" in id || "park" in id || "grass" in id -> PARK
            else -> LANDUSE
        }
        else -> when {
            "water" in id -> WATER
            "park" in id || "grass" in id -> PARK
            else -> LANDUSE
        }
    }

    private fun lineColor(src: String, id: String): String = when {
        src == "water" || src == "waterway" || "water" in id -> WATERWAY
        src == "boundary" || "boundary" in id || "admin" in id -> BOUNDARY
        src == "building" -> BUILDING_EDGE
        src == "park" || src == "landcover" || src == "landuse" -> PARK
        "casing" in id || "outline" in id -> CASING
        "ferry" in id -> WATERWAY
        "rail" in id || "transit" in id || "aerialway" in id -> RAIL
        "motorway" in id -> MOTORWAY
        "trunk" in id || "primary" in id -> MAJOR
        "secondary" in id || "tertiary" in id -> MEDIUM
        "path" in id || "pedestrian" in id || "track" in id || "footway" in id || "cycle" in id || "steps" in id -> PATH
        src == "aeroway" -> MINOR
        else -> MINOR
    }

    private fun textColor(src: String, id: String): String = when (src) {
        "place" -> when {
            "city" in id || "capital" in id -> CITY_TEXT
            "country" in id || "state" in id || "continent" in id -> REGION_TEXT
            else -> PLACE_TEXT
        }
        "transportation_name" -> ROAD_TEXT
        "water_name", "waterway" -> WATER_TEXT
        "housenumber" -> FAINT_TEXT
        else -> TEXT
    }
}
