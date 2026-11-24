package com.example.trackerfinder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.trackerfinder.findhub.HubSettings
import com.example.trackerfinder.ui.MapArt
import com.example.trackerfinder.ui.MapTiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** Home-screen widget: your first Find Hub tag's last spot on a small map, with address and age. */
class TagWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    companion object {
        private const val ZOOM = 16

        fun hasWidgets(ctx: Context) =
            AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, TagWidget::class.java)).isNotEmpty()

        /** Redraws in the background (needs network for the map picture). Cheap no-op without widgets. */
        fun refresh(ctx: Context) {
            if (!hasWidgets(ctx)) return
            val req = OneTimeWorkRequestBuilder<TagWidgetWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("tag-widget", ExistingWorkPolicy.REPLACE, req)
        }

        internal suspend fun render(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TagWidget::class.java))
            if (ids.isEmpty()) return
            val views = RemoteViews(ctx.packageName, R.layout.widget_tag)
            val arr = HubSettings.widgetTags(ctx)
            val tag = (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull { it.has("lat") }
                ?: (if (arr.length() > 0) arr.getJSONObject(0) else null)
            val open = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (tag != null) {
                open.putExtra(MainActivity.EXTRA_HUB_DEVICE_ID, tag.getString("id"))
                views.setTextViewText(R.id.tag_name, tag.optString("name", "Tag"))
                if (tag.has("lat")) {
                    val lat = tag.getDouble("lat"); val lon = tag.getDouble("lon")
                    val addr = Addresses.lookup(ctx, lat, lon)
                    val ago = if (tag.has("ts")) "seen " + agoText(tag.getLong("ts") * 1000) else ""
                    views.setTextViewText(R.id.tag_info, listOfNotNull(addr, ago.ifBlank { null }).joinToString(" · "))
                    runCatching { mapImage(ctx, lat, lon) }.getOrNull()?.let { views.setImageViewBitmap(R.id.tag_map, it) }
                } else views.setTextViewText(R.id.tag_info, "No location yet")
            }
            views.setOnClickPendingIntent(R.id.tag_root, PendingIntent.getActivity(ctx, 300, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            ids.forEach { mgr.updateAppWidget(it, views) }
        }

        private fun agoText(t: Long): String {
            val s = (System.currentTimeMillis() - t) / 1000
            return when {
                s < 3600 -> "${(s / 60).coerceAtLeast(1)} min ago"
                s < 86_400 -> "${s / 3600} h ago"
                else -> "${s / 86_400} d ago"
            }
        }

        /** 320×200 map centred on the tag, stitched from at most 4 cached OpenStreetMap tiles. */
        private suspend fun mapImage(ctx: Context, lat: Double, lon: Double): Bitmap = withContext(Dispatchers.IO) {
            val n = (1 shl ZOOM).toDouble()
            val px = (lon + 180) / 360 * n * 256
            val latR = lat * PI / 180
            val py = (1 - ln(tan(latR) + 1 / cos(latR)) / PI) / 2 * n * 256
            val w = 320; val h = 200
            val left = px - w / 2; val top = py - h / 2
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            c.drawColor(0xFFE8E8E8.toInt())
            for (tx in floor(left / 256).toInt()..floor((left + w) / 256).toInt()) {
                for (ty in floor(top / 256).toInt()..floor((top + h) / 256).toInt()) {
                    tile(ctx, tx, ty)?.let { c.drawBitmap(it, (tx * 256 - left).toFloat(), (ty * 256 - top).toFloat(), null) }
                }
            }
            val pin = MapArt.tagPin(ctx, com.example.trackerfinder.ui.TagCoral, true)
            val scale = 0.8f
            val pw = pin.width * scale; val ph = pin.height * scale
            c.drawBitmap(Bitmap.createScaledBitmap(pin, pw.toInt(), ph.toInt(), true), w / 2f - pw / 2, h / 2f - ph / 2, Paint(Paint.FILTER_BITMAP_FLAG))
            Paint().apply { textSize = 10f; color = 0xFF555555.toInt(); isAntiAlias = true }.let { c.drawText("© OpenStreetMap", 4f, h - 4f, it) }
            out
        }

        /** One OSM tile, cached for a week (same identification and politeness as the in-app map). */
        private fun tile(ctx: Context, x: Int, y: Int): Bitmap? {
            val f = File(ctx.cacheDir, "widget-tiles/$ZOOM-$x-$y.png")
            if (f.exists() && System.currentTimeMillis() - f.lastModified() < 7 * 86_400_000L) return BitmapFactory.decodeFile(f.path)
            val conn = URL("https://tile.openstreetmap.org/$ZOOM/$x/$y.png").openConnection() as HttpURLConnection
            return try {
                conn.setRequestProperty("User-Agent", MapTiles.USER_AGENT)
                conn.connectTimeout = 10_000; conn.readTimeout = 10_000
                if (conn.responseCode != 200) return null
                val bytes = conn.inputStream.use { it.readBytes() }
                f.parentFile?.mkdirs(); f.writeBytes(bytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Exception) {
                if (f.exists()) BitmapFactory.decodeFile(f.path) else null
            } finally {
                conn.disconnect()
            }
        }
    }
}

class TagWidgetWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        runCatching { TagWidget.render(applicationContext) }
        return Result.success()
    }
}
