// SPDX-License-Identifier: AGPL-3.0-or-later

package io.github.nidamanx.softring

import android.content.Context
import java.time.ZonedDateTime

/** Single entry point used by the UI and by all receivers. */
object Engine {

    /**
     * Apply the correct volume state for "now", schedule the next change, refresh the tile.
     * [source] only labels the event in the debug diary.
     */
    fun refresh(context: Context, source: String, now: ZonedDateTime = ZonedDateTime.now()) {
        DebugLog.add(context) { "$source: ${DebugLog.audioState(context)}" }
        VolumeController(context).applyCurrentState(now)
        AlarmScheduler(context).scheduleNext(now)
        SoftRingTileService.requestUpdate(context)
    }

    /**
     * Same as [refresh], for callers with no visible activity (alarms, system
     * events, the tile). On Android 17+ it runs in [VolumeService], otherwise
     * the volume change is silently ignored. If Android does not allow the service
     * to start, the state is left as it is (the app applies it when opened) and
     * the next alarm is still scheduled.
     */
    fun refreshFromBackground(context: Context, source: String) {
        if (!VolumeService.isNeeded) {
            refresh(context, source)
            return
        }
        if (VolumeService.start(context, source)) return
        DebugLog.add(context) { "$source: volume service NOT started" }
        AlarmScheduler(context).scheduleNext()
        SoftRingTileService.requestUpdate(context)
    }
}
