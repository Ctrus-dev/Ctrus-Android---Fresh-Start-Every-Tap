package mo.dev.ctrus.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.R
import mo.dev.ctrus.icon.AppIcon
import mo.dev.ctrus.theme.ThemeColorOption
import mo.dev.ctrus.theme.ThemeManager
import mo.dev.ctrus.util.clickableNoRipple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeManager: ThemeManager,
    isUsageAccessGranted: Boolean,
    appVersion: String,
    selectedAppIcon: AppIcon,
    onSelectAppIcon: (AppIcon) -> Unit,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onValidateUnlockCode: suspend (String) -> Boolean,
    deviceId: String? = null,
    onDebugModeClick: () -> Unit = {},
) {
    var showInvalidCodeAlert by remember { mutableStateOf(false) }
    var unlockCode by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.settings_close_content_description))
                    }
                }
            )
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
                            .padding(horizontal = 16.dp, vertical = 14.dp),
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
                                style = MaterialTheme.typography.bodyMedium,
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
                SettingsSection(title = stringResource(R.string.settings_section_help)) {
                    SettingsRow(title = stringResource(R.string.settings_debug_mode), showChevron = true, onClick = onDebugModeClick)
                    SettingsDivider()
                    SettingsLinkRow(title = stringResource(R.string.settings_blocking_native_apps)) { onOpenUrl("https://ctrus.net") }
                }
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_section_recovery)) {
                    SettingsLinkRow(title = stringResource(R.string.settings_get_unlock_code)) { onOpenUrl("https://recover.ctrus.net") }
                    if (deviceId != null) {
                        SettingsDivider()
                        val clipboard = LocalClipboard.current
                        val deviceIdLabel = stringResource(R.string.settings_device_id)
                        SettingsRow(title = deviceIdLabel) {
                            TextButton(onClick = {
                                coroutineScope.launch {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(deviceIdLabel, deviceId)))
                                }
                            }) {
                                Text(deviceId.take(8) + "…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = unlockCode,
                            onValueChange = { unlockCode = it },
                            placeholder = { Text(stringResource(R.string.settings_enter_code_placeholder)) },
                            singleLine = true,
                            enabled = !isVerifying,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        if (isVerifying) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            TextButton(
                                enabled = unlockCode.isNotEmpty(),
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
            }

            item {
                SettingsSection(title = stringResource(R.string.settings_section_about)) {
                    SettingsRow(title = stringResource(R.string.settings_version)) {
                        Text(stringResource(R.string.settings_version_value, appVersion), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SettingsDivider()
                    SettingsRow(title = stringResource(R.string.settings_usage_access)) {
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    SettingsDivider()
                    SettingsRow(title = stringResource(R.string.settings_made_in)) {
                        Text("🇵🇹")
                    }
                    SettingsDivider()
                    SettingsLinkRow(title = stringResource(R.string.settings_based_on_foqos)) {
                        onOpenUrl("https://www.foqos.app")
                    }
                }
            }
        }
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

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.settings_theme_color), modifier = Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickableNoRipple { expanded = true }
            ) {
                Text(
                    stringResource(themeManager.selectedColorOption.displayNameRes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ThemeColorOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(option.color, CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(option.displayNameRes))
                        }
                    },
                    onClick = {
                        themeManager.select(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AppIconRow(selected: AppIcon, onSelect: (AppIcon) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(AppIcon.entries) { option ->
            val isSelected = option == selected
            val label = stringResource(option.labelRes)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Image(
                        painter = painterResource(id = option.drawableRes),
                        contentDescription = label,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(14.dp)
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
