package mo.dev.ctrus.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import mo.dev.ctrus.R
import android.graphics.Color as AndroidColor

/**
 * Mirrors ThemeManager.availableColors on iOS (Ctrus/Utils/ThemeManager.swift).
 * The iOS values are Display P3; these are the closest sRGB approximation, matching
 * the app icon fills, so keep both in sync if the palette ever changes.
 */
enum class ThemeColorOption(@StringRes val displayNameRes: Int, val color: Color) {
    Orange(R.string.color_orange, Color(0xFFF3A641)),
    Lime(R.string.color_lime, Color(0xFF4CB65F)),
    Lemon(R.string.color_lemon, Color(0xFFF7D045)),
}

val DefaultThemeColorOption = ThemeColorOption.Orange

/**
 * Equivalent of ThemeManager.pastelBackground: same hue, desaturated and near-white,
 * used for pastel screens (Home/Intro-style surfaces).
 */
fun pastelBackground(color: Color): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(color.toArgb(), hsv)
    hsv[1] *= 0.22f
    hsv[2] = 0.99f
    return Color(AndroidColor.HSVToColor(hsv))
}

/**
 * Mirrors Color.fixedLightPrimaryText / fixedLightSecondaryText in ThemeManager.swift:
 * UIColor.label / .secondaryLabel resolved against a light trait collection, always used
 * (regardless of system dark mode) on screens — Home, Intro — that render against the
 * pastel background. secondaryLabel is a semi-transparent black on iOS, so it's kept as
 * an alpha color here too rather than pre-blended, matching how it composites on iOS.
 */
val FixedLightPrimaryText = Color(0xFF000000)
val FixedLightSecondaryText = Color(0x993C3C43)

/**
 * iOS system colors used by every screen that (unlike Home/Intro) follows the system's
 * light/dark setting — Settings, Manage Profiles, the profile form, Insights. Values are
 * UIKit's documented light/dark constants for the token in each name's comment, so the app's
 * "grouped list" screens look like an iOS Form (grouped background behind white/near-black
 * cards) in both modes instead of forcing one mode everywhere.
 */
object CtrusSystemColors {
    // systemGroupedBackground
    val backgroundLight = Color(0xFFF2F2F7)
    val backgroundDark = Color(0xFF000000)

    // secondarySystemGroupedBackground (the grouped-row/card fill)
    val surfaceVariantLight = Color(0xFFFFFFFF)
    val surfaceVariantDark = Color(0xFF1C1C1E)

    // label
    val onBackgroundLight = FixedLightPrimaryText
    val onBackgroundDark = Color(0xFFFFFFFF)

    // secondaryLabel
    val onSurfaceVariantLight = FixedLightSecondaryText
    val onSurfaceVariantDark = Color(0x99EBEBF5)

    // separator
    val separatorLight = Color(0x4A3C3C43)
    val separatorDark = Color(0xA6545458)
}
