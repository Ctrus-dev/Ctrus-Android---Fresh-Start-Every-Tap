package mo.dev.ctrus.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.R
import mo.dev.ctrus.util.DateFormatters
import java.util.Date

private val EmergencyRed = Color(0xFFFF3B30)
private val EmergencyGreen = Color(0xFF34C759)

/**
 * Android equivalent of EmergencyView.swift + BreakGlassButton.swift, reached from the
 * "Emergency" button on the active-session screen. The "break the glass" gesture is ported as a
 * hold-to-confirm gate (matching the same [HoldToConfirmButton] used to start/stop breaks)
 * rather than iOS's procedural crack-shatter animation — same friction-against-accidental-taps
 * intent, without the bespoke Canvas rendering.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyView(
    remaining: Int,
    resetPeriodWeeks: Int,
    resetDateMillis: Long?,
    onResetPeriodSelected: (Int) -> Unit,
    onConfirmUnblock: () -> Unit,
    onDismiss: () -> Unit,
) {
    // skipPartiallyExpanded avoids an intermediate "half open" resting state — without it, on a
    // device with the 3-button navigation bar the sheet can first settle partially behind that
    // bar, requiring an extra manual swipe up to reach the fully expanded, correctly-inset position.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasRemaining = remaining > 0

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.emergency_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                EmergencyResetGearButton(resetPeriodWeeks = resetPeriodWeeks, onResetPeriodSelected = onResetPeriodSelected)
            }
            Spacer(Modifier.height(4.dp))
            EmergencyResetStatusRow(resetDateMillis = resetDateMillis)
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (hasRemaining) Icons.Filled.Shield else Icons.Filled.GppBad,
                        contentDescription = null,
                        tint = if (hasRemaining) EmergencyGreen else EmergencyRed,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            stringResource(R.string.emergency_unblocks_remaining_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "$remaining",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (hasRemaining) MaterialTheme.colorScheme.onSurface else EmergencyRed,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.emergency_limited_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                HoldToConfirmButton(
                    title = stringResource(R.string.emergency_hold_button),
                    backgroundColor = if (hasRemaining) EmergencyRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    contentColor = if (hasRemaining) EmergencyRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    onConfirm = { if (hasRemaining) onConfirmUnblock() },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(if (hasRemaining) R.string.emergency_reduce_notice else R.string.emergency_none_remaining),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasRemaining) MaterialTheme.colorScheme.onSurfaceVariant else EmergencyRed,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmergencyResetGearButton(resetPeriodWeeks: Int, onResetPeriodSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.emergency_reset_period_content_description), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            listOf(2 to R.string.emergency_reset_weeks_2, 4 to R.string.emergency_reset_weeks_4).forEach { (weeks, labelRes) ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (resetPeriodWeeks == weeks) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(stringResource(labelRes))
                        }
                    },
                    onClick = { onResetPeriodSelected(weeks); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun EmergencyResetStatusRow(resetDateMillis: Long?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            resetStatusText(resetDateMillis),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun resetStatusText(resetDateMillis: Long?): String {
    if (resetDateMillis == null) return ""
    val diffMillis = resetDateMillis - System.currentTimeMillis()
    return if (diffMillis <= 24 * 3_600_000L) {
        val hoursRemaining = kotlin.math.ceil(diffMillis / 3_600_000.0).toInt().coerceAtLeast(1)
        stringResource(R.string.emergency_resets_in_hours, hoursRemaining)
    } else {
        stringResource(R.string.emergency_resets_on_date, DateFormatters.formatMonthDay(Date(resetDateMillis)))
    }
}
