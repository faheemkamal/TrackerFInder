package com.example.trackerfinder

import android.content.Context

/**
 * When each favorite (or a look-alike of it) was last heard, in wall-clock millis.
 * Written by the app while open and by the background monitor; read by the widget and alerts.
 */
object Presence {
    private const val PREFS = "presence"
    private const val WRITE_EVERY_MS = 30_000L
    private val lastWrite = HashMap<String, Long>()

    /** "In range" if heard within this window. */
    const val IN_RANGE_MS = 2 * 60_000L

    @Synchronized
    fun heard(ctx: Context, favoriteId: String, now: Long = System.currentTimeMillis()): Boolean {
        val prev = lastWrite[favoriteId] ?: 0
        if (now - prev < WRITE_EVERY_MS) return false
        lastWrite[favoriteId] = now
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(favoriteId, now).apply()
        return true
    }

    fun lastHeard(ctx: Context, favoriteId: String): Long =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(favoriteId, 0)

    fun forget(ctx: Context, favoriteId: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(favoriteId).apply()
    }
}
