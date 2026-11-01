package mo.dev.ctrus.childmode

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import mo.dev.ctrus.R
import mo.dev.ctrus.util.TrustedClock
import java.util.UUID

/** Spec Part B, section 3: the whole phone is in one mode, chosen on first launch. */
enum class AppMode { NORMAL, CHILD }

/**
 * One of the parents' Ctrus — the master key for every profile in Child Mode. Recognised by its
 * [uid] only (lowercase hex): a URL can be copied onto any tag.
 */
@Serializable
data class ParentTag(val uid: String, val url: String? = null, val label: String)

/** The optional child's Ctrus on a profile. Starts sessions; ends them only when the profile allows it. */
@Serializable
data class ChildTag(val uid: String, val url: String? = null)

@Serializable
data class TamperLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val type: Kind,
    /** Epoch millis. */
    val at: Long,
    /** `clockChanged`: how far the date moved away from real time, in seconds; negative when moved back. */
    val dateShift: Long? = null,
    /** `pinLockout`: how long entering the parent code was locked for, in seconds. */
    val lockDuration: Long? = null,
    /** The profile whose session was running at the time. */
    val activeProfileName: String? = null,
) {
    @Serializable
    enum class Kind(
        @StringRes val titleRes: Int,
        @StringRes val explanationRes: Int,
        @StringRes val adviceRes: Int,
    ) {
        @SerialName("permissionRevoked") PERMISSION_REVOKED(
            R.string.tamper_permission_revoked, R.string.tamper_permission_revoked_explanation, R.string.tamper_permission_revoked_advice,
        ),
        @SerialName("appStopped") APP_STOPPED(
            R.string.tamper_app_stopped, R.string.tamper_app_stopped_explanation, R.string.tamper_app_stopped_advice,
        ),
        @SerialName("clockChanged") CLOCK_CHANGED(
            R.string.tamper_clock_changed, R.string.tamper_clock_changed_explanation, R.string.tamper_clock_changed_advice,
        ),
        @SerialName("pinLockout") PIN_LOCKOUT(
            R.string.tamper_pin_lockout, R.string.tamper_pin_lockout_explanation, R.string.tamper_pin_lockout_advice,
        ),
    }
}

/**
 * Port of iOS's ChildModeStore: the app mode, the parents' Ctrus and the attempt log, read
 * synchronously (the accessibility service and the alarm receivers need them without a
 * coroutine) and also exposed as flows for the UI. The parent code and recovery key aren't here —
 * see [ParentCredentials].
 */
object ChildModeStore {
    const val MAX_TAMPER_LOG_ENTRIES = 50

    private const val KEY_APP_MODE = "appMode"
    private const val KEY_PARENT_TAGS = "parentTags"
    private const val KEY_TAMPER_LOG = "tamperLog"
    private const val KEY_TAMPER_LOG_UNSEEN = "tamperLogUnseen"

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var prefs: SharedPreferences
    private lateinit var clockWatch: TrustedClock

    private val _appMode = MutableStateFlow(AppMode.NORMAL)
    val appModeFlow: StateFlow<AppMode> = _appMode

    private val _parentTags = MutableStateFlow<List<ParentTag>>(emptyList())
    val parentTagsFlow: StateFlow<List<ParentTag>> = _parentTags

    private val _tamperLog = MutableStateFlow<List<TamperLogEntry>>(emptyList())
    val tamperLogFlow: StateFlow<List<TamperLogEntry>> = _tamperLog

    private val _tamperLogUnseen = MutableStateFlow(false)
    val tamperLogUnseenFlow: StateFlow<Boolean> = _tamperLogUnseen

    fun init(context: Context) {
        prefs = context.getSharedPreferences("mo.dev.ctrus.child_mode", Context.MODE_PRIVATE)
        // A single watcher reads the clock only to spot date changes, so one change is logged
        // once rather than by every feature that later notices it.
        clockWatch = TrustedClock("clockWatch", TrustedClock.SharedPreferencesStore(context), bootSession = TrustedClock.bootCount(context))
        _appMode.value = if (prefs.getString(KEY_APP_MODE, null) == "child") AppMode.CHILD else AppMode.NORMAL
        _parentTags.value = decode(KEY_PARENT_TAGS, ListSerializer(ParentTag.serializer()))
        _tamperLog.value = decode(KEY_TAMPER_LOG, ListSerializer(TamperLogEntry.serializer()))
        _tamperLogUnseen.value = prefs.getBoolean(KEY_TAMPER_LOG_UNSEEN, false)
    }

    var appMode: AppMode
        get() = _appMode.value
        set(value) {
            prefs.edit().putString(KEY_APP_MODE, if (value == AppMode.CHILD) "child" else "normal").commit()
            _appMode.value = value
        }

    val isChildMode: Boolean get() = appMode == AppMode.CHILD

    var parentTags: List<ParentTag>
        get() = _parentTags.value
        set(value) {
            prefs.edit().putString(KEY_PARENT_TAGS, json.encodeToString(ListSerializer(ParentTag.serializer()), value)).commit()
            _parentTags.value = value
        }

    var tamperLog: List<TamperLogEntry>
        get() = _tamperLog.value
        set(value) {
            prefs.edit().putString(KEY_TAMPER_LOG, json.encodeToString(ListSerializer(TamperLogEntry.serializer()), value)).commit()
            _tamperLog.value = value
        }

    var tamperLogUnseen: Boolean
        get() = _tamperLogUnseen.value
        set(value) {
            prefs.edit().putBoolean(KEY_TAMPER_LOG_UNSEEN, value).commit()
            _tamperLogUnseen.value = value
        }

    /** Lowercase hex with no separators, the one format both apps compare in. */
    fun normalizedUid(uid: String): String = uid.lowercase().filter { it in '0'..'9' || it in 'a'..'f' }

    fun isParentTag(uid: String): Boolean {
        val normalized = normalizedUid(uid)
        return parentTags.any { normalizedUid(it.uid) == normalized }
    }

    /** "Parents' Ctrus N", with the first number not already taken. */
    fun nextParentTagLabel(tags: List<ParentTag>, labelFor: (Int) -> String): String {
        val used = tags.map { it.label }.toSet()
        val number = (1..tags.size + 1).first { labelFor(it) !in used }
        return labelFor(number)
    }

    @Synchronized
    fun logTamper(
        kind: TamperLogEntry.Kind,
        at: Long = System.currentTimeMillis(),
        dateShiftSeconds: Long? = null,
        lockDurationSeconds: Long? = null,
    ) {
        if (!isChildMode) return
        val activeProfileName = mo.dev.ctrus.blocking.BlockingStateHolder.state.value
            .takeIf { it.isBlocking }?.profileName?.takeIf { it.isNotEmpty() }
        val entry = TamperLogEntry(
            type = kind,
            at = at,
            dateShift = dateShiftSeconds,
            lockDuration = lockDurationSeconds,
            activeProfileName = activeProfileName,
        )
        tamperLog = (tamperLog + entry).takeLast(MAX_TAMPER_LOG_ENTRIES)
        tamperLogUnseen = true
    }

    fun checkForClockChange() {
        val reading = clockWatch.read()
        if (reading.clockJumped) logTamper(TamperLogEntry.Kind.CLOCK_CHANGED, dateShiftSeconds = reading.dateShiftMillis / 1000)
    }

    fun clearChildModeData() {
        appMode = AppMode.NORMAL
        parentTags = emptyList()
        tamperLog = emptyList()
        tamperLogUnseen = false
    }

    private fun <T> decode(key: String, serializer: kotlinx.serialization.KSerializer<List<T>>): List<T> =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
}
