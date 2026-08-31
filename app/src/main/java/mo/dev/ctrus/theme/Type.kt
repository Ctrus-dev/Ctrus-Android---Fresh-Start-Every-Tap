package mo.dev.ctrus.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import mo.dev.ctrus.R

/**
 * Approximates the bold, rounded system-font look used throughout the iOS app
 * (e.g. .font(.largeTitle).fontWeight(.bold) on profile/session titles).
 */
val CtrusTypography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
)

/**
 * Substitute for iOS's `.font(.system(size:, weight: .bold, design: .rounded))`, used on the
 * big session-duration numbers (WeeklySessionChart/MonthlySessionChart totals, SessionRow).
 * Android has no built-in rounded system font, so this bundles Nunito — a Google Fonts /
 * OFL-licensed variable font — pinned to an ExtraBold instance as the closest visual match.
 */
@OptIn(ExperimentalTextApi::class)
val CtrusRoundedBold = FontFamily(
    Font(
        R.font.nunito_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800)),
    ),
)
