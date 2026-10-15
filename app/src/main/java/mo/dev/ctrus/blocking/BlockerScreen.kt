package mo.dev.ctrus.blocking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mo.dev.ctrus.R
import kotlin.math.sqrt

private val DarkBackground = Color(0xFF1F1F1F)
private val LightBackground = Color(0xFFEAEAEA)
private val DarkSecondaryText = Color(0xFF98989D)
private val LightSecondaryText = Color(0xFF8A8A8E)

/**
 * Block surface shown in place of a blocked app or site, mirroring iOS's stock Screen Time shield
 * as Ctrus configures it (`themedStockConfiguration()` in ShieldConfigurationExtension.swift, iOS
 * b5a33c8): the system's own translucent background and copy ("Restricted" / "You cannot use X
 * because it is restricted."), with only the "nosign" icon and the OK button in the theme color.
 * Follows the system's light/dark mode, like the iOS shield.
 *
 * [backgroundAlpha] is below 1 only when the window can blur what's behind it (see
 * BlockerActivity), so the blocked app shows through blurred, never sharp.
 *
 * Hosted by [BlockerActivity].
 */
@Composable
fun BlockerScreen(appLabel: String, themeColor: Color, backgroundAlpha: Float, onDismiss: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val background = if (dark) DarkBackground else LightBackground
    val primaryText = if (dark) Color.White else Color.Black
    val secondaryText = if (dark) DarkSecondaryText else LightSecondaryText

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background.copy(alpha = backgroundAlpha))
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 104.dp, start = 32.dp, end = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NoSignIcon(color = themeColor, modifier = Modifier.size(62.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.blocker_title),
                style = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold),
                color = primaryText,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.blocker_body, appLabel),
                style = TextStyle(fontSize = 19.sp, lineHeight = 25.sp),
                color = secondaryText,
                textAlign = TextAlign.Center,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .size(width = 150.dp, height = 45.dp)
                .clip(CircleShape)
                .background(themeColor)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.blocker_ok),
                style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
                color = Color.White,
            )
        }
    }
}

/** SF Symbols' "nosign": a ring with a diagonal bar from top-left to bottom-right. */
@Composable
private fun NoSignIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val strokeWidth = size.minDimension * 0.085f
        val radius = (size.minDimension - strokeWidth) / 2f
        drawCircle(color = color, radius = radius, style = Stroke(width = strokeWidth))
        // The bar ends where it meets the ring's inner edge.
        val reach = (radius - strokeWidth / 2f) / sqrt(2f)
        drawLine(
            color = color,
            start = Offset(center.x - reach, center.y - reach),
            end = Offset(center.x + reach, center.y + reach),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Butt,
        )
    }
}
