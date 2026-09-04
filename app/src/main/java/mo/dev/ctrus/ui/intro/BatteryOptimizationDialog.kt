package mo.dev.ctrus.ui.intro

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import mo.dev.ctrus.R

/**
 * Shown once, right after the accessibility onboarding completes, on devices where the OS still
 * allows battery optimization for this app. Several OEM skins (MIUI, ColorOS, FuntouchOS/OriginOS,
 * OxygenOS) kill background services far more aggressively than stock Android, which can silently
 * end blocking mid-session — exempting the app is the one lever Android exposes uniformly to
 * reduce that risk. See BatteryOptimizationUtil.
 */
@Composable
fun BatteryOptimizationDialog(onAllow: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.battery_optimization_title)) },
        text = {
            Text(
                stringResource(R.string.battery_optimization_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onAllow) { Text(stringResource(R.string.settings_battery_optimization_disable_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.battery_optimization_not_now)) }
        },
    )
}
