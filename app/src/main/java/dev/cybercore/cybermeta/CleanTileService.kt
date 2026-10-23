package dev.cybercore.cybermeta

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tile to clean the most recent photo in one tap.
 */
class CleanTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { tile ->
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.tile_clean_label)
            tile.subtitle = getString(R.string.app_name)
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_CLEAN_LATEST
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("StartActivityAndCollapseDeprecated", "DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    companion object {
        const val ACTION_CLEAN_LATEST = "dev.cybercore.cybermeta.CLEAN_LATEST"
    }
}
