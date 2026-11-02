// SPDX-License-Identifier: AGPL-3.0-or-later

package io.github.nidamanx.softring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class NightWindowTest {

    private val overnight = NightWindow(LocalTime.of(22, 0), LocalTime.of(7, 0))
    private val sameDay = NightWindow(LocalTime.of(13, 0), LocalTime.of(15, 30))
    private val rome = ZoneId.of("Europe/Rome")

    private fun t(h: Int, m: Int = 0) = LocalTime.of(h, m)
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, rome)

    @Test
    fun overnightWindowContainsNightHours() {
        assertTrue(overnight.contains(t(22)))
        assertTrue(overnight.contains(t(23, 59)))
        assertTrue(overnight.contains(t(0)))
        assertTrue(overnight.contains(t(6, 59)))
    }

    @Test
    fun overnightWindowExcludesDayHoursAndEnd() {
        assertFalse(overnight.contains(t(7))) // end is exclusive
        assertFalse(overnight.contains(t(12)))
        assertFalse(overnight.contains(t(21, 59)))
    }

    @Test
    fun sameDayWindow() {
        assertTrue(sameDay.contains(t(13)))
        assertTrue(sameDay.contains(t(15, 29)))
        assertFalse(sameDay.contains(t(15, 30)))
        assertFalse(sameDay.contains(t(12, 59)))
    }

    @Test
    fun invalidWindowIsNeverActiveAndHasNoTransition() {
        val invalid = NightWindow(t(8), t(8))
        assertFalse(invalid.isValid)
        assertFalse(invalid.contains(t(8)))
        assertNull(invalid.nextTransition(at(2026, 10, 30, 12)))
    }

    @Test
    fun nextTransitionDuringDayIsTonightsStart() {
        val next = overnight.nextTransition(at(2026, 10, 30, 12))!!
        assertTrue(next.entersNight)
        assertEquals(at(2026, 10, 30, 22), next.at)
    }

    @Test
    fun nextTransitionDuringNightIsMorningEnd() {
        val beforeMidnight = overnight.nextTransition(at(2026, 10, 30, 23))!!
        assertFalse(beforeMidnight.entersNight)
        assertEquals(at(2026, 10, 31, 7), beforeMidnight.at)

        val afterMidnight = overnight.nextTransition(at(2026, 10, 31, 3))!!
        assertFalse(afterMidnight.entersNight)
        assertEquals(at(2026, 10, 31, 7), afterMidnight.at)
    }

    @Test
    fun nextTransitionExactlyAtBoundaryMovesForward() {
        // At 22:00 sharp the night has just started: next is the end
        val next = overnight.nextTransition(at(2026, 10, 30, 22))!!
        assertFalse(next.entersNight)
        assertEquals(at(2026, 10, 31, 7), next.at)
    }

    @Test
    fun daylightSavingEndKeepsLocalTime() {
        // Europe/Rome leaves DST on 2026-10-25 at 03:00 -> 02:00
        val next = overnight.nextTransition(at(2026, 10, 25, 1))!!
        assertEquals(LocalTime.of(7, 0), next.at.toLocalTime())
        assertEquals(25, next.at.dayOfMonth)
    }

    @Test
    fun startInsideDaylightSavingGapIsMovedForward() {
        // Europe/Rome enters DST on 2026-03-29: 02:00-02:59 does not exist
        val window = NightWindow(t(2, 30), t(7))
        val next = window.nextTransition(at(2026, 3, 29, 1))!!
        assertTrue(next.entersNight)
        assertEquals(LocalTime.of(3, 30), next.at.toLocalTime())
    }

    @Test
    fun shouldBeLoweredFollowsSwitchAlwaysAndWindow() {
        val noon = at(2026, 10, 30, 12)
        val night = at(2026, 10, 30, 23)
        assertFalse(overnight.shouldBeLowered(night, enabled = false, always = false))
        assertFalse(overnight.shouldBeLowered(night, enabled = false, always = true))
        assertTrue(overnight.shouldBeLowered(night, enabled = true, always = false))
        assertFalse(overnight.shouldBeLowered(noon, enabled = true, always = false))
        assertTrue(overnight.shouldBeLowered(noon, enabled = true, always = true))
        // "Always" does not need a valid window
        assertTrue(NightWindow(t(8), t(8)).shouldBeLowered(noon, enabled = true, always = true))
    }
}
