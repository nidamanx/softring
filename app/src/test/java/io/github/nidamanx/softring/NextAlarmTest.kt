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

class NextAlarmTest {

    private val overnight = NightWindow(LocalTime.of(22, 0), LocalTime.of(7, 0))
    private val rome = ZoneId.of("Europe/Rome")

    private fun at(d: Int, h: Int, mi: Int = 0) = ZonedDateTime.of(2026, 10, d, h, mi, 0, 0, rome)

    @Test
    fun dayWithoutPendingRestoreWaitsForTheNightExactly() {
        val alarm = nextAlarm(at(30, 12), overnight, enabled = true, nightActive = false)!!
        assertEquals(at(30, 22), alarm.at)
        assertTrue(alarm.exact)
    }

    @Test
    fun nightInProgressWaitsForTheMorningExactly() {
        val alarm = nextAlarm(at(30, 23), overnight, enabled = true, nightActive = true)!!
        assertEquals(at(31, 7), alarm.at)
        assertTrue(alarm.exact)
    }

    @Test
    fun failedMorningRestoreIsRetried() {
        val alarm = nextAlarm(at(31, 7), overnight, enabled = true, nightActive = true)!!
        assertEquals(at(31, 7).plus(RESTORE_RETRY), alarm.at)
        assertFalse(alarm.exact)
    }

    @Test
    fun retryNeverDelaysTheNextNight() {
        val alarm = nextAlarm(at(31, 21, 50), overnight, enabled = true, nightActive = true)!!
        assertEquals(at(31, 22), alarm.at)
        assertTrue(alarm.exact)
    }

    @Test
    fun disabledAppStillRetriesAPendingRestore() {
        val alarm = nextAlarm(at(30, 23), overnight, enabled = false, nightActive = true)!!
        assertEquals(at(30, 23).plus(RESTORE_RETRY), alarm.at)
        assertFalse(alarm.exact)
    }

    @Test
    fun disabledAppWithNothingPendingHasNoAlarm() {
        assertNull(nextAlarm(at(30, 23), overnight, enabled = false, nightActive = false))
    }

    @Test
    fun invalidWindowRetriesOnlyAPendingRestore() {
        val invalid = NightWindow(LocalTime.of(8, 0), LocalTime.of(8, 0))
        assertNull(nextAlarm(at(30, 12), invalid, enabled = true, nightActive = false))
        val alarm = nextAlarm(at(30, 12), invalid, enabled = true, nightActive = true)!!
        assertEquals(at(30, 12).plus(RESTORE_RETRY), alarm.at)
    }

    @Test
    fun nightStartedOnVibrateIsCheckedAgain() {
        val alarm = nextAlarm(at(30, 23), overnight, enabled = true, nightActive = true, daytimeSaved = false)!!
        assertEquals(at(30, 23).plus(RESTORE_RETRY), alarm.at)
        assertFalse(alarm.exact)
    }

    @Test
    fun checksForANightStartedOnVibrateNeverDelayTheMorning() {
        val alarm = nextAlarm(at(31, 6, 50), overnight, enabled = true, nightActive = true, daytimeSaved = false)!!
        assertEquals(at(31, 7), alarm.at)
        assertTrue(alarm.exact)
    }

    @Test
    fun alwaysStartedOnVibrateIsCheckedAgain() {
        val alarm = nextAlarm(
            at(30, 12), overnight, enabled = true, nightActive = true, always = true, daytimeSaved = false,
        )!!
        assertEquals(at(30, 12).plus(RESTORE_RETRY), alarm.at)
    }

    @Test
    fun alwaysHasNoAlarm() {
        assertNull(nextAlarm(at(30, 12), overnight, enabled = true, nightActive = true, always = true))
    }

    @Test
    fun alwaysSwitchedOffRetriesAPendingRestore() {
        val alarm = nextAlarm(at(30, 12), overnight, enabled = false, nightActive = true, always = true)!!
        assertEquals(at(30, 12).plus(RESTORE_RETRY), alarm.at)
        assertFalse(alarm.exact)
    }

    @Test
    fun backFromAlwaysToHoursDuringTheDayRetriesTheRestore() {
        // Was "always" (night state active), now back to 22:00-07:00 at noon: restore pending
        val alarm = nextAlarm(at(30, 12), overnight, enabled = true, nightActive = true, always = false)!!
        assertEquals(at(30, 12).plus(RESTORE_RETRY), alarm.at)
    }
}
