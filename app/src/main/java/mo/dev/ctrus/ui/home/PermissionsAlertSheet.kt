package mo.dev.ctrus.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.R

/**
 * Android equivalent of HomeAlertDetailView.swift's `.screenTimeAccess` case. Accessibility access
 * (iOS's Screen Time equivalent) is the only OS permission the app can't function without, so this
 * sheet only ever covers that one — battery-optimization exemption is a separate, non-blocking
 * recommendation surfaced through its own one-time dialog and a Settings row (see
 * BatteryOptimizationUtil's kdoc). Reached by tapping the red pill on Home, or by tapping Start
 * while accessibility is missing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsAlertSheet(
    isAccessibilityEnabled: Boolean,
    onDismiss: () -> Unit,
    onFixAccessibility: () -> Unit,
) {
    // skipPartiallyExpanded avoids an intermediate "half open" resting state — without it, on a
    // device with the 3-button navigation bar the sheet can first settle partially behind that
    // bar, requiring an extra manual swipe up to reach the fully expanded, correctly-inset position.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 8.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.GppMaybe,
                contentDescription = null,
                tint = Color(0xFFFF3B30),
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.home_permissions_alert_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.home_permissions_alert_sheet_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            PermissionChecklistRow(
                title = stringResource(R.string.settings_accessibility_access),
                granted = isAccessibilityEnabled,
                actionLabel = stringResource(R.string.battery_optimization_allow),
                onFix = onFixAccessibility,
            )
        }
    }
}

@Composable
private fun PermissionChecklistRow(title: String, granted: Boolean, actionLabel: String, onFix: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Weighted so a long title (translated strings run longer than English) shrinks to make
        // room for the fix button/checkmark instead of squeezing it off the edge.
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (granted) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF34C759))
        } else {
            TextButton(onClick = onFix, shape = RoundedCornerShape(50)) {
                Text(actionLabel)
            }
        }
    }
}
