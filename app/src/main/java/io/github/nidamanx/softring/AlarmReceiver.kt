// SPDX-License-Identifier: AGPL-3.0-or-later

package io.github.nidamanx.softring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires at the start and at the end of the hours, and for daytime restore retries. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRANSITION) return
        // Re-evaluate from the current time instead of trusting the alarm:
        // robust against late alarms and settings changed in the meantime.
        Engine.refreshFromBackground(context, "alarm")
    }

    companion object {
        const val ACTION_TRANSITION = "io.github.nidamanx.softring.action.TRANSITION"
    }
}
