// SPDX-License-Identifier: AGPL-3.0-or-later

package io.github.nidamanx.softring

import android.content.Context
import android.media.AudioManager
import android.util.Log
import java.time.ZonedDateTime

/**
 * Lowers and restores the ringtone and notification volumes.
 *
 * Rules:
 * - Only STREAM_RING and STREAM_NOTIFICATION are touched. Alarms and media never.
 * - Night volume is at least 1: volume 0 would switch the phone to vibrate or
 *   silent, which Android blocks while Do Not Disturb is active.
 * - The night never raises a volume: if the daytime volume is already lower
 *   than the night volume, it is kept (see [nightLevel]).
 * - If the phone is on vibrate or silent, it is left exactly as it is: setting
 *   a ring volume would turn the sound back on. This is read from the mute state
 *   of the ringtone stream, not from the ringer mode: while a mode (Do Not
 *   Disturb) is on, Android reports the ringer mode as silent to apps even when
 *   the mode lets calls ring, and the volume must still be lowered then.
 * - Notification is set before ring: on devices where the two streams are
 *   linked, the ringtone (calls) value wins.
 */
class VolumeController(private val context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val settings = Settings(context)

    /** Bring the system to the state required by the current time and settings. */
    fun applyCurrentState(now: ZonedDateTime = ZonedDateTime.now()) {
        val lowered = settings.window.shouldBeLowered(now, settings.enabled, settings.always)
        DebugLog.add(context) {
            "  ${if (lowered) "lower" else "restore"} (nightActive=${settings.nightActive}, ringerAudible=${isRingerAudible()})"
        }
        if (lowered) enterNight() else exitNight()
    }

    /** Start the night: save daytime volumes once, then apply the night volumes. */
    private fun enterNight() {
        if (!settings.nightActive) {
            val saved = if (isRingerAudible()) {
                Pair(audio.getStreamVolume(RING), audio.getStreamVolume(NOTIFICATION))
            } else {
                Pair(Settings.NOT_SAVED, Settings.NOT_SAVED)
            }
            settings.markNightStarted(saved.first, saved.second)
        }
        applyNightVolumes()
    }

    /** Re-apply the night volumes, e.g. after the user changed them in the app. */
    fun applyNightVolumes() {
        if (!settings.nightActive || !isRingerAudible()) return
        if (mustSaveDaytime(settings.savedRingVolume, settings.savedNotificationVolume)) {
            // The night started on vibrate or silent and the sound is back: save the
            // daytime volumes now, or the morning would have nothing to restore
            settings.markNightStarted(audio.getStreamVolume(RING), audio.getStreamVolume(NOTIFICATION))
        }
        nightLevel(settings.savedNotificationVolume, settings.nightNotificationVolume)
            ?.let { setVolume(NOTIFICATION, it) }
        nightLevel(settings.savedRingVolume, settings.nightRingVolume)
            ?.let { setVolume(RING, it) }
    }

    /**
     * End the night: restore the saved daytime volumes. If they cannot be restored
     * now, the night state and the saved volumes are kept, and [AlarmScheduler]
     * schedules a retry:
     * - on vibrate or silent: the ringer is left alone, but when the user turns the
     *   sound back on Android brings back the last level, the night one;
     * - Android did not apply them (a stream it keeps muted).
     */
    private fun exitNight() {
        if (!settings.nightActive) return
        if (isRingerAudible()) {
            // Both calls must run: no short-circuit
            val notificationDone = restore(NOTIFICATION, settings.savedNotificationVolume)
            val ringDone = restore(RING, settings.savedRingVolume)
            if (!notificationDone || !ringDone) return
        } else if (settings.savedRingVolume != Settings.NOT_SAVED ||
            settings.savedNotificationVolume != Settings.NOT_SAVED
        ) {
            return
        }
        settings.markNightEnded()
    }

    /** Sets a saved daytime level and reports whether the stream is now at that level. */
    private fun restore(stream: Int, saved: Int): Boolean {
        // A level of 0 was left untouched by the night: restoring it would set the minimum, 1
        if (saved <= 0 || audio.getStreamVolume(stream) == saved) return true
        setVolume(stream, saved)
        return audio.getStreamVolume(stream) == saved
    }

    fun maxVolume(stream: Int): Int = audio.getStreamMaxVolume(stream)

    fun currentVolume(stream: Int): Int = audio.getStreamVolume(stream)

    /**
     * False on vibrate or silent, or when a mode silences calls: then the volumes
     * are left alone. Android mutes the ringtone stream in all these cases, but
     * not during a mode that lets calls ring, whatever ringer mode it reports.
     */
    fun isRingerAudible(): Boolean = !audio.isStreamMute(RING)

    private fun setVolume(stream: Int, level: Int) {
        val clamped = level.coerceIn(MIN_VOLUME, audio.getStreamMaxVolume(stream))
        val before = audio.getStreamVolume(stream)
        try {
            audio.setStreamVolume(stream, clamped, 0)
        } catch (e: SecurityException) {
            // Do Not Disturb is active and access to it was not granted
            Log.w(TAG, "Cannot change volume of stream $stream: ${e.message}")
        }
        val after = audio.getStreamVolume(stream)
        DebugLog.add(context) {
            val name = if (stream == RING) "ring" else "notif"
            "  set $name $before→$clamped: now $after ${if (after == clamped) "OK" else "IGNORED"}"
        }
    }

    companion object {
        const val RING = AudioManager.STREAM_RING
        const val NOTIFICATION = AudioManager.STREAM_NOTIFICATION
        const val MIN_VOLUME = 1
        private const val TAG = "SoftRing"
    }
}

/** True when the night started on vibrate or silent: no daytime volume was saved. */
internal fun mustSaveDaytime(savedRing: Int, savedNotification: Int): Boolean =
    savedRing == Settings.NOT_SAVED && savedNotification == Settings.NOT_SAVED

/**
 * Night level of a stream, given its saved daytime level, or null to leave the
 * stream as it is.
 *
 * The night only lowers: a daytime level below the night setting is kept. A
 * stream muted during the day (level 0) stays muted, because the minimum night
 * level (1) would turn it on. Without a saved level the night setting applies.
 */
internal fun nightLevel(saved: Int, night: Int): Int? {
    val target = night.coerceAtLeast(VolumeController.MIN_VOLUME)
    return when {
        saved == Settings.NOT_SAVED -> target
        saved <= 0 -> null
        else -> minOf(saved, target)
    }
}
