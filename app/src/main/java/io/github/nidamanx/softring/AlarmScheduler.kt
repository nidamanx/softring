// SPDX-License-Identifier: AGPL-3.0-or-later

package io.github.nidamanx.softring

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Schedules a single alarm at the next start or end of the hours, or sooner to
 * retry a daytime restore that could not be done yet (see [VolumeController]
 * exitNight) or a night that started on vibrate or silent.
 * When it fires, [AlarmReceiver] applies the state and schedules the next one.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** True if exact alarms may be used (always true before Android 12). */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Replace any pending alarm with one for the next change, if there is one. */
    fun scheduleNext(now: ZonedDateTime = ZonedDateTime.now()) {
        val pending = pendingIntent()
        alarmManager.cancel(pending)

        val settings = Settings(context)
        val alarm = nextAlarm(
            now, settings.window, settings.enabled, settings.nightActive, settings.always,
            daytimeSaved = !mustSaveDaytime(settings.savedRingVolume, settings.savedNotificationVolume),
        ) ?: return

        val triggerAt = alarm.at.toInstant().toEpochMilli()
        try {
            // Android 17+: only an exact alarm may start VolumeService from the
            // background, so restore retries must be exact too
            if ((alarm.exact || VolumeService.isNeeded) && canScheduleExact()) {
                // Fires on time even in Doze, as a night-time volume change must
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                setInexact(triggerAt, pending)
            }
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call
            setInexact(triggerAt, pending)
        }
    }

    /** Fallback without the permission, and for retries: the system may delay it. */
    private fun setInexact(triggerAt: Long, pending: PendingIntent) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_TRANSITION)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** How long to wait before checking again: restore pending, or night started on vibrate. */
internal val RESTORE_RETRY: Duration = Duration.ofMinutes(15)

internal data class Alarm(val at: ZonedDateTime, val exact: Boolean)

/**
 * The next alarm, or null if none is needed. Pure logic, unit tested.
 *
 * "Always" has no transitions. Retry after [RESTORE_RETRY], unless the next
 * transition comes first, when:
 * - the night state is still active but the volume should not be lowered
 *   (outside the window, or the app disabled): the daytime volumes could not be
 *   restored yet;
 * - the night is on but no daytime volume was saved ([daytimeSaved] false): it
 *   started on vibrate or silent, and the night volume applies once the sound is back.
 * Retries need not be exact, but on Android 17+ [AlarmScheduler] makes every alarm exact.
 */
internal fun nextAlarm(
    now: ZonedDateTime,
    window: NightWindow,
    enabled: Boolean,
    nightActive: Boolean,
    always: Boolean = false,
    daytimeSaved: Boolean = true,
): Alarm? {
    val transition = if (enabled && !always) window.nextTransition(now) else null
    val lowered = window.shouldBeLowered(now, enabled, always)
    val restorePending = nightActive && !lowered
    val nightPending = nightActive && lowered && !daytimeSaved
    val retry = if (restorePending || nightPending) now.plus(RESTORE_RETRY) else null
    return when {
        retry != null && (transition == null || retry.isBefore(transition.at)) -> Alarm(retry, exact = false)
        transition != null -> Alarm(transition.at, exact = true)
        else -> null
    }
}
