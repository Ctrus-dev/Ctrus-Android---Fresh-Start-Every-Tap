package mo.dev.ctrus.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import mo.dev.ctrus.R
import mo.dev.ctrus.data.ProfileSchedule

/** Alarm mode's tones (spec D3.2): the files in res/raw, their names, and the 3-second preview. */
object AlarmSounds {
    @RawRes
    fun rawRes(soundId: String): Int? = when (soundId) {
        "ctrus_morning" -> R.raw.ctrus_morning
        "ctrus_lumen" -> R.raw.ctrus_lumen
        "ctrus_lagoon" -> R.raw.ctrus_lagoon
        "ctrus_daybreak" -> R.raw.ctrus_daybreak
        "ctrus_timber" -> R.raw.ctrus_timber
        "ctrus_serenade" -> R.raw.ctrus_serenade
        "ctrus_arcade" -> R.raw.ctrus_arcade
        "ctrus_groove" -> R.raw.ctrus_groove
        "ctrus_oldclock" -> R.raw.ctrus_oldclock
        "ctrus_birdsong" -> R.raw.ctrus_birdsong
        else -> null
    }

    @StringRes
    fun titleRes(soundId: String): Int = when (soundId) {
        "ctrus_morning" -> R.string.alarm_sound_morning
        "ctrus_lumen" -> R.string.alarm_sound_lumen
        "ctrus_lagoon" -> R.string.alarm_sound_lagoon
        "ctrus_daybreak" -> R.string.alarm_sound_daybreak
        "ctrus_timber" -> R.string.alarm_sound_timber
        "ctrus_serenade" -> R.string.alarm_sound_serenade
        "ctrus_arcade" -> R.string.alarm_sound_arcade
        "ctrus_groove" -> R.string.alarm_sound_groove
        "ctrus_oldclock" -> R.string.alarm_sound_oldclock
        "ctrus_birdsong" -> R.string.alarm_sound_birdsong
        ProfileSchedule.VIBRATE_ALARM_SOUND -> R.string.alarm_sound_vibrate
        else -> R.string.alarm_sound_morning
    }

    val alarmAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** A looping player on the alarm stream, or null for "Vibrate only". */
    fun makePlayer(context: Context, soundId: String, looping: Boolean): MediaPlayer? {
        val res = rawRes(soundId) ?: return null
        return runCatching {
            MediaPlayer().apply {
                setAudioAttributes(alarmAttributes)
                setDataSource(context, Uri.parse("android.resource://${context.packageName}/$res"))
                isLooping = looping
                prepare()
            }
        }.onFailure { android.util.Log.w("CtrusAlarm", "Couldn't play $soundId", it) }.getOrNull()
    }

    fun vibrator(context: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    /** One of the ringing alarm's pulses; [AlarmService] asks for one every 1.5 s. */
    fun vibratePulse(context: Context) = vibrate(context, VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE))

    /** Vibrates once as an alarm, so it still does on silent ("Vibrate only" when chosen). */
    fun vibrate(context: Context) = vibrate(context, VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))

    private fun vibrate(context: Context, effect: VibrationEffect) {
        val vibrator = vibrator(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, alarmAttributes)
        }
    }

    private var previewPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopPreview = Runnable { releasePreview() }

    /**
     * Plays a sound for 3 s while choosing it. Each pick restarts the 3 s, so picking the same tone
     * again plays it in full again. Nothing plays while the alarm itself is ringing.
     */
    fun preview(context: Context, soundId: String) {
        if (AlarmService.isRunning) return
        handler.removeCallbacks(stopPreview)
        releasePreview()
        if (soundId == ProfileSchedule.VIBRATE_ALARM_SOUND) {
            vibrate(context)
            return
        }
        previewPlayer = makePlayer(context.applicationContext, soundId, looping = true)?.also { it.start() }
        handler.postDelayed(stopPreview, 3_000)
    }

    private fun releasePreview() {
        previewPlayer?.runCatching { stop(); release() }
        previewPlayer = null
    }
}
