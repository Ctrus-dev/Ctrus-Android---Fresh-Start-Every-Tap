package mo.dev.ctrus.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.R
import mo.dev.ctrus.permissions.BackgroundLaunchHelpUtil
import mo.dev.ctrus.permissions.OemBackgroundGuide
import mo.dev.ctrus.permissions.XiaomiAutostartUtil
import mo.dev.ctrus.ui.common.GlassIconButton

private val GrantedGreen = Color(0xFF34C759)
private val MissingRed = Color(0xFFFF3B30)

/**
 * Every permission/setting Ctrus depends on, grouped so Settings itself only needs one row:
 * "Required" are the ones that gate starting a session (same set as Home's PermissionsAlertSheet),
 * "Recommended" are the ones that can't be verified or don't block (battery optimization, the
 * per-brand background steps, and the background pop-up help).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsSheet(
    isAccessibilityEnabled: Boolean,
    isExactAlarmGranted: Boolean,
    isAutostartGranted: Boolean,
    isBatteryOptimizationExempt: Boolean,
    onRequestAccessibility: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestAutostart: () -> Unit,
    onRequestBatteryOptimizationExemption: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val oemGuide = remember { OemBackgroundGuide.forDevice() }
    var showOemGuide by remember { mutableStateOf(false) }
    var showBackgroundLaunchHelp by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                // Full height like Settings (whose Scaffold fills the sheet), not just as tall as
                // the content.
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                GlassIconButton(onClick = onDismiss, icon = Icons.Filled.Close, contentDescription = stringResource(R.string.settings_close_content_description))
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.settings_permissions), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
            }

            SettingsSection(title = stringResource(R.string.permissions_required_header)) {
                // Each is tappable only while missing; once granted there's nothing to do here.
                PermissionStatusRow(
                    title = stringResource(R.string.settings_accessibility_access),
                    granted = isAccessibilityEnabled,
                    onClick = onRequestAccessibility,
                )
                // "Alarms & reminders" only exists from Android 12; below that exact alarms always work.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingsDivider()
                    PermissionStatusRow(
                        title = stringResource(R.string.settings_exact_alarms),
                        granted = isExactAlarmGranted,
                        onClick = onRequestExactAlarm,
                    )
                }
                if (XiaomiAutostartUtil.isApplicable(context)) {
                    SettingsDivider()
                    PermissionStatusRow(
                        title = stringResource(R.string.settings_autostart),
                        granted = isAutostartGranted,
                        onClick = onRequestAutostart,
                    )
                }
            }

            SettingsSection(title = stringResource(R.string.permissions_recommended_header)) {
                // Tapping anywhere in this block relaunches the system's "exempt this app" dialog;
                // when already exempt that's a harmless no-op, so it stays tappable.
                Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onRequestBatteryOptimizationExemption)) {
                    SettingsRow(
                        title = stringResource(R.string.settings_battery_optimization),
                        contentPadding = PaddingValues(
                            start = 16.dp, top = 16.dp, end = 16.dp,
                            bottom = if (isBatteryOptimizationExempt) 16.dp else 2.dp,
                        ),
                    ) {
                        // "Authorized" reads backwards here: being exempt means optimization is OFF.
                        StatusLabel(
                            granted = isBatteryOptimizationExempt,
                            text = stringResource(if (isBatteryOptimizationExempt) R.string.settings_battery_optimization_off else R.string.settings_battery_optimization_on),
                        )
                    }
                    if (!isBatteryOptimizationExempt) {
                        Text(
                            stringResource(R.string.settings_battery_optimization_caption),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 48.dp, bottom = 16.dp),
                        )
                    }
                }
                if (oemGuide != null) {
                    SettingsDivider()
                    SettingsRow(title = stringResource(R.string.oem_keep_alive_title), showChevron = true, onClick = { showOemGuide = true })
                }
                SettingsDivider()
                SettingsLinkRow(title = stringResource(R.string.settings_background_launch_help)) { showBackgroundLaunchHelp = true }
            }
        }
    }

    if (showOemGuide && oemGuide != null) {
        AlertDialog(
            onDismissRequest = { showOemGuide = false },
            title = { Text(stringResource(R.string.oem_keep_alive_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.oem_keep_alive_intro))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(oemGuide.stepsRes))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showOemGuide = false
                    oemGuide.open(context)
                }) { Text(stringResource(R.string.background_launch_help_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showOemGuide = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (showBackgroundLaunchHelp) {
        AlertDialog(
            onDismissRequest = { showBackgroundLaunchHelp = false },
            title = { Text(stringResource(R.string.background_launch_help_title)) },
            text = { Text(stringResource(R.string.background_launch_help_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showBackgroundLaunchHelp = false
                    BackgroundLaunchHelpUtil.openSettings(context)
                }) { Text(stringResource(R.string.background_launch_help_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundLaunchHelp = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun PermissionStatusRow(title: String, granted: Boolean, onClick: () -> Unit) {
    SettingsRow(title = title, onClick = if (granted) null else onClick) {
        StatusLabel(
            granted = granted,
            text = stringResource(if (granted) R.string.settings_authorized else R.string.settings_not_authorized),
        )
    }
}

/** Green/red dot plus a short status, as on every permission row. */
@Composable
fun StatusLabel(granted: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(if (granted) GrantedGreen else MissingRed, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
