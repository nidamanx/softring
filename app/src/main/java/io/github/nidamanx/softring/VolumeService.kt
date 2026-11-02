// SPDX-License-Identifier: AGPL-3.0-or-later

package io.github.nidamanx.softring

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Android 17+ only: a foreground service that lives just long enough to apply
 * the volume state, then stops.
 *
 * From Android 17, volume changes from an app with no visible activity and no
 * foreground service are silently ignored, so alarms, system events and the
 * Quick Settings tile go through this service (see [Engine.refreshFromBackground]).
 * Without POST_NOTIFICATIONS its notification is not shown in the shade.
 */
class VolumeService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startSpecialUse()
        // Only after startForeground: volume calls made before it still count as background
        Engine.refresh(this, "service(${intent?.getStringExtra(EXTRA_SOURCE)})")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    // Started only on Android 17+ (isNeeded), so the Android 14 API is always there.
    // @RequiresApi is AndroidX (no dependencies here)
    @SuppressLint("UseRequiresApi")
    @TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun startSpecialUse() {
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.volume_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ),
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentText(getString(R.string.volume_notification_text))
            .build()
    }

    companion object {
        /** Android 17: VERSION_CODES has no name for it with compileSdk 36. */
        const val ANDROID_17 = 37

        private const val CHANNEL_ID = "volume_change"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_SOURCE = "source"

        /** True on the Android versions that need this service to change the volume. */
        val isNeeded: Boolean get() = Build.VERSION.SDK_INT >= ANDROID_17

        /**
         * Starts the service, or returns false if Android does not allow it now
         * (only some events allow a foreground service to start from the background).
         */
        fun start(context: Context, source: String): Boolean = try {
            context.startForegroundService(
                Intent(context, VolumeService::class.java).putExtra(EXTRA_SOURCE, source),
            )
            true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException is an IllegalStateException
            Log.w("SoftRing", "Volume service not started: ${e.message}")
            false
        }
    }
}
