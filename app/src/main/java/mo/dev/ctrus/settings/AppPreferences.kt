package mo.dev.ctrus.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import mo.dev.ctrus.icon.AppIcon
import mo.dev.ctrus.icon.DefaultAppIcon
import mo.dev.ctrus.util.TrustedClock
import java.time.Instant
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "mo.dev.ctrus.prefs")

/**
 * DataStore-backed equivalent of the @AppStorage-backed counters/flags scattered across
 * StrategyManager.swift, RecoveryCodeUtil.swift, and RatingManager.swift. Kept as a single
 * store (unlike iOS's per-property @AppStorage) since Android has no direct equivalent and
 * grouping them avoids a proliferation of tiny DataStore files.
 */
class AppPreferences(private val context: Context) {
    private object Keys {
        val EMERGENCY_REMAINING = intPreferencesKey("emergency_unblocks_remaining")
        val EMERGENCY_RESET_WEEKS = intPreferencesKey("emergency_reset_period_weeks")
        // Real time counted towards the current cycle by TrustedClock, in millis — the phone's
        // date can be moved forward, so it isn't used to tell when the unlocks come back.
        val EMERGENCY_CYCLE_ELAPSED = longPreferencesKey("emergencyCycleElapsed")

        val RECOVERY_REMAINING = intPreferencesKey("recovery_unlocks_remaining")
        val RECOVERY_RESET_WEEKS = intPreferencesKey("recovery_reset_period_weeks")
        // Same as the emergency cycle, but only counted from the first unlock of the cycle.
        val RECOVERY_CYCLE_ELAPSED = longPreferencesKey("recoveryCycleElapsed")
        val IS_RECOVERY_CYCLE_RUNNING = booleanPreferencesKey("isRecoveryCycleRunning")

        val DEVICE_ID = stringPreferencesKey("recovery_device_id")
        val SHOW_INTRO = booleanPreferencesKey("show_intro_screen")
        val SHOWN_BATTERY_OPTIMIZATION_PROMPT = booleanPreferencesKey("shown_battery_optimization_prompt")
        val LAUNCH_COUNT = intPreferencesKey("launch_count")
        val SELECTED_APP_ICON = stringPreferencesKey("selected_app_icon")
        val LAST_REVIEW_PROMPT_VERSION = stringPreferencesKey("last_review_prompt_version")
        val USE_LEFT_HANDED_LAYOUT = booleanPreferencesKey("use_left_handed_layout")
        val HAS_COMPLETED_FIRST_SESSION = booleanPreferencesKey("has_completed_first_session")
    }

    private val weekMillis = 7L * 24 * 60 * 60 * 1000

    private val trustedClockStore = TrustedClock.SharedPreferencesStore(context)
    private val emergencyCycleClock = TrustedClock("emergencyCycle", trustedClockStore)
    private val recoveryCycleClock = TrustedClock("recoveryCycle", trustedClockStore)

    suspend fun deviceId(): String {
        val prefs = context.dataStore.data.first()
        prefs[Keys.DEVICE_ID]?.let { return it }
        val generated = UUID.randomUUID().toString()
        context.dataStore.edit { it[Keys.DEVICE_ID] = generated }
        return generated
    }

    suspend fun remainingEmergencyUnblocks(): Int {
        checkAndResetEmergencyUnblocks()
        return context.dataStore.data.first()[Keys.EMERGENCY_REMAINING] ?: DEFAULT_EMERGENCY_UNBLOCKS
    }

    suspend fun resetPeriodWeeksEmergency(): Int =
        context.dataStore.data.first()[Keys.EMERGENCY_RESET_WEEKS] ?: DEFAULT_RESET_PERIOD_WEEKS

    /**
     * Mirrors iOS 319a1eb: changing the cycle length must not throw away however much of the wait
     * was already done. Only the length changes; the counter is just compared against the new one,
     * and the re-check resets right away if the new, shorter period has already passed.
     */
    suspend fun setResetPeriodWeeksEmergency(weeks: Int) {
        context.dataStore.edit { it[Keys.EMERGENCY_RESET_WEEKS] = weeks }
        checkAndResetEmergencyUnblocks()
    }

    suspend fun nextEmergencyResetDate(): Instant {
        val prefs = context.dataStore.data.first()
        val period = (prefs[Keys.EMERGENCY_RESET_WEEKS] ?: DEFAULT_RESET_PERIOD_WEEKS) * weekMillis
        val remaining = (period - (prefs[Keys.EMERGENCY_CYCLE_ELAPSED] ?: 0)).coerceAtLeast(0)
        return Instant.now().plusMillis(remaining)
    }

    suspend fun consumeEmergencyUnblock(): Boolean {
        val remaining = remainingEmergencyUnblocks()
        if (remaining <= 0) return false
        context.dataStore.edit { it[Keys.EMERGENCY_REMAINING] = remaining - 1 }
        return true
    }

    /**
     * Mirrors iOS ee78b30: the cycle adds up real time from [TrustedClock] instead of comparing
     * the phone's date to the last reset, which came back early once the date was moved forward.
     */
    private suspend fun checkAndResetEmergencyUnblocks() {
        context.dataStore.edit {
            val period = (it[Keys.EMERGENCY_RESET_WEEKS] ?: DEFAULT_RESET_PERIOD_WEEKS) * weekMillis
            val elapsed = (it[Keys.EMERGENCY_CYCLE_ELAPSED] ?: 0) + emergencyCycleClock.trustedElapsedMillis()
            if (elapsed >= period) {
                it[Keys.EMERGENCY_REMAINING] = DEFAULT_EMERGENCY_UNBLOCKS
                it[Keys.EMERGENCY_CYCLE_ELAPSED] = 0
            } else {
                it[Keys.EMERGENCY_CYCLE_ELAPSED] = elapsed
            }
        }
    }

    /** Brings both unlock cycles up to date — on app open/return and when a session starts or ends. */
    suspend fun checkUnlockCycles() {
        checkAndResetEmergencyUnblocks()
        checkAndResetRecoveryUnlocks()
    }

    suspend fun remainingRecoveryUnlocks(): Int {
        checkAndResetRecoveryUnlocks()
        return context.dataStore.data.first()[Keys.RECOVERY_REMAINING] ?: DEFAULT_RECOVERY_UNLOCKS
    }

    // Available as soon as the first unlock of the cycle is used, not just once they're all
    // gone — the "1 left" status text needs this too, not only "0 left".
    suspend fun nextRecoveryResetDate(): Instant? {
        val prefs = context.dataStore.data.first()
        if (prefs[Keys.IS_RECOVERY_CYCLE_RUNNING] != true) return null
        val period = (prefs[Keys.RECOVERY_RESET_WEEKS] ?: DEFAULT_RESET_PERIOD_WEEKS) * weekMillis
        val remaining = (period - (prefs[Keys.RECOVERY_CYCLE_ELAPSED] ?: 0)).coerceAtLeast(0)
        return Instant.now().plusMillis(remaining)
    }

    suspend fun consumeRecoveryUnlock(): Boolean {
        val remaining = remainingRecoveryUnlocks()
        if (remaining <= 0) return false
        context.dataStore.edit {
            it[Keys.RECOVERY_REMAINING] = remaining - 1
            // The 4-week cooldown starts on the first unlock of the cycle — using the second
            // one shouldn't push the reset date back further. remainingRecoveryUnlocks() above
            // just read the clock, so the new cycle only counts time from here.
            if (it[Keys.IS_RECOVERY_CYCLE_RUNNING] != true) {
                it[Keys.RECOVERY_CYCLE_ELAPSED] = 0
                it[Keys.IS_RECOVERY_CYCLE_RUNNING] = true
            }
        }
        return true
    }

    private suspend fun checkAndResetRecoveryUnlocks() {
        context.dataStore.edit {
            // Read every time, even with no cycle running, so a cycle that starts later only
            // counts time from its own start.
            val elapsedNow = recoveryCycleClock.trustedElapsedMillis()
            if (it[Keys.IS_RECOVERY_CYCLE_RUNNING] != true) return@edit
            val period = (it[Keys.RECOVERY_RESET_WEEKS] ?: DEFAULT_RESET_PERIOD_WEEKS) * weekMillis
            val elapsed = (it[Keys.RECOVERY_CYCLE_ELAPSED] ?: 0) + elapsedNow
            if (elapsed >= period) {
                it[Keys.RECOVERY_REMAINING] = DEFAULT_RECOVERY_UNLOCKS
                it[Keys.RECOVERY_CYCLE_ELAPSED] = 0
                it[Keys.IS_RECOVERY_CYCLE_RUNNING] = false
            } else {
                it[Keys.RECOVERY_CYCLE_ELAPSED] = elapsed
            }
        }
    }

    suspend fun showIntroScreen(): Boolean = context.dataStore.data.first()[Keys.SHOW_INTRO] ?: true

    suspend fun setShowIntroScreen(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_INTRO] = show }
    }

    suspend fun hasShownBatteryOptimizationPrompt(): Boolean =
        context.dataStore.data.first()[Keys.SHOWN_BATTERY_OPTIMIZATION_PROMPT] ?: false

    suspend fun setHasShownBatteryOptimizationPrompt(shown: Boolean) {
        context.dataStore.edit { it[Keys.SHOWN_BATTERY_OPTIMIZATION_PROMPT] = shown }
    }

    suspend fun selectedAppIcon(): AppIcon {
        val name = context.dataStore.data.first()[Keys.SELECTED_APP_ICON] ?: return DefaultAppIcon
        return AppIcon.entries.firstOrNull { it.name == name } ?: DefaultAppIcon
    }

    suspend fun setSelectedAppIcon(icon: AppIcon) {
        context.dataStore.edit { it[Keys.SELECTED_APP_ICON] = icon.name }
    }

    suspend fun useLeftHandedLayout(): Boolean =
        context.dataStore.data.first()[Keys.USE_LEFT_HANDED_LAYOUT] ?: false

    suspend fun setUseLeftHandedLayout(useLeftHanded: Boolean) {
        context.dataStore.edit { it[Keys.USE_LEFT_HANDED_LAYOUT] = useLeftHanded }
    }

    suspend fun hasCompletedFirstSession(): Boolean =
        context.dataStore.data.first()[Keys.HAS_COMPLETED_FIRST_SESSION] ?: false

    suspend fun setHasCompletedFirstSession(completed: Boolean) {
        context.dataStore.edit { it[Keys.HAS_COMPLETED_FIRST_SESSION] = completed }
    }

    /** Mirrors RatingManager.swift: prompt after 3 launches, once per app version. */
    suspend fun recordLaunchAndShouldPromptReview(appVersion: String): Boolean {
        val prefs = context.dataStore.data.first()
        val count = (prefs[Keys.LAUNCH_COUNT] ?: 0) + 1
        val lastPromptedVersion = prefs[Keys.LAST_REVIEW_PROMPT_VERSION]
        context.dataStore.edit { it[Keys.LAUNCH_COUNT] = count }

        if (count < 3 || lastPromptedVersion == appVersion) return false
        context.dataStore.edit { it[Keys.LAST_REVIEW_PROMPT_VERSION] = appVersion }
        return true
    }

    companion object {
        const val DEFAULT_EMERGENCY_UNBLOCKS = 3
        const val DEFAULT_RECOVERY_UNLOCKS = 2
        const val DEFAULT_RESET_PERIOD_WEEKS = 4
    }
}
