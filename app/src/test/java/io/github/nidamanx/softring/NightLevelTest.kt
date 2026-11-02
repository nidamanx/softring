// SPDX-License-Identifier: AGPL-3.0-or-later

package io.github.nidamanx.softring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightLevelTest {

    @Test
    fun lowersDaytimeVolumeToNightSetting() {
        assertEquals(1, nightLevel(saved = 6, night = 1))
        assertEquals(3, nightLevel(saved = 7, night = 3))
    }

    @Test
    fun neverRaisesALowerDaytimeVolume() {
        assertEquals(2, nightLevel(saved = 2, night = 3))
        assertEquals(1, nightLevel(saved = 1, night = 5))
    }

    @Test
    fun sameLevelIsKept() {
        assertEquals(4, nightLevel(saved = 4, night = 4))
    }

    @Test
    fun mutedDaytimeStreamIsLeftAlone() {
        assertNull(nightLevel(saved = 0, night = 1))
    }

    @Test
    fun withoutSavedLevelTheNightSettingApplies() {
        assertEquals(3, nightLevel(saved = Settings.NOT_SAVED, night = 3))
    }

    @Test
    fun nightSettingBelowMinimumIsRaisedToMinimum() {
        assertEquals(VolumeController.MIN_VOLUME, nightLevel(saved = 5, night = 0))
    }

    @Test
    fun daytimeIsSavedWhenTheNightStartedOnVibrate() {
        assertTrue(mustSaveDaytime(savedRing = Settings.NOT_SAVED, savedNotification = Settings.NOT_SAVED))
    }

    @Test
    fun savedDaytimeIsNeverOverwritten() {
        assertFalse(mustSaveDaytime(savedRing = 6, savedNotification = 4))
        // A stream muted during the day (0) still counts as saved
        assertFalse(mustSaveDaytime(savedRing = 5, savedNotification = 0))
    }
}
