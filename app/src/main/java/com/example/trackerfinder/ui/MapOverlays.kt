package com.example.trackerfinder.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.cos
import kotlin.math.sin

/** Tag colour on maps: warm coral, deliberately far from the blue of your phone's arrow. */
const val TagCoral: Int = 0xFFFF5A4E.toInt()

/** Our own map artwork, drawn in code (no copied icons). */
object MapArt {
    private val cache = HashMap<String, Bitmap>()

    /**
     * A tag: the "glow dot". A solid dot in [color] (coral by default, so it can never be mistaken
     * for the blue arrow that marks your phone) with a thick white border, a soft drop shadow and a
     * faint inner shine. The halo rings around it are drawn and animated by the map (TagMap).
     * Centred: anchor the middle of the bitmap on the tag's position.
     */
    fun tagPin(ctx: Context, color: Int, selected: Boolean): Bitmap {
        val key = "dot|$color|$selected"
        return cache.getOrPut(key) {
            val d = ctx.resources.displayMetrics.density
            val r = (if (selected) 9f else 7.5f) * d        // dot radius incl. white border
            val border = (if (selected) 2.6f else 2.2f) * d
            val pad = 4f * d                                 // room for the shadow
            val s = (2 * (r + pad)).toInt()
            val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val cx = s / 2f
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.WHITE
            p.setShadowLayer(2.5f * d, 0f, 1f * d, 0x66000000)
            c.drawCircle(cx, cx, r, p)
            p.clearShadowLayer()
            p.color = color
            c.drawCircle(cx, cx, r - border, p)
            // Faint shine on the upper half so it reads as a lit bead, not a flat disc.
            p.shader = android.graphics.RadialGradient(cx - (r - border) * 0.3f, cx - (r - border) * 0.35f, (r - border),
                0x55FFFFFF, 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP)
            c.drawCircle(cx, cx, r - border, p)
            p.shader = null
            bmp
        }
    }

    /**
     * Your phone: a blue navigation arrow with a white edge, pointing up (north). The map rotates
     * it to your compass heading.
     */
    fun meArrow(ctx: Context, color: Int): Bitmap = cache.getOrPut("me|$color") {
        val d = ctx.resources.displayMetrics.density
        val s = (34 * d).toInt()
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = s / 2f
        val r = s * 0.36f
        fun arrow(scale: Float) = Path().apply {
            moveTo(cx, cx - r * scale)                         // tip
            lineTo(cx + r * 0.78f * scale, cx + r * 0.82f * scale)
            lineTo(cx, cx + r * 0.42f * scale)                 // notch
            lineTo(cx - r * 0.78f * scale, cx + r * 0.82f * scale)
            close()
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Color.WHITE
        p.strokeJoin = Paint.Join.ROUND
        p.style = Paint.Style.FILL_AND_STROKE
        p.strokeWidth = 3.2f * d
        p.setShadowLayer(3f * d, 0f, 1f * d, 0x66000000)
        c.drawPath(arrow(1f), p)
        p.clearShadowLayer()
        p.style = Paint.Style.FILL
        p.strokeWidth = 0f
        p.color = color
        c.drawPath(arrow(0.86f), p)
        bmp
    }

    /** A circle of [radiusM] metres around a point, as a polygon (for areas and accuracy). */
    fun circle(lat: Double, lon: Double, radiusM: Double, steps: Int = 64): Polygon {
        val pts = ArrayList<Point>(steps + 1)
        val dLat = radiusM / 111_320.0
        val dLon = radiusM / (111_320.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.01))
        for (i in 0..steps) {
            val a = 2 * Math.PI * i / steps
            pts.add(Point.fromLngLat(lon + dLon * sin(a), lat + dLat * cos(a)))
        }
        return Polygon.fromLngLats(listOf(pts))
    }
}
