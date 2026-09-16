package mo.dev.ctrus.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mo.dev.ctrus.theme.CtrusSystemColors

/** Mirrors a SwiftUI `Section("...")` inside an inset-grouped Form. */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, bottom = 6.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    // 20.dp is this app's standard "bubble" corner radius — used uniformly across
                    // cards, sheets, and pill buttons instead of the mix of ad-hoc values (14/18/28dp,
                    // or a height-derived full stadium) that made corners look inconsistent between
                    // screens, and between one card's own left/right vs top/bottom edges.
                    RoundedCornerShape(20.dp)
                ),
            // No content padding here: every row hosted here (SettingsRow, CustomToggleRow,
            // StrategyFields, ...) already carries its own uniform 16.dp on all four sides, which
            // alone gives the first/last row that same 16.dp gap to this card's top/bottom edge —
            // adding another 16.dp here on top of that doubled it to 20.dp. Between two rows this
            // never showed, since each side of the divider between them supplies its own,
            // independent 16.dp margin — only this card's own outer edges were affected.
            content = content
        )
    }
}

@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    // Overridable for the rare row a caption directly follows (e.g. Battery Optimization's) —
    // that pairing needs a tight bottom gap like a title-to-subtitle pair (see Device ID's own
    // 2.dp), not this row's usual 16.dp edge inset, which would read as two unrelated blocks.
    contentPadding: PaddingValues = PaddingValues(16.dp),
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
        if (showChevron) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsLinkRow(title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// IosStyleSwitch's fixed 31dp track is taller than the title's own ~24dp bodyLarge line by 7dp;
// centered next to it (CenterVertically), the title sits 3.5dp below the row's true top edge.
// Shrinking just this row's own top inset by that half-difference — instead of the padding trick
// tried earlier, which under-reported the switch's height to the layout system and let its real,
// unreported bottom edge overlap the description below it — lands the title's top at the same
// 16.dp every other row's text gets, using the row's real (uninflated) height throughout, so nothing
// can ever overlap by construction.
private val ToggleRowTopInset = 12.5.dp

/**
 * Mirrors CustomToggle.swift: title + switch share the title's own line (switch trailing), with
 * the description as its own full-width line below — not a title+description block centered
 * against the switch, which read as the switch belonging to neither line in particular.
 */
@Composable
fun CustomToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = ToggleRowTopInset, bottom = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(16.dp))
            IosStyleSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
        // Tight, matching Device ID's own title-to-subtitle gap — this description is describing
        // the title right above it, not a separate block.
        Spacer(Modifier.height(2.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Recreates SwiftUI's native `Toggle` proportions (51x31pt pill track, thumb nearly filling its
 * height) instead of Material3's `Switch`, which uses a visibly smaller thumb inside a wider
 * track. Keeps standard switch semantics/accessibility (Role.Switch) via [Modifier.toggleable].
 */
@Composable
fun IosStyleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val trackWidth = 51.dp
    val trackHeight = 31.dp
    val thumbDiameter = trackHeight - 4.dp
    val trackOffColor = if (isSystemInDarkTheme()) Color(0xFF39393D) else Color(0xFFE9E9EA)
    val trackColor by animateColorAsState(if (checked) tint else trackOffColor, label = "toggleTrackColor")
    val thumbOffset by animateDpAsState(
        if (checked) trackWidth - thumbDiameter - 2.dp else 2.dp,
        label = "toggleThumbOffset",
    )

    Box(
        modifier = modifier
            .size(trackWidth, trackHeight)
            .clip(CircleShape)
            .background(trackColor)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                enabled = enabled,
                role = Role.Switch,
            )
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbOffset)
                .size(thumbDiameter)
                .align(Alignment.CenterStart)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

@Composable
fun SettingsDivider() {
    // Inset on both ends — matching the row content's own horizontal padding — instead of
    // running flush to the card's raw right edge.
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = if (isSystemInDarkTheme()) CtrusSystemColors.separatorDark else CtrusSystemColors.separatorLight,
    )
}
