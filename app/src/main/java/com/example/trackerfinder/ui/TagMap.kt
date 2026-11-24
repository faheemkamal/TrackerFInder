package com.example.trackerfinder.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trackerfinder.findhub.Geofence
import com.example.trackerfinder.findhub.HubUi
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.HeatmapLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory as PF
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.text.DateFormat
import java.util.Date

/**
 * What screens can do with a map: move the camera, and read your phone's last position.
 * MapLibre zoom is one step lower than the old osmdroid zoom for the same view (512 px tiles).
 */
class MapHandle internal constructor(private val map: MapLibreMap, private val density: Float) {
    var me: LatLng? = null
        internal set

    fun flyTo(lat: Double, lon: Double, zoom: Double = 15.5) =
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), zoom), 900)

    /** Show all [points] with some room around them; one point just flies there. */
    fun fit(points: List<LatLng>, paddingDp: Int = 72, animate: Boolean = true) {
        when {
            points.size >= 2 -> {
                val b = LatLngBounds.Builder().includes(points).build()
                val pad = (paddingDp * density).toInt()
                val u = CameraUpdateFactory.newLatLngBounds(b, pad)
                if (animate) map.animateCamera(u, 900) else map.moveCamera(u)
                if (map.cameraPosition.zoom > 17) map.moveCamera(CameraUpdateFactory.zoomTo(17.0))
            }
            points.size == 1 -> flyTo(points[0].latitude, points[0].longitude, 14.0)
        }
    }

    internal fun zoom() = map.cameraPosition.zoom
    internal fun jump(lat: Double, lon: Double, zoom: Double) =
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), zoom))
}

/**
 * The map used on the Map tab and the History page (MapLibre). Draws, bottom to top: area circle,
 * accuracy circle, heatmap or path (with replay progress), saved points, replay dot, halo rings
 * under the selected tag, your phone (blue arrow + accuracy), and the tag pins on top.
 *
 * The base map comes from [MapTiles]; it's re-read from settings each time the map is created,
 * and swapped live when you change it in More, or when the app switches light/dark.
 */
@Composable
internal fun TagMap(
    ui: HubUi,
    fence: Geofence?,
    modifier: Modifier,
    main: Boolean = false,
    showMe: Boolean = false,
    onMe: (LatLng) -> Unit = {},
    onReady: (MapHandle) -> Unit = {},
    creditPadding: PaddingValues = PaddingValues(8.dp),
    creditAlignment: Alignment = Alignment.BottomStart,
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val dark = MaterialTheme.colorScheme.background.let { (0.299 * it.red + 0.587 * it.green + 0.114 * it.blue) < 0.5 }
    val pathColor = MaterialTheme.colorScheme.primary.toArgb()
    val fenceColor = MaterialTheme.colorScheme.tertiary.toArgb()
    val meColor = MeBlue.toArgb()
    val provider by MapTiles.provider.collectAsStateWithLifecycle()

    val view = remember {
        MapTiles.init(ctx) // make sure MapLibre is ready and the saved style is loaded
        MapView(ctx, MapLibreMapOptions.createFromAttributes(ctx).textureMode(true)
            .attributionEnabled(false).logoEnabled(false).compassEnabled(false)
            .rotateGesturesEnabled(false).tiltGesturesEnabled(false)
            .camera(org.maplibre.android.camera.CameraPosition.Builder().target(LatLng(20.0, 0.0)).zoom(1.5).build()))
            .apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var handle by remember { mutableStateOf<MapHandle?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var fittedKey by remember { mutableStateOf("") }
    val onReadyNow by rememberUpdatedState(onReady)
    val onMeNow by rememberUpdatedState(onMe)
    val uiNow by rememberUpdatedState(ui)

    // Lifecycle: MapView needs start/resume/pause/stop/destroy.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, view) {
        var started = false
        var resumed = false
        fun start() { if (!started) { view.onStart(); started = true } }
        fun resume() { start(); if (!resumed) { view.onResume(); resumed = true } }
        fun pause() { if (resumed) { view.onPause(); resumed = false } }
        fun stop() { pause(); if (started) { view.onStop(); started = false } }
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_STOP -> stop()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose {
            owner.lifecycle.removeObserver(obs)
            stop()
            view.onDestroy()
        }
    }

    LaunchedEffect(view) {
        view.getMapAsync { m ->
            m.uiSettings.isAttributionEnabled = false
            m.uiSettings.isLogoEnabled = false
            m.addOnMapClickListener { ll -> describeTap(ctx, m, ll, uiNow) }
            val h = MapHandle(m, density)
            map = m
            handle = h
            onReadyNow(h)
        }
    }

    // Base map: load on start and whenever the saved choice or light/dark changes.
    LaunchedEffect(map, provider, dark) {
        val m = map ?: return@LaunchedEffect
        style = null
        m.prefetchZoomDelta = if (MapTiles.allowPrefetch(provider)) 4 else 0
        m.setStyle(MapTiles.style(provider, dark)) { s -> addLayers(s); style = s }
    }

    // Pins and phone arrow are bitmaps; re-add them if the theme colours change.
    LaunchedEffect(style, pathColor, meColor) {
        val s = style ?: return@LaunchedEffect
        s.addImage("pin-sel", MapArt.tagPin(ctx, TagCoral, true))
        s.addImage("pin-other", MapArt.tagPin(ctx, TagCoral, false))
        s.addImage("me", MapArt.meArrow(ctx, meColor))
        s.getLayer("path-all")?.setProperties(PF.lineColor(pathColor))
        s.getLayer("path-done")?.setProperties(PF.lineColor(pathColor))
        s.getLayer("pts")?.setProperties(PF.circleColor(pathColor))
        s.getLayer("replay")?.setProperties(PF.circleColor(pathColor))
        s.getLayer("pulse1")?.setProperties(PF.circleColor(TagCoral))
        s.getLayer("pulse2")?.setProperties(PF.circleColor(TagCoral))
        s.getLayer("acc-fill")?.setProperties(PF.fillColor(pathColor))
        s.getLayer("acc-line")?.setProperties(PF.lineColor(pathColor))
        s.getLayer("fence-fill")?.setProperties(PF.fillColor(fenceColor))
        s.getLayer("fence-line")?.setProperties(PF.lineColor(fenceColor))
        s.getLayer("me-fill")?.setProperties(PF.fillColor(meColor))
        s.getLayer("me-line")?.setProperties(PF.lineColor(meColor))
    }

    // Your phone: GPS fixes and compass heading.
    var meFix by remember { mutableStateOf<Location?>(null) }
    var heading by remember { mutableStateOf(Float.NaN) }
    DisposableEffect(showMe) {
        val tracker = if (showMe) MeTracker(ctx, onFix = { l ->
            meFix = l
            val p = LatLng(l.latitude, l.longitude)
            handle?.me = p
            onMeNow(p)
        }, onHeading = { heading = it }) else null
        tracker?.start()
        onDispose { tracker?.stop() }
    }
    LaunchedEffect(style, meFix, heading) {
        val s = style ?: return@LaunchedEffect
        val l = meFix
        val rot = if (!heading.isNaN()) heading else if (l?.hasBearing() == true) l.bearing else 0f
        s.put("me", l?.let { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)).apply { addNumberProperty("rot", rot) } })
        s.put("me-acc", l?.takeIf { it.hasAccuracy() && it.accuracy > 5 }?.let { Feature.fromGeometry(MapArt.circle(it.latitude, it.longitude, it.accuracy.toDouble())) })
    }

    // Tags, path, heatmap, points, area and camera.
    LaunchedEffect(style, ui, fence, main) {
        val s = style ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        val h = handle ?: return@LaunchedEffect
        val geo = if (main) emptyList() else ui.geoHistory
        val line = geo.map { Point.fromLngLat(it.lon!!, it.lat!!) }
        s.put("fence", fence?.let { Feature.fromGeometry(MapArt.circle(it.lat, it.lon, it.radiusM.toDouble())) })
        val heat = ui.showHeat && !main && geo.isNotEmpty()
        s.src("heat")?.setGeoJson(FeatureCollection.fromFeatures(if (heat) line.map { Feature.fromGeometry(it) } else emptyList()))
        if (!heat && line.size >= 2) {
            val upto = (ui.replayIndex ?: (line.size - 1)).coerceIn(0, line.size - 1)
            s.put("path-all", Feature.fromGeometry(LineString.fromLngLats(line)))
            s.put("path-done", if (upto >= 1) Feature.fromGeometry(LineString.fromLngLats(line.take(upto + 1))) else null)
            s.put("replay", if (ui.replayIndex != null) Feature.fromGeometry(line[upto]) else null)
        } else {
            s.put("path-all", null)
            s.put("path-done", null)
            s.put("replay", null)
        }
        s.src("pts")?.setGeoJson(FeatureCollection.fromFeatures(
            if (ui.showPoints && !main) geo.mapIndexed { i, p -> Feature.fromGeometry(line[i]).apply { addNumberProperty("i", i); addNumberProperty("ts", p.ts) } } else emptyList(),
        ))
        val sel = ui.selected
        s.put("acc", 
            sel?.lastLat?.let { lat -> sel.lastAccuracy?.takeIf { it > 0 }?.let { Feature.fromGeometry(MapArt.circle(lat, sel.lastLon!!, it)) } },
        )
        // Halo rings around every tag with a location (the "glow dot").
        s.src("pulse")?.setGeoJson(FeatureCollection.fromFeatures(ui.devices.mapNotNull { d ->
            val lat = d.lastLat ?: return@mapNotNull null
            Feature.fromGeometry(Point.fromLngLat(d.lastLon ?: return@mapNotNull null, lat))
        }))
        s.src("pins")?.setGeoJson(FeatureCollection.fromFeatures(ui.devices.mapNotNull { d ->
            val lat = d.lastLat ?: return@mapNotNull null
            val lon = d.lastLon ?: return@mapNotNull null
            val selected = d.id == ui.selectedId
            Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply {
                addStringProperty("id", d.id)
                addStringProperty("name", d.name)
                addStringProperty("icon", if (selected) "pin-sel" else "pin-other")
                addNumberProperty("z", if (selected) 1 else 0)
            }
        }))

        // Camera: fly to the selected tag on the main map; fit the path on the history map.
        val key = "${ui.selectedId}|${ui.range}|${ui.customStart}|${geo.size}|${if (main) sel?.lastTs else ""}"
        if (key != fittedKey) {
            if (!main && geo.size >= 2) {
                view.post { h.fit(geo.map { LatLng(it.lat!!, it.lon!!) }, 40, animate = fittedKey.isNotEmpty()) }
                fittedKey = key
            } else if (sel?.lastLat != null) {
                if (fittedKey.isEmpty()) h.jump(sel.lastLat, sel.lastLon!!, 15.5)
                else h.flyTo(sel.lastLat, sel.lastLon!!, maxOf(h.zoom(), 14.5))
                fittedKey = key
            }
        }
        m.triggerRepaint()
    }

    // Glow dot halo: two soft rings that keep spreading out from each tag (only while on screen).
    LaunchedEffect(style) {
        val s = style ?: return@LaunchedEffect
        val period = 2400f
        val start = SystemClock.uptimeMillis()
        val rings = listOf(s.getLayer("pulse1"), s.getLayer("pulse2"))
        while (true) {
            withFrameMillis { }
            val now = SystemClock.uptimeMillis() - start
            rings.forEachIndexed { k, layer ->
                val t = ((now + k * period / 2) % period) / period
                layer?.setProperties(PF.circleRadius(14f + 22f * t), PF.circleOpacity((1f - t) * 0.32f))
            }
            kotlinx.coroutines.delay(33)
        }
    }

    Box(modifier) {
        AndroidView({ view }, Modifier.matchParentSize())
        Surface(
            Modifier.align(creditAlignment).padding(creditPadding)
                .clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MapTiles.attributionUrl(provider)))) } },
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        ) {
            Text(
                MapTiles.attribution(provider),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

private fun Style.src(id: String) = getSourceAs<GeoJsonSource>(id)

/** Set a source to one feature, or clear it. */
private fun Style.put(id: String, f: Feature?) = src(id)?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(f)))

/** Our sources and layers on top of whichever base map is loaded. */
private fun addLayers(s: Style) {
    listOf("fence", "acc", "heat", "path-all", "path-done", "pts", "replay", "pulse", "me-acc", "me", "pins")
        .forEach { if (s.getSource(it) == null) s.addSource(GeoJsonSource(it)) }
    fun add(l: Layer) { if (s.getLayer(l.id) == null) s.addLayer(l) }
    add(FillLayer("fence-fill", "fence").withProperties(PF.fillOpacity(0.13f)))
    add(LineLayer("fence-line", "fence").withProperties(PF.lineWidth(2f)))
    add(FillLayer("acc-fill", "acc").withProperties(PF.fillOpacity(0.12f)))
    add(LineLayer("acc-line", "acc").withProperties(PF.lineWidth(1f), PF.lineOpacity(0.35f)))
    add(HeatmapLayer("heat", "heat").withProperties(
        PF.heatmapRadius(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(10, 14), Expression.stop(16, 34))),
        PF.heatmapIntensity(Expression.interpolate(Expression.linear(), Expression.zoom(), Expression.stop(10, 0.6), Expression.stop(16, 1.4))),
        PF.heatmapOpacity(0.85f),
        PF.heatmapColor(Expression.interpolate(Expression.linear(), Expression.heatmapDensity(),
            Expression.stop(0, Expression.rgba(255, 200, 0, 0)),
            Expression.stop(0.25, Expression.rgba(255, 190, 0, 0.45)),
            Expression.stop(0.6, Expression.rgb(255, 120, 0)),
            Expression.stop(1, Expression.rgb(210, 25, 15)))),
    ))
    val round = arrayOf(PF.lineCap(Property.LINE_CAP_ROUND), PF.lineJoin(Property.LINE_JOIN_ROUND))
    add(LineLayer("path-all", "path-all").withProperties(*round, PF.lineWidth(4f), PF.lineOpacity(0.35f)))
    add(LineLayer("path-done", "path-done").withProperties(*round, PF.lineWidth(5f)))
    add(CircleLayer("pts", "pts").withProperties(PF.circleRadius(4.5f), PF.circleStrokeColor(android.graphics.Color.WHITE), PF.circleStrokeWidth(1.5f)))
    add(CircleLayer("replay", "replay").withProperties(PF.circleRadius(8f), PF.circleStrokeColor(android.graphics.Color.WHITE), PF.circleStrokeWidth(3f)))
    add(CircleLayer("pulse1", "pulse").withProperties(PF.circleOpacity(0f), PF.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP)))
    add(CircleLayer("pulse2", "pulse").withProperties(PF.circleOpacity(0f), PF.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP)))
    add(FillLayer("me-fill", "me-acc").withProperties(PF.fillOpacity(0.13f)))
    add(LineLayer("me-line", "me-acc").withProperties(PF.lineWidth(1f), PF.lineOpacity(0.4f)))
    add(SymbolLayer("me", "me").withProperties(
        PF.iconImage("me"), PF.iconRotate(Expression.get("rot")),
        PF.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
        PF.iconAllowOverlap(true), PF.iconIgnorePlacement(true),
    ))
    add(SymbolLayer("pins", "pins").withProperties(
        PF.iconImage(Expression.get("icon")), PF.iconAnchor(Property.ICON_ANCHOR_CENTER),
        PF.iconAllowOverlap(true), PF.iconIgnorePlacement(true),
        PF.symbolSortKey(Expression.get("z")), PF.symbolZOrder(Property.SYMBOL_Z_ORDER_SOURCE),
    ))
}

/** Tap a saved point or a pin: say what it is. */
private fun describeTap(ctx: Context, m: MapLibreMap, ll: LatLng, ui: HubUi): Boolean {
    val px = m.projection.toScreenLocation(ll)
    val pad = 14f * ctx.resources.displayMetrics.density
    val box = android.graphics.RectF(px.x - pad, px.y - pad, px.x + pad, px.y + pad)
    m.queryRenderedFeatures(box, "pins").firstOrNull()?.let { f ->
        val d = ui.devices.firstOrNull { it.id == f.getStringProperty("id") } ?: return false
        Toast.makeText(ctx, d.name + (d.lastTs?.let { " · last seen " + formatAgo(it * 1000) } ?: ""), Toast.LENGTH_SHORT).show()
        return true
    }
    m.queryRenderedFeatures(box, "pts").firstOrNull()?.let { f ->
        val i = f.getNumberProperty("i")?.toInt() ?: return false
        val p = ui.geoHistory.getOrNull(i) ?: return false
        val text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(p.ts * 1000)) +
            (p.accuracy?.let { " · ±${it.toInt()} m" } ?: "")
        Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
        return true
    }
    return false
}

/** Phone position (GPS/network) and compass heading, only while a map that shows you is on screen. */
private class MeTracker(
    private val ctx: Context,
    private val onFix: (Location) -> Unit,
    private val onHeading: (Float) -> Unit,
) : LocationListener, SensorEventListener {
    private val lm = ctx.getSystemService(LocationManager::class.java)
    private val sm = ctx.getSystemService(SensorManager::class.java)
    private val rot = FloatArray(9)
    private val ori = FloatArray(3)
    private var lastHeading = Float.NaN
    private var lastPush = 0L

    @SuppressLint("MissingPermission")
    fun start() {
        val fine = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (lm != null && (fine || coarse)) {
            val providers = buildList {
                if (Build.VERSION.SDK_INT >= 31 && lm.allProviders.contains(LocationManager.FUSED_PROVIDER)) add(LocationManager.FUSED_PROVIDER)
                else {
                    if (fine && lm.allProviders.contains(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
                    if (lm.allProviders.contains(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
                }
            }
            providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }?.let(onFix)
            providers.forEach { runCatching { lm.requestLocationUpdates(it, 2000L, 2f, this, Looper.getMainLooper()) } }
        }
        sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        runCatching { lm?.removeUpdates(this) }
        sm?.unregisterListener(this)
    }

    override fun onLocationChanged(location: Location) = onFix(location)

    override fun onSensorChanged(e: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rot, e.values)
        SensorManager.getOrientation(rot, ori)
        val az = ((Math.toDegrees(ori[0].toDouble()) + 360) % 360).toFloat()
        val now = SystemClock.uptimeMillis()
        val diff = if (lastHeading.isNaN()) 360f else Math.abs(((az - lastHeading + 540) % 360) - 180)
        if (diff > 3f && now - lastPush > 120) {
            lastHeading = az; lastPush = now
            onHeading(az)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
}
