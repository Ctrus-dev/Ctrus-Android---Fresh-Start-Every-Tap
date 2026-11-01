package mo.dev.ctrus.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mo.dev.ctrus.CtrusApp
import mo.dev.ctrus.R
import mo.dev.ctrus.data.ProfileSchedule
import mo.dev.ctrus.util.DateFormatters
import java.time.Instant
import java.time.ZoneId

/**
 * Plays Alarm mode's alarm (spec D5.2): the tone in a loop on the alarm stream and a vibration
 * every 1.5 s (or just the vibration for "Vibrate only"), with a notification whose full-screen
 * intent puts [AlarmActivity] over the lock screen.
 *
 * Keeps ringing whether the app or the alarm screen is open or not, until the session ends or the
 * alarm is snoozed — it checks [WakeAlarmManager.ringingProfileId] every second and stops itself
 * once nothing is due. If the system kills it, the next spare alarm starts it again; while it
 * rings, those spares are kept a minute ahead.
 */
class AlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loopJob: Job? = null
    private var player: MediaPlayer? = null
    private var ringingProfileId: String? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var vibrationJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Has to come first, even when it turns out nothing is due any more.
        val record = WakeAlarmManager.records.value.values
            .filter { it.alarmDate <= System.currentTimeMillis() }
            .maxByOrNull { it.alarmDate }
        val profileId = WakeAlarmManager.records.value.entries.firstOrNull { it.value == record }?.key
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(profileId, record),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        if (loopJob == null) loopJob = scope.launch { ringWhileDue() }
        return START_NOT_STICKY
    }

    private suspend fun ringWhileDue() {
        val app = CtrusApp.from(this)
        var lastSparesPush = 0L
        while (true) {
            val profileId = WakeAlarmManager.ringingProfileId(app)
            if (profileId == null) {
                stopSelf()
                return
            }
            if (profileId != ringingProfileId) {
                ringingProfileId = profileId
                val record = WakeAlarmManager.records.value[profileId]
                startSound(record?.soundId ?: ProfileSchedule.DEFAULT_ALARM_SOUND)
                NotificationManagerCompat.from(this).runCatching { notify(NOTIFICATION_ID, buildNotification(profileId, record)) }
            }
            val now = System.currentTimeMillis()
            bringAlarmScreenBack(profileId, now)
            if (now - lastSparesPush >= 30_000) {
                lastSparesPush = now
                WakeAlarmManager.keepSparesAhead(app, profileId)
            }
            delay(1_000)
        }
    }

    private var hiddenSince = 0L

    /**
     * While it rings, the alarm screen comes back a few seconds after it's left (Home, Back, the
     * power button, or the heads-up swiped away on an unlocked phone), unless Ctrus itself is on
     * screen or the phone is being unlocked after the slide. Started straight from here: the
     * Accessibility service being bound lets Ctrus open activities from the background, like the
     * block screen. Not in the spec; the user asked for it (2026-11-01).
     */
    private fun bringAlarmScreenBack(profileId: String, now: Long) {
        if (AlarmActivity.isVisible || mo.dev.ctrus.MainActivity.isVisible || now < AlarmActivity.openingAppUntil) {
            hiddenSince = 0L
            return
        }
        if (hiddenSince == 0L) hiddenSince = now
        if (now - hiddenSince < BRING_BACK_AFTER_MILLIS) return
        hiddenSince = now
        runCatching {
            startActivity(
                Intent(this, AlarmActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                    .putExtra(AlarmActivity.EXTRA_PROFILE_ID, profileId),
            )
        }.onFailure { android.util.Log.w("CtrusAlarm", "Couldn't bring the alarm screen back", it) }
        // Without the Accessibility service the system can refuse that start. A new full-screen
        // notification always opens it over the lock screen (the service's own can't be posted
        // anew), so one is posted for a moment, under its own id.
        if (getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) {
            val record = WakeAlarmManager.records.value[profileId]
            val reopen = notificationBuilder(profileId, record)
                .setTimeoutAfter(BRING_BACK_AFTER_MILLIS)
                .setOngoing(false)
                .build()
            NotificationManagerCompat.from(this).runCatching {
                cancel(REOPEN_NOTIFICATION_ID)
                notify(REOPEN_NOTIFICATION_ID, reopen)
            }.onFailure { android.util.Log.w("CtrusAlarm", "Couldn't post the alarm screen again", it) }
        }
    }

    private fun startSound(soundId: String) {
        stopSound()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Ctrus:Alarm")
            .apply { acquire(60 * 60 * 1000L) }

        val audioManager = getSystemService(AudioManager::class.java)
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AlarmSounds.alarmAttributes)
            .build()
            .also { audioManager.requestAudioFocus(it) }

        // The system alarm vibrates alongside its sound, so this does too: a pulse every 1.5 s,
        // each one asked for anew. One repeating waveform got cancelled by the system once the
        // alarm screen was closed (seen on the POCO), leaving only the sound.
        vibrationJob = scope.launch {
            while (true) {
                AlarmSounds.vibratePulse(this@AlarmService)
                delay(VIBRATION_PERIOD_MILLIS)
            }
        }
        if (soundId == ProfileSchedule.VIBRATE_ALARM_SOUND) return
        player = AlarmSounds.makePlayer(this, soundId, looping = true)?.also { it.start() }
    }

    private fun stopSound() {
        player?.runCatching { stop(); release() }
        player = null
        vibrationJob?.cancel()
        vibrationJob = null
        AlarmSounds.vibrator(this).cancel()
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focusRequest = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun buildNotification(profileId: String?, record: WakeAlarmManager.AlarmRecord?): Notification =
        notificationBuilder(profileId, record).build()

    private fun notificationBuilder(profileId: String?, record: WakeAlarmManager.AlarmRecord?): NotificationCompat.Builder {
        val alarmScreen = PendingIntent.getActivity(
            this,
            0,
            Intent(this, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(AlarmActivity.EXTRA_PROFILE_ID, profileId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val time = record?.let {
            val alarm = Instant.ofEpochMilli(it.alarmDate).atZone(ZoneId.systemDefault())
            DateFormatters.formatTimeOfDay(this, alarm.hour, alarm.minute)
        }
        val builder = NotificationCompat.Builder(this, CtrusApp.ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(record?.profileName ?: getString(R.string.strategy_alarm_name))
            .setContentText(time)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(alarmScreen)
            .setFullScreenIntent(alarmScreen, true)
        if (profileId != null && record?.allowsSnooze == true) {
            val snooze = PendingIntent.getBroadcast(
                this,
                1,
                Intent(this, AlarmReceiver::class.java)
                    .setAction(AlarmReceiver.ACTION_SNOOZE)
                    .putExtra(AlarmReceiver.EXTRA_PROFILE_ID, profileId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, getString(R.string.alarm_snooze), snooze)
        }
        return builder
    }

    override fun onDestroy() {
        isRunning = false
        stopSound()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 0x416c_726d
        private const val REOPEN_NOTIFICATION_ID = NOTIFICATION_ID + 1
        private const val BRING_BACK_AFTER_MILLIS = 3_000L
        private const val VIBRATION_PERIOD_MILLIS = 1_500L

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java)) }
                .onFailure { android.util.Log.w("CtrusAlarm", "Couldn't start the alarm", it) }
        }

        fun stop(context: Context) {
            if (isRunning) context.stopService(Intent(context, AlarmService::class.java))
        }
    }
}
