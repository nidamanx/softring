// SPDX-License-Identifier: AGPL-3.0-or-later

package io.github.nidamanx.softring

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Reboot, app update, clock and time zone changes, exact-alarm permission
 * changes: in all these cases alarms may be lost or wrong, so the state is
 * re-applied and the next alarm rescheduled.
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action in HANDLED_ACTIONS) {
            Engine.refreshFromBackground(context, action.substringAfterLast('.'))
        }
    }

    private companion object {
        // Only action names: before Android 12 that broadcast is never sent, nothing breaks
        @SuppressLint("InlinedApi")
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
