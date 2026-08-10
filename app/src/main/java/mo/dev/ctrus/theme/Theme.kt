package mo.dev.ctrus.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private const val PREFS_NAME = "mo.dev.ctrus.theme"
private const val KEY_THEME_COLOR_NAME = "CtrusThemeColorName"

/**
 * Equivalent of ThemeManager on iOS (Ctrus/Utils/ThemeManager.swift): holds the
 * selected theme color, persisted like the iOS @AppStorage-backed property.
 */
class ThemeManager private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var selectedColorOption by mutableStateOf(loadSavedOption())
        private set

    fun select(option: ThemeColorOption) {
        selectedColorOption = option
        prefs.edit().putString(KEY_THEME_COLOR_NAME, option.name).apply()
    }

    private fun loadSavedOption(): ThemeColorOption {
        val savedName = prefs.getString(KEY_THEME_COLOR_NAME, null) ?: return DefaultThemeColorOption
        return ThemeColorOption.entries.firstOrNull { it.name == savedName } ?: DefaultThemeColorOption
    }

    companion object {
        @Volatile private var instance: ThemeManager? = null

        fun getInstance(context: Context): ThemeManager =
            instance ?: synchronized(this) {
                instance ?: ThemeManager(context).also { instance = it }
            }
    }
}

val LocalThemeManager = staticCompositionLocalOf<ThemeManager> {
    error("No ThemeManager provided — wrap content in CtrusTheme")
}

// The iOS app renders its Settings/list screens in dark mode (near-black background,
// slightly lighter grouped-row cards) — matched here rather than following the
// device's system light/dark setting, since that's what every iOS reference
// screenshot in this project shows.
private val CtrusBackground = Color(0xFF000000)
private val CtrusSurface = Color(0xFF1C1C1E)
private val CtrusSurfaceVariant = Color(0xFF1C1C1E)
private val CtrusOnSurfaceVariant = Color(0xFFAEAEB2)

@Composable
fun CtrusTheme(themeManager: ThemeManager, content: @Composable () -> Unit) {
    val themeColor = themeManager.selectedColorOption.color
    val colorScheme = darkColorScheme(
        primary = themeColor,
        secondary = themeColor,
        tertiary = themeColor,
        background = CtrusBackground,
        surface = CtrusSurface,
        surfaceVariant = CtrusSurfaceVariant,
        onBackground = Color.White,
        onSurface = Color.White,
        onSurfaceVariant = CtrusOnSurfaceVariant,
    )

    CompositionLocalProvider(LocalThemeManager provides themeManager) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CtrusTypography,
            content = content
        )
    }
}
