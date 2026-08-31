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
