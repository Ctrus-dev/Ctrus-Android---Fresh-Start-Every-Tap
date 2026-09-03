package mo.dev.ctrus.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.theme.FixedLightSecondaryText

/**
 * Shared "frosted glass" icon button — a translucent white rounded square with a thin white
 * border, matching HomeScreen's launcher-bar icon buttons (Manage Profiles / Settings). Reused
 * everywhere a header/toolbar icon needs the same look, regardless of what's behind it — a solid
 * theme color, a Material-adaptive sheet surface, or Home's pastel background. The chip itself is
 * always the same translucent white/bordered look; only [tint] adapts, defaulting to white in
 * system dark mode (where the Material-adaptive sheet screens sit on a near-black background,
 * making a dark icon on a barely-there glass chip go invisible) and a fixed dark tint otherwise.
 * A screen with its own fixed, non-adaptive background (e.g. the always-colorful active-session
 * screen) should pass an explicit [tint] instead of relying on this default.
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = if (isSystemInDarkTheme()) Color.White else FixedLightSecondaryText,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint.copy(alpha = if (enabled) 1f else 0.4f))
    }
}
