package com.example.trackerfinder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

/** Home-screen widget: up to four favorites with "In range" / "Last heard …". Tap a row to find it. */
class FavoritesWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, render(context)) }
    }

    companion object {
        private val ROWS = intArrayOf(R.id.row0, R.id.row1, R.id.row2, R.id.row3)
        private val DOTS = intArrayOf(R.id.dot0, R.id.dot1, R.id.dot2, R.id.dot3)
        private val NAMES = intArrayOf(R.id.name0, R.id.name1, R.id.name2, R.id.name3)
        private val STATUSES = intArrayOf(R.id.status0, R.id.status1, R.id.status2, R.id.status3)
        private var lastUpdate = 0L

        /** Redraws every placed widget. Throttled because scans can call this often. */
        fun updateAll(ctx: Context, force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - lastUpdate < 5_000) return
            lastUpdate = now
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, FavoritesWidget::class.java))
            if (ids.isEmpty()) return
            val views = render(ctx)
            ids.forEach { mgr.updateAppWidget(it, views) }
        }

        private fun render(ctx: Context): RemoteViews {
            val views = RemoteViews(ctx.packageName, R.layout.widget_favorites)
            val favs = FavoritesStore.load(ctx.getSharedPreferences(ScanViewModel.PREFS, Context.MODE_PRIVATE)).take(ROWS.size)
            val now = System.currentTimeMillis()

            views.setOnClickPendingIntent(R.id.widget_root, openApp(ctx, null, 0))
            views.setViewVisibility(R.id.empty, if (favs.isEmpty()) View.VISIBLE else View.GONE)

            ROWS.indices.forEach { i ->
                val f = favs.getOrNull(i)
                if (f == null) {
                    views.setViewVisibility(ROWS[i], View.GONE)
                    return@forEach
                }
                val last = maxOf(Presence.lastHeard(ctx, f.id), f.lastSeenAt)
                val inRange = last > 0 && now - last < Presence.IN_RANGE_MS
                views.setViewVisibility(ROWS[i], View.VISIBLE)
                views.setTextViewText(NAMES[i], f.nickname)
                views.setTextViewText(STATUSES[i], if (inRange) "In range" else "Last heard ${ago(now, last)}")
                views.setTextColor(
                    DOTS[i],
                    ContextCompat.getColor(ctx, if (inRange) R.color.widget_in_range else R.color.widget_away),
                )
                views.setOnClickPendingIntent(ROWS[i], openApp(ctx, f.id, i + 1))
            }
            return views
        }

        private fun openApp(ctx: Context, favoriteId: String?, requestCode: Int): PendingIntent {
            val i = Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (favoriteId != null) i.putExtra(MainActivity.EXTRA_FAVORITE_ID, favoriteId)
            return PendingIntent.getActivity(ctx, 100 + requestCode, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun ago(now: Long, t: Long): String {
            if (t <= 0) return "never"
            val s = (now - t) / 1000
            return when {
                s < 3600 -> "${(s / 60).coerceAtLeast(1)} min ago"
                s < 86_400 -> "${s / 3600} h ago"
                else -> "${s / 86_400} d ago"
            }
        }
    }
}
