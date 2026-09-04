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
        val EMERGENCY_LAST_RESET = longPreferencesKey("emergency_last_reset_epoch_milli")

        val RECOVERY_REMAINING = intPreferencesKey("recovery_unlocks_remaining")
        val RECOVERY_RESET_WEEKS = intPreferencesKey("recovery_reset_period_weeks")
        val RECOVERY_LAST_UNLOCK = longPreferencesKey("recovery_last_unlock_epoch_milli")

        val DEVICE_ID = stringPreferencesKey("recovery_device_id")
        val SHOW_INTRO = booleanPreferencesKey("show_intro_screen")
        val SHOWN_BATTERY_OPTIMIZATION_PROMPT = booleanPreferencesKey("shown_battery_optimization_prompt")
        val LAUNCH_COUNT = intPreferencesKey("launch_count")
        val SELECTED_APP_ICON = stringPreferencesKey("selected_app_icon")
        val LAST_REVIEW_PROMPT_VERSION = stringPreferencesKey("last_review_prompt_version")
    }

    private val weekMillis = 7L * 24 * 60 * 60 * 1000

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

    suspend fun setResetPeriodWeeksEmergency(weeks: Int) {
        context.dataStore.edit {
            it[Keys.EMERGENCY_RESET_WEEKS] = weeks
            it[Keys.EMERGENCY_LAST_RESET] = Instant.now().toEpochMilli()
        }
    }

    suspend fun nextEmergencyResetDate(): Instant? {
        val last = context.dataStore.data.first()[Keys.EMERGENCY_LAST_RESET] ?: return null
        val weeks = resetPeriodWeeksEmergency()
        return Instant.ofEpochMilli(last + weeks * weekMillis)
    }

    suspend fun consumeEmergencyUnblock(): Boolean {
        val remaining = remainingEmergencyUnblocks()
        if (remaining <= 0) return false
        context.dataStore.edit { it[Keys.EMERGENCY_REMAINING] = remaining - 1 }
        return true
    }

    private suspend fun checkAndResetEmergencyUnblocks() {
        val prefs = context.dataStore.data.first()
        val lastReset = prefs[Keys.EMERGENCY_LAST_RESET]
        val weeks = prefs[Keys.EMERGENCY_RESET_WEEKS] ?: DEFAULT_RESET_PERIOD_WEEKS
        val now = Instant.now().toEpochMilli()

        if (lastReset == null) {
            context.dataStore.edit { it[Keys.EMERGENCY_LAST_RESET] = now }
            return
        }
        if (now - lastReset >= weeks * weekMillis) {
            context.dataStore.edit {
                it[Keys.EMERGENCY_REMAINING] = DEFAULT_EMERGENCY_UNBLOCKS
                it[Keys.EMERGENCY_LAST_RESET] = now
            }
        }
    }

    suspend fun remainingRecoveryUnlocks(): Int {
        checkAndResetRecoveryUnlocks()
        return context.dataStore.data.first()[Keys.RECOVERY_REMAINING] ?: DEFAULT_RECOVERY_UNLOCKS
    }

    suspend fun nextRecoveryResetDate(): Instant? {
        val prefs = context.dataStore.data.first()
        val last = prefs[Keys.RECOVERY_LAST_UNLOCK] ?: return null
        val remaining = prefs[Keys.RECOVERY_REMAINING] ?: DEFAULT_RECOVERY_UNLOCKS
        if (remaining > 0) return null
        val weeks = prefs[Keys.RECOVERY_RESET_WEEKS] ?: DEFAULT_RESET_PERIOD_WEEKS
        return Instant.ofEpochMilli(last + weeks * weekMillis)
    }

    suspend fun consumeRecoveryUnlock(): Boolean {
        val remaining = remainingRecoveryUnlocks()
        if (remaining <= 0) return false
        context.dataStore.edit {
            it[Keys.RECOVERY_REMAINING] = remaining - 1
            it[Keys.RECOVERY_LAST_UNLOCK] = Instant.now().toEpochMilli()
        }
        return true
    }

    private suspend fun checkAndResetRecoveryUnlocks() {
        val prefs = context.dataStore.data.first()
        val last = prefs[Keys.RECOVERY_LAST_UNLOCK] ?: return
        val weeks = prefs[Keys.RECOVERY_RESET_WEEKS] ?: DEFAULT_RESET_PERIOD_WEEKS
        if (Instant.now().toEpochMilli() - last >= weeks * weekMillis) {
            context.dataStore.edit {
                it[Keys.RECOVERY_REMAINING] = DEFAULT_RECOVERY_UNLOCKS
                it.remove(Keys.RECOVERY_LAST_UNLOCK)
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
