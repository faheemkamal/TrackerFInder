package com.example.trackerfinder.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.os.SystemClock
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/** Our own map artwork: a round badge with the tag's initial on a short stem, drawn in code. */
object MapArt {
    private val cache = HashMap<String, BitmapDrawable>()

    fun tagPin(ctx: Context, name: String, color: Int, selected: Boolean): BitmapDrawable {
        val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "•"
        val key = "$initial|$color|$selected"
        return cache.getOrPut(key) {
            val d = ctx.resources.displayMetrics.density
            val r = (if (selected) 22f else 17f) * d          // badge radius
            val ring = 3f * d
            val stem = 9f * d
            val pad = 6f * d                                 // room for the shadow
            val w = (2 * (r + pad)).toInt()
            val h = (2 * r + stem + 2 * pad).toInt()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val cx = w / 2f
            val cy = pad + r
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            // Stem + white disc with a soft shadow.
            p.color = Color.WHITE
            p.setShadowLayer(4f * d, 0f, 1.5f * d, 0x55000000)
            val tail = Path().apply {
                moveTo(cx - r * 0.42f, cy + r * 0.8f)
                lineTo(cx, cy + r + stem)
                lineTo(cx + r * 0.42f, cy + r * 0.8f)
                close()
            }
            c.drawPath(tail, p)
            c.drawCircle(cx, cy, r, p)
            p.clearShadowLayer()
            // Coloured face.
            p.color = color
            c.drawCircle(cx, cy, r - ring, p)
            // Initial.
            p.color = Color.WHITE
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textAlign = Paint.Align.CENTER
            p.textSize = r * 0.95f
            val fm = p.fontMetrics
            c.drawText(initial, cx, cy - (fm.ascent + fm.descent) / 2, p)
            BitmapDrawable(ctx.resources, bmp)
        }
    }

    /** Anchor (0..1) of the stem tip, for Marker.setAnchor. */
    fun pinAnchorY(ctx: Context, selected: Boolean): Float {
        val b = tagPin(ctx, "x", 0, selected).bitmap
        val d = ctx.resources.displayMetrics.density
        return (b.height - 6f * d) / b.height
    }

    /** A tiny phone: white outline, coloured body, light screen and a speaker slit. */
    fun meDot(ctx: Context, color: Int): Bitmap {
        val d = ctx.resources.displayMetrics.density
        val w = (18 * d).toInt()
        val h = (28 * d).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val m = 2.5f * d
        val body = RectF(m, m, w - m, h - m)
        p.color = Color.WHITE
        p.setShadowLayer(2.5f * d, 0f, 0.8f * d, 0x66000000)
        c.drawRoundRect(body, 4f * d, 4f * d, p)
        p.clearShadowLayer()
        val inner = RectF(body.left + 1.6f * d, body.top + 1.6f * d, body.right - 1.6f * d, body.bottom - 1.6f * d)
        p.color = color
        c.drawRoundRect(inner, 3f * d, 3f * d, p)
        p.color = 0xCCFFFFFF.toInt()
        c.drawRoundRect(RectF(inner.left + 1.6f * d, inner.top + 3.4f * d, inner.right - 1.6f * d, inner.bottom - 3.4f * d), 1.2f * d, 1.2f * d, p)
        p.color = Color.WHITE
        c.drawRoundRect(RectF(w / 2f - 2f * d, inner.top + 1.2f * d, w / 2f + 2f * d, inner.top + 2f * d), d, d, p)
        return bmp
    }

    /** Dot plus a soft cone pointing up (the overlay rotates it to the heading). */
    fun meHeading(ctx: Context, color: Int): Bitmap {
        val d = ctx.resources.displayMetrics.density
        val s = (72 * d).toInt()
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = s / 2f
        p.shader = android.graphics.RadialGradient(cx, cx, s / 2f, (color and 0x00FFFFFF) or 0x77000000, color and 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP)
        c.drawArc(RectF(0f, 0f, s.toFloat(), s.toFloat()), -90f - 30f, 60f, true, p)
        p.shader = null
        val dot = meDot(ctx, color)
        c.drawBitmap(dot, cx - dot.width / 2f, cx - dot.height / 2f, null)
        return bmp
    }
}

/** Gentle expanding rings under the selected tag. Only animates while the map is on screen. */
class PulseOverlay(private val color: Int) : Overlay() {
    var point: GeoPoint? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val start = SystemClock.uptimeMillis()

    override fun draw(c: Canvas, map: MapView, shadow: Boolean) {
        if (shadow) return
        val gp = point ?: return
        val px = map.projection.toPixels(gp, null)
        val d = map.context.resources.displayMetrics.density
        val period = 2200f
        for (k in 0..1) {
            val t = (((SystemClock.uptimeMillis() - start) + k * period / 2) % period) / period
            paint.color = color
            paint.alpha = ((1f - t) * 90).toInt()
            c.drawCircle(px.x.toFloat(), px.y.toFloat(), (10 + 34 * t) * d, paint)
        }
        map.postInvalidateDelayed(40)
    }
}

/**
 * Your phone on the map: blue dot, accuracy circle, and a cone that follows the compass.
 * Reports each fix so the sheet can show "x km away".
 */
class MeOverlay(ctx: Context, map: MapView, color: Int, private val onFix: (GeoPoint) -> Unit) :
    MyLocationNewOverlay(GpsMyLocationProvider(ctx), map) {
    private val compass = InternalCompassOrientationProvider(ctx)
    private var last: Location? = null
    private var lastSource: IMyLocationProvider? = null
    private var heading = Float.NaN
    private var lastPush = 0L

    init {
        val dot = MapArt.meDot(ctx, color)
        setDirectionArrow(dot, MapArt.meHeading(ctx, color))
        setPersonAnchor(0.5f, 0.5f)
        setDirectionAnchor(0.5f, 0.5f)
        isDrawAccuracyEnabled = true
        mCirclePaint.color = (color and 0x00FFFFFF) or 0x22000000
    }

    override fun onLocationChanged(location: Location?, source: IMyLocationProvider?) {
        if (location == null) return
        last = Location(location); lastSource = source
        super.onLocationChanged(withHeading(location), source)
        onFix(GeoPoint(location.latitude, location.longitude))
    }

    private fun withHeading(l: Location) = Location(l).apply { if (!heading.isNaN()) bearing = heading }

    fun start(): Boolean {
        val ok = enableMyLocation()
        compass.startOrientationProvider { az, _ ->
            heading = az
            val now = SystemClock.uptimeMillis()
            val l = last
            if (l != null && now - lastPush > 150) {
                lastPush = now
                super.onLocationChanged(withHeading(l), lastSource)
            }
        }
        return ok
    }

    fun stop() {
        compass.stopOrientationProvider()
        disableMyLocation()
    }
}

/**
 * Heatmap: each saved location adds a soft warm blob; where the tag spends time the blobs pile up
 * and turn deep red. Blob size scales with zoom so it reads well from street to city level.
 */
class HeatOverlay(private val points: List<GeoPoint>) : Overlay() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val core = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(c: Canvas, map: MapView, shadow: Boolean) {
        if (shadow || points.isEmpty()) return
        val d = map.context.resources.displayMetrics.density
        val radius = (18f + (map.zoomLevelDouble.toFloat() - 12f).coerceIn(0f, 6f) * 4f) * d
        val p = android.graphics.Point()
        // Fewer, stronger blobs when there are few points; lighter ones when there are thousands.
        val alpha = (900 / points.size.coerceAtLeast(6)).coerceIn(18, 150)
        for (g in points) {
            map.projection.toPixels(g, p)
            paint.shader = android.graphics.RadialGradient(
                p.x.toFloat(), p.y.toFloat(), radius,
                intArrayOf(Color.argb(alpha, 230, 40, 20), Color.argb(alpha / 2, 255, 150, 0), Color.argb(0, 255, 200, 0)),
                floatArrayOf(0f, 0.5f, 1f), android.graphics.Shader.TileMode.CLAMP,
            )
            c.drawCircle(p.x.toFloat(), p.y.toFloat(), radius, paint)
        }
        core.color = Color.argb(160, 200, 20, 10)
    }
}
