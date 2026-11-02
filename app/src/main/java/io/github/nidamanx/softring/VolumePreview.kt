// SPDX-License-Identifier: AGPL-3.0-or-later

package io.github.nidamanx.softring

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings as SystemSettings
import android.util.Log
import kotlin.math.pow

/**
 * Lets the user hear a lowered level in advance: plays the default ringtone or
 * notification sound for a moment, attenuated from the current stream volume down
 * to that level. The system volume is never changed, so vibrate, silent and the
 * Do Not Disturb modes are never touched.
 *
 * Android 9+ only: older versions cannot tell the decibels of a volume step.
 * The default sounds are played through [RingtoneManager], which needs no
 * permission even when the user's ringtone is one of their own files.
 */
class VolumePreview(private val context: Context) {

    /** SILENT: vibrate, silent or a muted stream. FAILED: the sound could not be played. */
    enum class Result { PLAYED, SILENT, UNSUPPORTED, FAILED }

    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var playing: Ringtone? = null
    private val stopPlaying = Runnable { stop() }

    /** Plays [stream]'s default sound at [level]; levels above the current volume play at the current one. */
    fun play(stream: Int, level: Int): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return Result.UNSUPPORTED
        // Vibrate or silent: from the ring stream's mute state, as in VolumeController (during
        // a mode Android reports the ringer mode as silent even when calls can ring)
        if (audio.isStreamMute(VolumeController.RING)) return Result.SILENT
        val current = audio.getStreamVolume(stream)
        if (current <= 0 || level <= 0) return Result.SILENT

        val ring = stream == VolumeController.RING
        val uri = if (ring) SystemSettings.System.DEFAULT_RINGTONE_URI else SystemSettings.System.DEFAULT_NOTIFICATION_URI
        stop()
        // A preview must never break the app: any playback error just means no sound
        return try {
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return Result.FAILED
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(if (ring) AudioAttributes.USAGE_NOTIFICATION_RINGTONE else AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ringtone.volume = gain(stream, level.coerceAtMost(current), current)
            ringtone.play()
            playing = ringtone
            handler.postDelayed(stopPlaying, PREVIEW_MS)
            Result.PLAYED
        } catch (e: RuntimeException) {
            Log.w(TAG, "Volume preview not played: ${e.message}")
            Result.FAILED
        }
    }

    fun stop() {
        handler.removeCallbacks(stopPlaying)
        try {
            playing?.stop()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Volume preview not stopped: ${e.message}")
        }
        playing = null
    }

    /** Linear gain that turns the [current] step into the [level] step, from their decibels. */
    // @RequiresApi is AndroidX (no dependencies here); the only caller checks Android 9+
    @SuppressLint("UseRequiresApi")
    @TargetApi(Build.VERSION_CODES.P)
    private fun gain(stream: Int, level: Int, current: Int): Float {
        if (level >= current) return 1f
        return try {
            val db = { index: Int -> audio.getStreamVolumeDb(stream, index, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) }
            val difference = db(level) - db(current)
            if (difference.isFinite()) 10f.pow(difference / 20f).coerceIn(0f, 1f) else level.toFloat() / current
        } catch (e: IllegalArgumentException) {
            // Device without decibel data for this stream: an approximation is better than nothing
            level.toFloat() / current
        }
    }

    companion object {
        private const val PREVIEW_MS = 2_500L
        private const val TAG = "SoftRing"

        val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }
}
