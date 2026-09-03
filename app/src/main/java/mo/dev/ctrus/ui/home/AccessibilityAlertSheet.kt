package mo.dev.ctrus.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
 * Android equivalent of HomeAlertDetailView.swift (specifically the `.screenTimeAccess` case —
 * the only alert type ported here; iOS's `.scheduleOutOfSync` alert has no Android equivalent,
 * since this port uses AlarmManager, not a DeviceActivityCenter schedule that can silently stop).
 * Reached by tapping the red pill on Home, or by tapping Start while Accessibility access is off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilityAlertSheet(themeColor: Color, onDismiss: () -> Unit, onAllowTapped: () -> Unit) {
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
                stringResource(R.string.home_accessibility_alert_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.home_accessibility_alert_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAllowTapped,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = themeColor, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(stringResource(R.string.intro_allow_button))
            }
        }
    }
}
