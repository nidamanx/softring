// SPDX-License-Identifier: AGPL-3.0-or-later

package io.github.nidamanx.softring

import android.app.Activity
import android.app.NotificationManager
import android.app.StatusBarManager
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings as SystemSettings
import android.text.format.DateFormat
import android.view.View
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * The main screen: on/off switch, when (hours or always), lowered volumes with a
 * preview, permission hints; the info button opens [InfoActivity].
 * Platform widgets only (no AndroidX), to keep the app dependency-free.
 */
class MainActivity : Activity() {

    private lateinit var settings: Settings
    private lateinit var volumes: VolumeController
    private lateinit var scheduler: AlarmScheduler
    private lateinit var preview: VolumePreview
    private lateinit var notificationManager: NotificationManager
    private val handler = Handler(Looper.getMainLooper())
    private var pendingPreview: Runnable? = null

    /** Do Not Disturb access when the app was left, to notice it was just granted. */
    private var dndAccessAtPause: Boolean? = null

    private lateinit var status: TextView
    private lateinit var switchEnabled: Switch
    private lateinit var valueStart: TextView
    private lateinit var valueEnd: TextView
    private lateinit var valueRing: TextView
    private lateinit var valueNotification: TextView
    private lateinit var seekRing: SeekBar
    private lateinit var seekNotification: SeekBar
    private lateinit var hintRing: TextView
    private lateinit var hintNotification: TextView
    private lateinit var cardDnd: View
    private lateinit var cardExact: View
    private lateinit var cardHours: View
    private lateinit var noteFollowsHours: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        EdgeToEdge.apply(this, findViewById(R.id.root))

        settings = Settings(this)
        volumes = VolumeController(this)
        scheduler = AlarmScheduler(this)
        preview = VolumePreview(this)
        notificationManager = getSystemService(NotificationManager::class.java)

        status = findViewById(R.id.status)
        switchEnabled = findViewById(R.id.switch_enabled)
        valueStart = findViewById(R.id.value_start)
        valueEnd = findViewById(R.id.value_end)
        valueRing = findViewById(R.id.value_ring)
        valueNotification = findViewById(R.id.value_notification)
        seekRing = findViewById(R.id.seek_ring)
        seekNotification = findViewById(R.id.seek_notification)
        cardDnd = findViewById(R.id.card_dnd)
        cardExact = findViewById(R.id.card_exact)
        cardHours = findViewById(R.id.card_hours)
        noteFollowsHours = findViewById(R.id.note_follows_hours)
        hintRing = findViewById(R.id.hint_ring)
        hintNotification = findViewById(R.id.hint_notification)
        if (VolumeService.isNeeded) {
            findViewById<TextView>(R.id.text_exact).setText(R.string.perm_exact_text_required)
        }
        findViewById<TextView>(R.id.note_preview).setText(
            if (VolumePreview.isSupported) R.string.preview_hint else R.string.preview_unavailable,
        )

        setUpSwitch()
        setUpWhen()
        setUpTimeRows()
        setUpSeekBar(seekRing, VolumeController.RING) { settings.nightRingVolume = it }
        setUpSeekBar(seekNotification, VolumeController.NOTIFICATION) {
            settings.nightNotificationVolume = it
        }
        findViewById<View>(R.id.btn_dnd).setOnClickListener {
            startActivity(Intent(SystemSettings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        findViewById<View>(R.id.btn_exact).setOnClickListener { requestExactAlarms() }
        findViewById<View>(R.id.btn_dnd_dismiss).setOnClickListener {
            settings.dndCardDismissed = true
            render()
        }
        findViewById<View>(R.id.btn_add_tile).setOnClickListener { requestAddTile() }
        findViewById<View>(R.id.btn_info).setOnClickListener {
            startActivity(Intent(this, InfoActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val lowered = settings.window.shouldBeLowered(ZonedDateTime.now(), settings.enabled, settings.always)
        val dndAccess = notificationManager.isNotificationPolicyAccessGranted
        if (settings.nightActive != lowered) {
            // The state does not match the time: a daytime restore is pending (e.g. the
            // phone was on vibrate when the night ended), or Android 17 did not let a
            // change run with the app closed. Do it now.
            Engine.refresh(this, "app")
        } else {
            // Do Not Disturb access just granted: the night volume refused during a mode
            // applies now. Night started on vibrate or silent: it applies if the sound is back.
            val dndJustGranted = dndAccess && dndAccessAtPause == false
            val startedSilent = mustSaveDaytime(settings.savedRingVolume, settings.savedNotificationVolume)
            if (lowered && (dndJustGranted || startedSilent)) volumes.applyNightVolumes()
            // Permissions may have changed in the system settings, and the checks for a
            // night started on vibrate stop once it applies: reschedule (cheap, idempotent)
            scheduler.scheduleNext()
        }
        // The Quick Settings tile may have switched SoftRing on or off meanwhile
        switchEnabled.isChecked = settings.enabled
        render()
    }

    override fun onPause() {
        super.onPause()
        dndAccessAtPause = notificationManager.isNotificationPolicyAccessGranted
        pendingPreview?.let { handler.removeCallbacks(it) }
        preview.stop()
    }

    // --- Setup ---

    private fun setUpSwitch() {
        switchEnabled.isChecked = settings.enabled
        switchEnabled.setOnCheckedChangeListener { _, checked ->
            // Already in this state: the switch was only aligned in onResume
            if (checked == settings.enabled) return@setOnCheckedChangeListener
            settings.enabled = checked
            // Switching on again shows the Do Not Disturb card again, if access is still missing
            if (checked) settings.dndCardDismissed = false
            Engine.refresh(this, "app")
            render()
        }
    }

    private fun setUpWhen() {
        val group = findViewById<RadioGroup>(R.id.when_group)
        group.check(if (settings.always) R.id.when_always else R.id.when_schedule)
        group.setOnCheckedChangeListener { _, checkedId ->
            settings.always = checkedId == R.id.when_always
            Engine.refresh(this, "app")
            render()
        }
    }

    private fun setUpTimeRows() {
        findViewById<View>(R.id.row_start).setOnClickListener {
            pickTime(settings.window.start) { settings.window = settings.window.copy(start = it) }
        }
        findViewById<View>(R.id.row_end).setOnClickListener {
            pickTime(settings.window.end) { settings.window = settings.window.copy(end = it) }
        }
    }

    private fun pickTime(initial: LocalTime, onPicked: (LocalTime) -> Unit) {
        TimePickerDialog(
            this,
            { _, hour, minute ->
                onPicked(LocalTime.of(hour, minute))
                Engine.refresh(this, "app")
                render()
            },
            initial.hour,
            initial.minute,
            DateFormat.is24HourFormat(this),
        ).show()
    }

    private fun setUpSeekBar(seek: SeekBar, stream: Int, save: (Int) -> Unit) {
        val max = volumes.maxVolume(stream)
        seek.max = max
        seek.progress = nightSetting(stream).coerceIn(VolumeController.MIN_VOLUME, max)

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                // Here and not on stop tracking: TalkBack and keyboard change the
                // value without any touch tracking
                if (fromUser) {
                    save(progress)
                    // If the night is in progress, the new level applies immediately
                    volumes.applyNightVolumes()
                    schedulePreview(stream)
                }
                renderVolumes()
            }

            override fun onStartTrackingTouch(bar: SeekBar) = Unit

            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
    }

    /** Plays the preview once the slider has been still for a moment, not at every step. */
    private fun schedulePreview(stream: Int) {
        if (!VolumePreview.isSupported) return
        pendingPreview?.let { handler.removeCallbacks(it) }
        pendingPreview = Runnable {
            val level = nightLevel(daytimeVolume(stream), nightSetting(stream)) ?: 0
            if (preview.play(stream, level) == VolumePreview.Result.SILENT) {
                Toast.makeText(this, R.string.preview_silent, Toast.LENGTH_SHORT).show()
            }
        }.also { handler.postDelayed(it, PREVIEW_DELAY_MS) }
    }

    /** The daytime level of [stream]: saved when the night started, otherwise the current one. */
    private fun daytimeVolume(stream: Int): Int {
        val saved = if (stream == VolumeController.RING) settings.savedRingVolume else settings.savedNotificationVolume
        return if (settings.nightActive && saved > 0) saved else volumes.currentVolume(stream)
    }

    private fun nightSetting(stream: Int): Int =
        if (stream == VolumeController.RING) settings.nightRingVolume else settings.nightNotificationVolume

    /** Android 13+: the system asks the user to add the tile, no permission needed. */
    private fun requestAddTile() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, SoftRingTileService::class.java),
            getString(R.string.app_name),
            Icon.createWithResource(this, R.drawable.ic_tile),
            mainExecutor,
        ) { result ->
            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            ) {
                settings.tileAdded = true
                render()
            }
        }
    }

    private fun requestExactAlarms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(
                Intent(SystemSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:$packageName")),
            )
        }
    }

    // --- Rendering ---

    private fun render() {
        val window = settings.window
        valueStart.text = formatTime(this, window.start)
        valueEnd.text = formatTime(this, window.end)

        val always = settings.always
        val state = when {
            !settings.enabled -> getString(R.string.status_off)
            always -> getString(R.string.status_always)
            !window.isValid -> getString(R.string.status_invalid)
            // nightActive alone stays true while a daytime restore is being retried
            settings.nightActive && window.contains(LocalTime.now()) ->
                getString(R.string.status_active, formatTime(this, window.end))
            else -> getString(R.string.status_next, formatTime(this, window.start))
        }
        status.text = if (settings.enabled && !volumes.isRingerAudible()) {
            "$state\n${getString(R.string.status_ringer_silent)}"
        } else {
            state
        }

        cardDnd.visibility = visibleIf(
            settings.enabled && !settings.dndCardDismissed && !notificationManager.isNotificationPolicyAccessGranted,
        )
        findViewById<View>(R.id.btn_add_tile).visibility =
            visibleIf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !settings.tileAdded)
        // "Always" sets no alarms at fixed times: exact timing does not matter
        cardExact.visibility = visibleIf(settings.enabled && !always && !scheduler.canScheduleExact())
        cardHours.visibility = visibleIf(!always)
        noteFollowsHours.visibility = visibleIf(!always)
        if (DebugLog.isEnabled(this)) {
            findViewById<TextView>(R.id.debug_log).apply {
                visibility = View.VISIBLE
                text = DebugLog.read(this@MainActivity)
            }
        }
        renderVolumes()
        SoftRingTileService.requestUpdate(this)
    }

    /**
     * For both sliders: the value with the daytime volume as reference ("2 / 7 · now 5"),
     * a lighter bar up to the daytime volume, and the "never raised" hint when the
     * slider is above it. No reference on vibrate or silent.
     */
    private fun renderVolumes() {
        val sliders = listOf(
            Triple(VolumeController.RING, seekRing, valueRing) to hintRing,
            Triple(VolumeController.NOTIFICATION, seekNotification, valueNotification) to hintNotification,
        )
        for ((slider, hint) in sliders) {
            val (stream, seek, label) = slider
            val daytime = if (volumes.isRingerAudible()) daytimeVolume(stream) else 0
            seek.secondaryProgress = daytime
            label.text = if (daytime > 0) {
                getString(R.string.volume_value_now, seek.progress, seek.max, daytime)
            } else {
                getString(R.string.volume_value, seek.progress, seek.max)
            }
            val capped = daytime > 0 && seek.progress > daytime
            hint.visibility = visibleIf(capped)
            if (capped) hint.text = getString(R.string.volume_capped, daytime)
        }
    }

    private fun visibleIf(condition: Boolean) = if (condition) View.VISIBLE else View.GONE

    private companion object {
        const val PREVIEW_DELAY_MS = 500L
    }
}
