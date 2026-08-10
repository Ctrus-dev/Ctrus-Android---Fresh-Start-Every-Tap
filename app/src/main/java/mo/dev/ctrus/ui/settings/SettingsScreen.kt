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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.R
import mo.dev.ctrus.theme.ThemeColorOption
import mo.dev.ctrus.theme.ThemeManager
import mo.dev.ctrus.util.clickableNoRipple

/** Matches the real preview artwork ported from the iOS asset catalog (AppIconPicker.swift). */
private enum class AppIconAsset(val label: String, val drawableRes: Int) {
    Orange("Orange", R.drawable.ic_app_icon_orange),
    Lime("Lime", R.drawable.ic_app_icon_lime),
    Lemon("Lemon", R.drawable.ic_app_icon_lemon),
    Dark("Dark", R.drawable.ic_app_icon_dark),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeManager: ThemeManager,
    isBlocking: Boolean,
    isUsageAccessGranted: Boolean,
    appVersion: String,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onResetBlockingState: () -> Unit,
    onValidateUnlockCode: (String) -> Boolean,
    onDebugModeClick: () -> Unit = {},
) {
    var selectedAppIcon by remember { mutableStateOf(AppIconAsset.Orange) }
    var showResetAlert by remember { mutableStateOf(false) }
    var showInvalidCodeAlert by remember { mutableStateOf(false) }
    var unlockCode by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
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
                SettingsSection(title = "Theme") {
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
                            Text("Appearance", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Customize the look of your app",
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
                SettingsSection(title = "App Icon") {
                    AppIconRow(
                        selected = selectedAppIcon,
                        onSelect = { selectedAppIcon = it }
                    )
                }
            }

            item {
                SettingsSection(title = "Help") {
                    SettingsRow(title = "Debug Mode", showChevron = true, onClick = onDebugModeClick)
                    SettingsDivider()
                    SettingsLinkRow(title = "Blocking Native Apps") { onOpenUrl("https://ctrus.net") }
                    if (!isBlocking) {
                        SettingsDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            TextButton(onClick = { showResetAlert = true }) {
                                Text(
                                    "Reset Blocking State",
                                    color = themeManager.selectedColorOption.color
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection(title = "Locked Out and Lost Your Ctrus?") {
                    SettingsLinkRow(title = "Get an Unlock Code") { onOpenUrl("https://ctrus.net") }
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
                            placeholder = { Text("Enter code") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            enabled = unlockCode.isNotEmpty(),
                            onClick = {
                                if (onValidateUnlockCode(unlockCode)) {
                                    unlockCode = ""
                                } else {
                                    showInvalidCodeAlert = true
                                }
                            }
                        ) {
                            Text("Unlock", color = themeManager.selectedColorOption.color)
                        }
                    }
                }
            }

            item {
                SettingsSection(title = "About") {
                    SettingsRow(title = "Version") {
                        Text("v$appVersion", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SettingsDivider()
                    SettingsRow(title = "Usage Access") {
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
                                if (isUsageAccessGranted) "Authorized" else "Not Authorized",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    SettingsDivider()
                    SettingsRow(title = "Made in") {
                        Text("🇵🇹")
                    }
                    SettingsDivider()
                    SettingsLinkRow(title = "Based on Foqos - Tap to Block") {
                        onOpenUrl("https://www.foqos.app")
                    }
                }
            }
        }
    }

    if (showResetAlert) {
        AlertDialog(
            onDismissRequest = { showResetAlert = false },
            title = { Text("Reset Blocking State") },
            text = {
                Text("This will clear all app restrictions and remove any ghost schedules. Only use this if you're locked out and no profile is active.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetAlert = false
                    onResetBlockingState()
                }) { Text("Reset", color = Color(0xFFFF3B30)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetAlert = false }) { Text("Cancel") }
            }
        )
    }

    if (showInvalidCodeAlert) {
        AlertDialog(
            onDismissRequest = { showInvalidCodeAlert = false },
            title = { Text("Invalid Code") },
            text = { Text("That unlock code isn't valid. Visit ctrus.net to get one.") },
            confirmButton = {
                TextButton(onClick = { showInvalidCodeAlert = false }) { Text("OK") }
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
            Text("Theme Color", modifier = Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickableNoRipple { expanded = true }
            ) {
                Text(
                    themeManager.selectedColorOption.displayName,
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
                            Text(option.displayName)
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
private fun AppIconRow(selected: AppIconAsset, onSelect: (AppIconAsset) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(AppIconAsset.entries) { option ->
            val isSelected = option == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Image(
                        painter = painterResource(id = option.drawableRes),
                        contentDescription = option.label,
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
                Text(option.label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
