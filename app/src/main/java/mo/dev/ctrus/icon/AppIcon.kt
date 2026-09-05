package mo.dev.ctrus.icon

import androidx.annotation.StringRes
import mo.dev.ctrus.R

/**
 * The four launcher-icon variants, matching the `<activity-alias>` entries in AndroidManifest.xml
 * (which use the real adaptive icons in mipmap/ic_launcher_*). The in-app picker preview uses flat
 * drawable/ic_launcher_*_preview PNGs instead — Compose's painterResource() can't load an
 * <adaptive-icon> XML (throws IllegalArgumentException), only vectors and rasterized assets.
 * Mirrors iOS's AppIconPicker.swift options — independent of the in-app accent color
 * (ThemeColorOption); a user can run the Lime theme with the Dark icon, same as on iOS.
 */
enum class AppIcon(@StringRes val labelRes: Int, val drawableRes: Int, val aliasClassName: String) {
    Orange(R.string.color_orange, R.drawable.ic_launcher_orange_preview, "mo.dev.ctrus.OrangeIconAlias"),
    Lemon(R.string.color_lemon, R.drawable.ic_launcher_lemon_preview, "mo.dev.ctrus.LemonIconAlias"),
    Lime(R.string.color_lime, R.drawable.ic_launcher_lime_preview, "mo.dev.ctrus.LimeIconAlias"),
    Dark(R.string.color_dark, R.drawable.ic_launcher_dark_preview, "mo.dev.ctrus.DarkIconAlias"),
}

val DefaultAppIcon = AppIcon.Orange
