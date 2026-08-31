package mo.dev.ctrus.icon

import androidx.annotation.StringRes
import mo.dev.ctrus.R

/**
 * The four launcher-icon variants, matching the `<activity-alias>` entries in AndroidManifest.xml
 * and the preview art already used in Settings (drawable/ic_app_icon_*). Mirrors iOS's
 * AppIconPicker.swift options — independent of the in-app accent color (ThemeColorOption); a user
 * can run the Lime theme with the Dark icon, same as on iOS.
 */
enum class AppIcon(@StringRes val labelRes: Int, val drawableRes: Int, val aliasClassName: String) {
    Orange(R.string.color_orange, R.drawable.ic_app_icon_orange, "mo.dev.ctrus.OrangeIconAlias"),
    Lime(R.string.color_lime, R.drawable.ic_app_icon_lime, "mo.dev.ctrus.LimeIconAlias"),
    Lemon(R.string.color_lemon, R.drawable.ic_app_icon_lemon, "mo.dev.ctrus.LemonIconAlias"),
    Dark(R.string.color_dark, R.drawable.ic_app_icon_dark, "mo.dev.ctrus.DarkIconAlias"),
}

val DefaultAppIcon = AppIcon.Orange
