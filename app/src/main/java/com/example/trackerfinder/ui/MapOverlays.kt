package com.example.trackerfinder.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.cos
import kotlin.math.sin

/** Our own map artwork, drawn in code (no copied icons). */
object MapArt {
    private val cache = HashMap<String, Bitmap>()

    /**
     * A little tracker tag: a rounded-square fob in the tag's colour, a keyring loop with a hole on
     * top, a soft "button" in the middle, and a short pointer underneath whose tip marks the spot.
     * The pointer tip is at the very bottom centre of the bitmap (anchor "bottom").
     */
    fun tagPin(ctx: Context, color: Int, selected: Boolean): Bitmap {
        val key = "tag|$color|$selected"
        return cache.getOrPut(key) {
            val d = ctx.resources.displayMetrics.density
            val half = (if (selected) 19f else 15f) * d      // half the fob's width
            val ring = 2.6f * d                              // white border
            val loopR = half * 0.42f                         // keyring loop
            val holeR = loopR * 0.45f
            val stem = 8f * d
            val pad = 5f * d                                 // room for the shadow
            val w = (2 * (half + pad)).toInt()
            val top = pad + loopR * 1.1f                     // fob starts below the loop
            val h = (top + 2 * half + stem).toInt() + 1
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val cx = w / 2f
            val body = RectF(cx - half, top, cx + half, top + 2 * half)
            val corner = half * 0.5f
            val p = Paint(Paint.ANTI_ALIAS_FLAG)

            // White silhouette with a soft shadow: loop, fob and pointer.
            p.color = Color.WHITE
            p.setShadowLayer(3.5f * d, 0f, 1.2f * d, 0x55000000)
            c.drawCircle(cx, top, loopR, p)
            c.drawRoundRect(body, corner, corner, p)
            c.drawPath(Path().apply {
                moveTo(cx - half * 0.38f, body.bottom - 1f * d)
                lineTo(cx, h.toFloat())
                lineTo(cx + half * 0.38f, body.bottom - 1f * d)
                close()
            }, p)
            p.clearShadowLayer()

            // Coloured face and loop.
            p.color = color
            val face = RectF(body.left + ring, body.top + ring, body.right - ring, body.bottom - ring)
            c.drawRoundRect(face, corner - ring, corner - ring, p)
            c.drawCircle(cx, top, loopR - ring, p)

            // Keyring hole, punched right through.
            p.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            c.drawCircle(cx, top - loopR * 0.15f, holeR, p)
            p.xfermode = null

            // Soft round "button" and a little shine.
            p.color = 0x55FFFFFF
            c.drawCircle(cx, face.centerY(), half * 0.42f, p)
            p.color = 0xCCFFFFFF.toInt()
            c.drawCircle(cx, face.centerY(), half * 0.22f, p)
            p.color = 0x33FFFFFF
            c.drawRoundRect(RectF(face.left + 2f * d, face.top + 2f * d, face.right - 2f * d, face.top + face.height() * 0.34f), corner * 0.6f, corner * 0.6f, p)
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
