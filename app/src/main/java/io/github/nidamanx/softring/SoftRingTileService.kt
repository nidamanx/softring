// SPDX-License-Identifier: AGPL-3.0-or-later

package io.github.nidamanx.softring

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import java.time.LocalTime

/**
 * Quick Settings tile: shows whether SoftRing is on (and until when, Android 10+)
 * and switches it on or off with a tap, only with the phone unlocked. No
 * permission needed, no data read.
 */
class SoftRingTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        // Only for the owner: from the lock screen the phone asks to be unlocked first
        if (isLocked) unlockAndRun { toggle() } else toggle()
    }

    private fun toggle() {
        val settings = Settings(this)
        settings.enabled = !settings.enabled
        Engine.refreshFromBackground(this, "tile")
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val settings = Settings(this)
        tile.state = if (settings.enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val window = settings.window
            tile.subtitle = when {
                !settings.enabled -> getString(R.string.tile_off)
                settings.always -> getString(R.string.tile_always)
                // Start equals end: the app screen explains it
                !window.isValid -> null
                // Not from nightActive: on Android 17 VolumeService applies the night after onClick
                window.contains(LocalTime.now()) -> getString(R.string.tile_until, formatTime(this, window.end))
                else -> getString(R.string.tile_from, formatTime(this, window.start))
            }
        }
        tile.updateTile()
    }

    companion object {
        /** Asks the system to refresh the tile, if it is in the Quick Settings panel. */
        fun requestUpdate(context: Context) {
            try {
                requestListeningState(context, ComponentName(context, SoftRingTileService::class.java))
            } catch (e: RuntimeException) {
                // Only a missed refresh: the tile updates itself when the panel opens
                Log.w("SoftRing", "Tile not refreshed: ${e.message}")
            }
        }
    }
}
