// SPDX-License-Identifier: AGPL-3.0-or-later

package io.github.nidamanx.softring

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import java.time.LocalTime

/**
 * App settings and runtime state.
 *
 * Stored in device-protected storage so they can be read right after a reboot,
 * before the user unlocks the phone (direct boot). Nothing here is sensitive.
 */
class Settings(context: Context) {

    private val prefs: SharedPreferences = context
        .createDeviceProtectedStorageContext()
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    // --- User settings ---

    /** Off until the user turns it on: nothing changes right after installing. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Lowered all the time, not only during [window], until the app is switched off. */
    var always: Boolean
        get() = prefs.getBoolean(KEY_ALWAYS, false)
        set(value) = prefs.edit().putBoolean(KEY_ALWAYS, value).apply()

    /** The user said they use no modes: the Do Not Disturb access card is hidden. */
    var dndCardDismissed: Boolean
        get() = prefs.getBoolean(KEY_DND_CARD_DISMISSED, false)
        set(value) = prefs.edit().putBoolean(KEY_DND_CARD_DISMISSED, value).apply()

    /** The Quick Settings tile was added from the app (Android 13+): stop offering it. */
    var tileAdded: Boolean
        get() = prefs.getBoolean(KEY_TILE_ADDED, false)
        set(value) = prefs.edit().putBoolean(KEY_TILE_ADDED, value).apply()

    var window: NightWindow
        get() = NightWindow(
            LocalTime.ofSecondOfDay(prefs.getInt(KEY_START, NightWindow.DEFAULT.start.toSecondOfDay()).toLong()),
            LocalTime.ofSecondOfDay(prefs.getInt(KEY_END, NightWindow.DEFAULT.end.toSecondOfDay()).toLong()),
        )
        set(value) = prefs.edit()
            .putInt(KEY_START, value.start.toSecondOfDay())
            .putInt(KEY_END, value.end.toSecondOfDay())
            .apply()

    /** Night volume for calls (ringtone stream). */
    var nightRingVolume: Int
        get() = prefs.getInt(KEY_NIGHT_RING, DEFAULT_NIGHT_VOLUME)
        set(value) = prefs.edit().putInt(KEY_NIGHT_RING, value).apply()

    /** Night volume for messages and other notifications. */
    var nightNotificationVolume: Int
        get() = prefs.getInt(KEY_NIGHT_NOTIFICATION, DEFAULT_NIGHT_VOLUME)
        set(value) = prefs.edit().putInt(KEY_NIGHT_NOTIFICATION, value).apply()

    // --- Runtime state ---

    /** True while the night volume is applied. */
    val nightActive: Boolean get() = prefs.getBoolean(KEY_ACTIVE, false)

    /** Daytime volumes saved when the night started, or [NOT_SAVED]. */
    val savedRingVolume: Int get() = prefs.getInt(KEY_SAVED_RING, NOT_SAVED)
    val savedNotificationVolume: Int get() = prefs.getInt(KEY_SAVED_NOTIFICATION, NOT_SAVED)

    // commit() instead of apply(): the state must be on disk before a receiver returns
    @SuppressLint("ApplySharedPref")
    fun markNightStarted(savedRing: Int, savedNotification: Int) {
        prefs.edit()
            .putBoolean(KEY_ACTIVE, true)
            .putInt(KEY_SAVED_RING, savedRing)
            .putInt(KEY_SAVED_NOTIFICATION, savedNotification)
            .commit()
    }

    @SuppressLint("ApplySharedPref")
    fun markNightEnded() {
        prefs.edit()
            .putBoolean(KEY_ACTIVE, false)
            .remove(KEY_SAVED_RING)
            .remove(KEY_SAVED_NOTIFICATION)
            .commit()
    }

    companion object {
        const val NOT_SAVED = -1
        const val DEFAULT_NIGHT_VOLUME = 1

        private const val FILE_NAME = "softring"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_ALWAYS = "always"
        private const val KEY_DND_CARD_DISMISSED = "dnd_card_dismissed"
        private const val KEY_TILE_ADDED = "tile_added"
        private const val KEY_START = "start_second_of_day"
        private const val KEY_END = "end_second_of_day"
        private const val KEY_NIGHT_RING = "night_ring_volume"
        private const val KEY_NIGHT_NOTIFICATION = "night_notification_volume"
        private const val KEY_ACTIVE = "night_active"
        private const val KEY_SAVED_RING = "saved_ring_volume"
        private const val KEY_SAVED_NOTIFICATION = "saved_notification_volume"
    }
}
