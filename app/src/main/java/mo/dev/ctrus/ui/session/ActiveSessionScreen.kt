package mo.dev.ctrus.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mo.dev.ctrus.R

data class ActiveSessionUiState(
    val profileName: String,
    val displayTime: String,
    val focusMessageIndex: Int,
    val isBreakActive: Boolean,
    val isBreakAvailable: Boolean,
)

/** Mirrors ActiveProfileSessionView.swift: solid theme-color background, header, timer, actions. */
@Composable
fun ActiveSessionScreen(
    state: ActiveSessionUiState,
    themeColor: Color,
    onChartTapped: () -> Unit,
    onCloseTapped: () -> Unit,
    onBreakHeld: () -> Unit,
    onEmergencyTapped: () -> Unit,
    onStopTapped: () -> Unit,
) {
    // The background is the selected theme color and nothing else — no wash blended on top —
    // so it always reads as pure Orange/Lime/Lemon, whichever the user picked. Text/icon colors
    // used to also adapt to the system's light/dark setting (white in dark mode, black in
    // light); this screen now stays fixed to the dark-mode look regardless, matching how the
    // rest of this port's pastel/solid-color screens (Home, Intro) are also fixed rather than
    // system-adaptive.
    val primaryContent = Color.White
    val supportingContent = Color.White.copy(alpha = 0.78f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(themeColor)
    ) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(24.dp)) {
            Header(state, primaryContent, onChartTapped, onCloseTapped)

            Spacer(Modifier.height(40.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = state.displayTime,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = primaryContent
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringArrayResource(R.array.focus_messages)[state.focusMessageIndex],
                    style = MaterialTheme.typography.titleMedium,
                    color = supportingContent,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.weight(1f))

            // All text buttons on this screen — Emergency included — share the same dark glass
            // fill; only the Emergency button's text/icon breaks from white, staying red as a
            // warning cue. Both the size (17pt semibold, SwiftUI's `.headline`) and the color
            // (`Color.red` = UIColor.systemRed) match ActiveSessionActionButton's label exactly.
            val darkButtonBackground = Color.Black
            val darkButtonAlpha = 0.42f
            val buttonTextStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            val emergencyRed = Color(0xFFFF3B30)

            if (state.isBreakAvailable) {
                HoldToConfirmButton(
                    title = stringResource(if (state.isBreakActive) R.string.session_hold_stop_break else R.string.session_hold_start_break),
                    backgroundColor = darkButtonBackground,
                    contentColor = primaryContent,
                    icon = Icons.Filled.Coffee,
                    textStyle = buttonTextStyle,
                    restingAlpha = darkButtonAlpha,
                    fillAlpha = 0.65f,
                    onConfirm = onBreakHeld
                )
                Spacer(Modifier.height(12.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onEmergencyTapped,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    border = null,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = darkButtonBackground.copy(alpha = darkButtonAlpha)),
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = emergencyRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.session_emergency_button),
                        color = emergencyRed,
                        maxLines = 1,
                        style = buttonTextStyle,
                    )
                }
                OutlinedButton(
                    onClick = onStopTapped,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    border = null,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = darkButtonBackground.copy(alpha = darkButtonAlpha)),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, tint = primaryContent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.session_stop_button),
                        color = primaryContent,
                        maxLines = 1,
                        style = buttonTextStyle,
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(
    state: ActiveSessionUiState,
    primaryContent: Color,
    onChartTapped: () -> Unit,
    onCloseTapped: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = state.profileName,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = primaryContent
        )

        Row {
            DarkIconButton(icon = Icons.Filled.BarChart, contentColor = primaryContent, onClick = onChartTapped)
            Spacer(Modifier.width(8.dp))
            DarkIconButton(icon = Icons.Filled.Close, contentColor = primaryContent, onClick = onCloseTapped)
        }
    }
}

@Composable
private fun DarkIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, contentColor: Color, onClick: () -> Unit) {
    // Same Box+clickable structure as HomeScreen's HomeGlassIconButton (Settings/Manage Profiles
    // icons) — not an IconButton, whose built-in minimum-touch-target padding was inflating both
    // the visual footprint and the gap between these two icons past what that spacing uses. Just
    // a dark fill in place of Home's white one.
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.42f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = contentColor)
    }
}
