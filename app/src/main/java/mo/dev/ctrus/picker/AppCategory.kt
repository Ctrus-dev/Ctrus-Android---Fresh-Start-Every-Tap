package mo.dev.ctrus.picker

import android.content.pm.ApplicationInfo
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import mo.dev.ctrus.R

/**
 * Groups installed apps for the picker's "block a whole theme, or just some apps within it" view
 * — see AppPickerDialog.kt. Backed by the OS-declared `ApplicationInfo.category` rather than a
 * hand-maintained package-name list: many apps never set one (there's no way to guess a category
 * for those beyond what the OS already knows), so those fall into [Other] rather than being
 * miscategorized by a guess. Icons are Material glyphs rather than iOS's emoji, matching the
 * Material iconography used everywhere else in this app instead of copying that choice literally.
 */
enum class AppCategory(@StringRes val labelRes: Int, val icon: ImageVector) {
    GAME(R.string.app_category_game, Icons.Filled.SportsEsports),
    SOCIAL(R.string.app_category_social, Icons.Filled.Groups),
    PRODUCTIVITY(R.string.app_category_productivity, Icons.Filled.Work),
    NEWS(R.string.app_category_news, Icons.Filled.Newspaper),
    MAPS(R.string.app_category_maps, Icons.Filled.Map),
    AUDIO(R.string.app_category_audio, Icons.Filled.LibraryMusic),
    VIDEO(R.string.app_category_video, Icons.Filled.Movie),
    IMAGE(R.string.app_category_image, Icons.Filled.Photo),
    OTHER(R.string.app_category_other, Icons.Filled.Apps);

    companion object {
        /** Display order: known categories first (alphabetical by their own label), Other last. */
        val displayOrder = listOf(GAME, SOCIAL, PRODUCTIVITY, NEWS, MAPS, AUDIO, VIDEO, IMAGE, OTHER)

        fun from(rawCategory: Int): AppCategory = when (rawCategory) {
            ApplicationInfo.CATEGORY_GAME -> GAME
            ApplicationInfo.CATEGORY_SOCIAL -> SOCIAL
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> PRODUCTIVITY
            ApplicationInfo.CATEGORY_NEWS -> NEWS
            ApplicationInfo.CATEGORY_MAPS -> MAPS
            ApplicationInfo.CATEGORY_AUDIO -> AUDIO
            ApplicationInfo.CATEGORY_VIDEO -> VIDEO
            ApplicationInfo.CATEGORY_IMAGE -> IMAGE
            else -> OTHER
        }
    }
}

fun InstalledApp.appCategory(): AppCategory = AppCategory.from(category)
