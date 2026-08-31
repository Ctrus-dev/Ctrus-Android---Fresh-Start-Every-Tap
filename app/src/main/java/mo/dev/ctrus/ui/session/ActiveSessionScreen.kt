package mo.dev.ctrus.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CoffeeMaker
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mo.dev.ctrus.R

data class ActiveSessionUiState(
    val profileName: String,
    val statusMessage: String? = null,
    val displayTime: String,
    val focusMessage: String,
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
    // Mirrors ActiveProfileSessionView's `colorScheme == .dark` checks: the theme-color
    // background stays solid in both modes, but the wash overlaid on it and the text/icon
    // colors drawn over it adapt to the system setting the same way iOS's .primary label
    // and Color(.systemBackground)-based gradient do.
    val dark = isSystemInDarkTheme()
    val primaryContent = if (dark) Color.White else Color.Black
    val supportingContent = if (dark) Color.White.copy(alpha = 0.78f) else Color.Black.copy(alpha = 0.66f)
    val systemBackground = if (dark) Color.Black else Color.White

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(themeColor)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        systemBackground.copy(alpha = 0.02f),
                        systemBackground.copy(alpha = 0.34f)
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(24.dp)) {
            Header(state, primaryContent, supportingContent, onChartTapped, onCloseTapped)

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
                    text = state.focusMessage,
                    style = MaterialTheme.typography.titleMedium,
                    color = supportingContent,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.weight(1f))

            if (state.isBreakAvailable) {
                HoldToConfirmButton(
                    title = stringResource(if (state.isBreakActive) R.string.session_hold_stop_break else R.string.session_hold_start_break),
                    backgroundColor = Color.White,
                    contentColor = primaryContent,
                    onConfirm = onBreakHeld
                )
                Spacer(Modifier.height(12.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onEmergencyTapped,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFF6B5C))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.session_emergency_button), color = Color(0xFFFF6B5C))
                }
                OutlinedButton(
                    onClick = onStopTapped,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text(stringResource(R.string.session_stop_button), color = primaryContent)
                }
            }
        }
    }
}

@Composable
private fun Header(
    state: ActiveSessionUiState,
    primaryContent: Color,
    supportingContent: Color,
    onChartTapped: () -> Unit,
    onCloseTapped: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = state.profileName,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = primaryContent
            )
            state.statusMessage?.let {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CoffeeMaker,
                        contentDescription = null,
                        tint = supportingContent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = it,
                        color = supportingContent,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        Row {
            CircleIconButton(icon = Icons.Filled.BarChart, contentColor = primaryContent, onClick = onChartTapped)
            Spacer(Modifier.width(8.dp))
            CircleIconButton(icon = Icons.Filled.Close, contentColor = primaryContent, onClick = onCloseTapped)
        }
    }
}

@Composable
private fun CircleIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, contentColor: Color, onClick: () -> Unit) {
    // Approximates iOS's `.thinMaterial` circle (an adaptive frosted-glass fill) with a
    // translucent version of the same primary content color used for its icon.
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(42.dp)
            .background(contentColor.copy(alpha = 0.18f), CircleShape)
    ) {
        Icon(icon, contentDescription = null, tint = contentColor)
    }
}
