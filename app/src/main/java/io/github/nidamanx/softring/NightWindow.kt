// SPDX-License-Identifier: AGPL-3.0-or-later

package io.github.nidamanx.softring

import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * A daily time window, e.g. 22:00 -> 07:00. The window may cross midnight.
 * Start is inclusive, end is exclusive. Pure logic, no Android dependencies,
 * so it is fully covered by JVM unit tests.
 */
data class NightWindow(val start: LocalTime, val end: LocalTime) {

    /** A window whose start equals its end is meaningless and is never active. */
    val isValid: Boolean get() = start != end

    fun contains(time: LocalTime): Boolean = when {
        !isValid -> false
        start < end -> time >= start && time < end
        else -> time >= start || time < end // crosses midnight
    }

    fun contains(moment: ZonedDateTime): Boolean = contains(moment.toLocalTime())

    /**
     * The first start or end strictly after [now], or null for an invalid window.
     * Uses zoned date-times so daylight saving changes are handled correctly.
     */
    fun nextTransition(now: ZonedDateTime): Transition? {
        if (!isValid) return null
        val nextStart = nextOccurrence(start, now)
        val nextEnd = nextOccurrence(end, now)
        return if (nextStart.isBefore(nextEnd)) {
            Transition(nextStart, entersNight = true)
        } else {
            Transition(nextEnd, entersNight = false)
        }
    }

    data class Transition(val at: ZonedDateTime, val entersNight: Boolean)

    /** True when the volume must be lowered: the app is on, and "always" or inside the window. */
    fun shouldBeLowered(now: ZonedDateTime, enabled: Boolean, always: Boolean): Boolean =
        enabled && (always || contains(now))

    companion object {
        val DEFAULT = NightWindow(LocalTime.of(22, 0), LocalTime.of(7, 0))

        private fun nextOccurrence(time: LocalTime, now: ZonedDateTime): ZonedDateTime {
            // ZonedDateTime.with() moves a time that falls in a DST gap forward
            val today = now.with(time).withSecond(0).withNano(0)
            return if (today.isAfter(now)) today else today.plusDays(1).with(time)
        }
    }
}
