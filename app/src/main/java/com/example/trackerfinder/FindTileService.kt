package com.example.trackerfinder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: one tap opens the hot/cold finder for your first saved tracker. */
class FindTileService : TileService() {
    override fun onStartListening() {
        val favs = FavoritesStore.load(getSharedPreferences(ScanViewModel.PREFS, Context.MODE_PRIVATE))
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_label)
            if (Build.VERSION.SDK_INT >= 29) subtitle = favs.firstOrNull()?.nickname ?: "Open app"
            updateTile()
        }
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated") // the Intent form is the only one before Android 14
    override fun onClick() {
        val fav = FavoritesStore.load(getSharedPreferences(ScanViewModel.PREFS, Context.MODE_PRIVATE)).firstOrNull()
        val i = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        fav?.let { i.putExtra(MainActivity.EXTRA_FAVORITE_ID, it.id) }
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 400, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }
}
