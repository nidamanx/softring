// SPDX-License-Identifier: AGPL-3.0-or-later

package io.github.nidamanx.softring

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.AudioManager
import android.util.Log
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Diagnostic diary for debug builds, switched off by [ENABLED]: when on, the
 * last volume events are shown at the bottom of the app screen (useful on a
 * phone without adb) and sent to logcat (tag SoftRing). Release builds never
 * record anything.
 */
object DebugLog {

    /** Set to true to bring the diary back in debug builds. */
    private const val ENABLED = false

    private const val TAG = "SoftRing"
    private const val FILE_NAME = "softring_debug"
    private const val KEY_LINES = "lines"
    private const val MAX_LINES = 30
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun isEnabled(context: Context): Boolean =
        ENABLED && (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** [message] is evaluated only when the diary is on. */
    fun add(context: Context, message: () -> String) {
        if (!isEnabled(context)) return
        val text = message()
        Log.i(TAG, text)
        val prefs = prefs(context)
        val lines = (prefs.getString(KEY_LINES, "").orEmpty().lines().filter { it.isNotEmpty() } +
            "${LocalTime.now().format(TIME)} $text").takeLast(MAX_LINES)
        prefs.edit().putString(KEY_LINES, lines.joinToString("\n")).apply()
    }

    /** Newest first. */
    fun read(context: Context): String =
        prefs(context).getString(KEY_LINES, "").orEmpty().lines().reversed().joinToString("\n")

    /** Audio and Do Not Disturb state that decides whether a volume change is applied. */
    fun audioState(context: Context): String {
        val audio = context.getSystemService(AudioManager::class.java)
        val filter = context.getSystemService(NotificationManager::class.java).currentInterruptionFilter
        val mode = when (filter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> "off"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "none"
            else -> "?$filter"
        }
        val ringer = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> "normal"
            AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
            else -> "silent"
        }
        return "dnd=$mode ringer=$ringer " +
            "ring=${audio.getStreamVolume(AudioManager.STREAM_RING)}" +
            "${if (audio.isStreamMute(AudioManager.STREAM_RING)) "(muted)" else ""} " +
            "notif=${audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION)}" +
            if (audio.isStreamMute(AudioManager.STREAM_NOTIFICATION)) "(muted)" else ""
    }

    private fun prefs(context: Context) = context
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
}
