// SPDX-License-Identifier: AGPL-3.0-or-later

package io.github.nidamanx.softring

import android.content.Context
import android.text.format.DateFormat
import java.time.LocalTime
import java.util.Calendar

/** Formats a time following the user's 12/24-hour preference. */
internal fun formatTime(context: Context, time: LocalTime): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, time.hour)
        set(Calendar.MINUTE, time.minute)
    }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}
