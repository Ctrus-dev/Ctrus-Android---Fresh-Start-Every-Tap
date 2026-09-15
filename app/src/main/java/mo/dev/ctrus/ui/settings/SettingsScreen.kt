package mo.dev.ctrus.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.R
import mo.dev.ctrus.icon.AppIcon
import mo.dev.ctrus.permissions.BackgroundLaunchHelpUtil
import mo.dev.ctrus.ui.common.GlassIconButton
import mo.dev.ctrus.theme.ThemeColorOption
import mo.dev.ctrus.theme.ThemeManager
import mo.dev.ctrus.util.DateFormatters
import mo.dev.ctrus.util.clickableNoRipple
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeManager: ThemeManager,
    isUsageAccessGranted: Boolean,
    isBatteryOptimizationExempt: Boolean,
    onRequestAccessibility: () -> Unit,
    onRequestBatteryOptimizationExemption: () -> Unit,
    appVersion: String,
    selectedAppIcon: AppIcon,
    onSelectAppIcon: (AppIcon) -> Unit,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onValidateUnlockCode: suspend (String) -> Boolean,
    remainingRecoveryUnlocks: Int,
    recoveryResetDateMillis: Long? = null,
    deviceId: String? = null,
    useLeftHandedLayout: Boolean = false,
    onUseLeftHandedLayoutChange: (Boolean) -> Unit = {},
) {
    var showInvalidCodeAlert by remember { mutableStateOf(false) }
    var unlockCode by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var showBackgroundLaunchHelp by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            // Matches ProfileInsightsScreen's header: the close button on its own row, with the
            // big bold title below it — not a Material TopAppBar's inline title.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                GlassIconButton(
                    onClick = onDismiss,
                    icon = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.settings_close_content_description),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
        ) {
            item {
                SettingsSection(title = stringResource(R.string.settings_section_theme)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = null,
                            tint = themeManager.selectedColorOption.color
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.settings_appearance_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.settings_appearance_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    SettingsDivider()
                    ThemeColorRow(themeManager)
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_section_app_icon)) {
                    AppIconRow(
                        selected = selectedAppIcon,
                        onSelect = onSelectAppIcon
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_section_recovery)) {
                    SettingsLinkRow(title = stringResource(R.string.settings_get_unlock_code)) { onOpenUrl("https://recover.ctrus.net") }
                    if (deviceId != null) {
                        SettingsDivider()
                        val clipboard = LocalClipboard.current
                        val deviceIdLabel = stringResource(R.string.settings_device_id)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(deviceIdLabel, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(2.dp))
                                Text(deviceId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            // IconButton's default 48dp minimum touch target left its icon
                            // sitting well short of the row's own edge, unlike every other
                            // row's trailing content — see "+ Add Tag"'s own kdoc for the
                            // same fix applied there.
                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                IconButton(onClick = {
                                    coroutineScope.launch {
                                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(deviceIdLabel, deviceId)))
                                    }
                                }) {
                                    Icon(
                                        Icons.Filled.ContentCopy,
                                        contentDescription = stringResource(R.string.settings_copy_device_id_content_description),
                                        tint = themeManager.selectedColorOption.color,
                                    )
                                }
                            }
                        }
                    }
                    SettingsDivider()
                    val hasUnlockRemaining = remainingRecoveryUnlocks > 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = unlockCode,
                            onValueChange = { unlockCode = it },
                            placeholder = { Text(stringResource(R.string.settings_enter_code_placeholder)) },
                            singleLine = true,
                            enabled = !isVerifying && hasUnlockRemaining,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent,
                            ),
                            // OutlinedTextField's own default ~16dp start content padding stacks
                            // on top of this row's 16dp inset, landing the placeholder a further
                            // 16dp in — noticeably adrift from every other row's text, which all
                            // start flush at that same 16dp. Nothing else here is asked to line
                            // up against the field's actual layout bounds, so an offset (purely
                            // visual — the touch target moves with it) is enough to cancel that
                            // built-in padding out without rebuilding this as a bare BasicTextField.
                            modifier = Modifier.weight(1f).offset(x = (-16).dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        if (isVerifying) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            // Same fix as the copy button above and "+ Add Tag": TextButton's own
                            // minimum touch target and content padding otherwise leave "Desbloquear"
                            // stopping well short of the row's actual right edge.
                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                TextButton(
                                    enabled = unlockCode.isNotEmpty() && hasUnlockRemaining,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    onClick = {
                                        isVerifying = true
                                        coroutineScope.launch {
                                            val valid = onValidateUnlockCode(unlockCode)
                                            isVerifying = false
                                            if (valid) {
                                                unlockCode = ""
                                            } else {
                                                showInvalidCodeAlert = true
                                            }
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.settings_unlock_button), color = themeManager.selectedColorOption.color)
                                }
                            }
                        }
                    }
                    SettingsDivider()
                    RecoveryUnlockStatusRow(
                        remainingUnlocks = remainingRecoveryUnlocks,
                        resetDateMillis = recoveryResetDateMillis,
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_section_accessibility)) {
                    CustomToggleRow(
                        title = stringResource(R.string.settings_left_handed_layout_title),
                        description = stringResource(R.string.settings_left_handed_layout_desc),
                        checked = useLeftHandedLayout,
                        onCheckedChange = onUseLeftHandedLayoutChange,
                    )
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_section_about)) {
                    SettingsRow(title = stringResource(R.string.settings_version)) {
                        Text(stringResource(R.string.settings_version_value, appVersion), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SettingsDivider()
                    // Tapping always re-shows the disclosure-then-Settings flow — even when already
                    // granted, that's a harmless way to jump straight to the system Accessibility
                    // page, and simpler than making the row conditionally clickable.
                    SettingsRow(
                        title = stringResource(R.string.settings_accessibility_access),
                        onClick = onRequestAccessibility,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (isUsageAccessGranted) Color(0xFF34C759) else Color(0xFFFF3B30),
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(if (isUsageAccessGranted) R.string.settings_authorized else R.string.settings_not_authorized),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    SettingsDivider()
                    // Tapping anywhere in this block — title row or caption — relaunches the
                    // system's own "exempt this app" dialog; even when already exempt, that's a
                    // harmless no-op there, and simpler than making it conditionally clickable.
                    // Wrapped in one clickable Column instead of leaving SettingsRow's own onClick
                    // in place, so the caption below shares the same tap target and ripple.
                    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onRequestBatteryOptimizationExemption)) {
                        SettingsRow(title = stringResource(R.string.settings_battery_optimization)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            if (isBatteryOptimizationExempt) Color(0xFF34C759) else Color(0xFFFF3B30),
                                            CircleShape
                                        )
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    // "Authorized"/"Not Authorized" reads backwards paired with this
                                    // row's title — being exempt means optimization is OFF, so this
                                    // states that directly instead.
                                    stringResource(if (isBatteryOptimizationExempt) R.string.settings_battery_optimization_off else R.string.settings_battery_optimization_on),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        // Only relevant while the setting still needs fixing — once exempt (green/
                        // "Off"), the recommendation is moot and stays hidden.
                        if (!isBatteryOptimizationExempt) {
                            Text(
                                stringResource(R.string.settings_battery_optimization_caption),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                            )
                        }
                    }
                    SettingsDivider()
                    SettingsLinkRow(title = stringResource(R.string.settings_background_launch_help)) {
                        showBackgroundLaunchHelp = true
                    }
                    SettingsDivider()
                    SettingsRow(title = stringResource(R.string.settings_made_in)) {
                        Text("🇵🇹")
                    }
                }
            }

            item {
                OpenSourceFooter(
                    themeColor = themeManager.selectedColorOption.color,
                    onOpenUrl = onOpenUrl,
                )
            }
        }
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

    if (showInvalidCodeAlert) {
        AlertDialog(
            onDismissRequest = { showInvalidCodeAlert = false },
            title = { Text(stringResource(R.string.settings_invalid_code_title)) },
            text = { Text(stringResource(R.string.settings_invalid_code_body)) },
            confirmButton = {
                TextButton(onClick = { showInvalidCodeAlert = false }) { Text(stringResource(R.string.common_ok)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeColorRow(themeManager: ThemeManager) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.settings_theme_color), modifier = Modifier.weight(1f))

        // The Box anchors the DropdownMenu to just this chip (not the whole row), so it opens
        // next to where the user tapped, on the right, instead of under the row's left edge.
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickableNoRipple { expanded = true }
            ) {
                Text(
                    stringResource(themeManager.selectedColorOption.displayNameRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                ThemeColorOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.displayNameRes)) },
                        onClick = {
                            themeManager.select(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Mirrors SettingsView's footer: open-source attribution, sitting in the normal scroll flow
 * right after About rather than floating over whatever section is on screen while scrolling.
 * Moved here from the required-permissions screen (see AccessibilityPermissionScreen's kdoc).
 */
@Composable
private fun OpenSourceFooter(themeColor: Color, onOpenUrl: (String) -> Unit) {
    val text = buildAnnotatedString {
        append(stringResource(R.string.intro_open_source_prefix))
        append(" ")
        withStyle(SpanStyle(color = themeColor)) {
            append(stringResource(R.string.intro_open_source_link))
        }
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 12.dp)
            .clickableNoRipple { onOpenUrl("https://github.com/Ctrus-dev/Ctrus-Android---Fresh-Start-Every-Tap") },
    )
}

/** Mirrors SettingsView's recoveryUnlockStatusText: red once at most 1 unlock remains. */
@Composable
private fun RecoveryUnlockStatusRow(remainingUnlocks: Int, resetDateMillis: Long?) {
    val isLow = remainingUnlocks <= 1
    val statusText = when {
        remainingUnlocks > 1 -> stringResource(R.string.settings_recovery_default_status)
        resetDateMillis == null -> {
            if (remainingUnlocks == 1) {
                stringResource(R.string.settings_recovery_one_left)
            } else {
                stringResource(R.string.settings_recovery_default_status)
            }
        }
        else -> {
            val diffMillis = resetDateMillis - System.currentTimeMillis()
            val hoursRemaining = kotlin.math.ceil(diffMillis / 3_600_000.0).toInt().coerceAtLeast(1)
            val date = DateFormatters.formatMonthDay(Date(resetDateMillis))
            if (remainingUnlocks == 1) {
                if (diffMillis <= 24 * 3_600_000L) {
                    stringResource(R.string.settings_recovery_one_left_hours, hoursRemaining)
                } else {
                    stringResource(R.string.settings_recovery_one_left_date, date)
                }
            } else if (diffMillis <= 24 * 3_600_000L) {
                stringResource(R.string.settings_recovery_resets_in_hours, hoursRemaining)
            } else {
                stringResource(R.string.settings_recovery_resets_on_date, date)
            }
        }
    }
    val statusColor = if (isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.History,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
    }
}

@Composable
private fun AppIconRow(selected: AppIcon, onSelect: (AppIcon) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        AppIcon.entries.forEach { option ->
            val isSelected = option == selected
            val label = stringResource(option.labelRes)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Image(
                        painter = painterResource(id = option.drawableRes),
                        contentDescription = label,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(14.dp))
                            // Applying a 0.dp border unconditionally (even to mean "no border")
                            // still rendered a faint hairline on every icon — only attach the
                            // border modifier at all when this one is actually selected.
                            .then(
                                if (isSelected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .clickableNoRipple { onSelect(option) }
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-6).dp)
                                .size(18.dp)
                                .background(Color.White, CircleShape)
                                .padding(2.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
