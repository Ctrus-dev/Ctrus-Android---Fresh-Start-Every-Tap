package mo.dev.ctrus.ui.childmode

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mo.dev.ctrus.R
import mo.dev.ctrus.childmode.ChildModeStore
import mo.dev.ctrus.childmode.ParentCredentials
import mo.dev.ctrus.childmode.ParentTag
import mo.dev.ctrus.nfc.NfcScanController
import mo.dev.ctrus.ui.common.GlassIconButton
import mo.dev.ctrus.ui.common.TagReadDialog
import mo.dev.ctrus.ui.settings.SettingsDivider
import mo.dev.ctrus.ui.settings.SettingsRow
import java.text.DateFormat
import java.util.Date

private enum class ChildModeDestination { MAIN, CHANGE_CODE, PARENT_TAGS, NEW_RECOVERY_KEY, LOG }

/**
 * Settings > Child Mode (spec Part B, section 9), reached only after the parents confirm. Port of
 * iOS's ChildModeSettingsView; its sub-screens are pushed in place, each with a back button.
 */
@Composable
fun ChildModeSettingsScreen(
    themeColor: Color,
    nfcScanController: NfcScanController,
    startsOnLog: Boolean = false,
    onDismiss: () -> Unit,
    onSwitchToNormal: () -> Unit,
) {
    var destination by remember { mutableStateOf(if (startsOnLog) ChildModeDestination.LOG else ChildModeDestination.MAIN) }
    var showSwitchAlert by remember { mutableStateOf(false) }
    val back = { destination = ChildModeDestination.MAIN }

    BackHandler(enabled = destination != ChildModeDestination.MAIN && !startsOnLog, onBack = back)

    when (destination) {
        ChildModeDestination.MAIN -> ChildModePage(
            title = stringResource(R.string.settings_child_section),
            navigationIcon = Icons.Filled.Close,
            onNavigation = onDismiss,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)),
            ) {
                NavigationRow(stringResource(R.string.settings_child_change_code), Icons.Filled.LockReset, themeColor) { destination = ChildModeDestination.CHANGE_CODE }
                SettingsDivider()
                NavigationRow(stringResource(R.string.settings_child_parent_tags), Icons.Filled.Nfc, themeColor) { destination = ChildModeDestination.PARENT_TAGS }
                SettingsDivider()
                NavigationRow(stringResource(R.string.settings_child_new_key), Icons.Filled.Key, themeColor) { destination = ChildModeDestination.NEW_RECOVERY_KEY }
                SettingsDivider()
                NavigationRow(stringResource(R.string.settings_child_log), Icons.AutoMirrored.Filled.ListAlt, themeColor) { destination = ChildModeDestination.LOG }
            }
            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)),
            ) {
                SettingsRow(
                    title = stringResource(R.string.settings_child_to_normal),
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { showSwitchAlert = true },
                )
            }
        }
        ChildModeDestination.CHANGE_CODE -> ChangeParentCodePage(themeColor, onBack = back)
        ChildModeDestination.PARENT_TAGS -> ParentTagsPage(themeColor, nfcScanController, onBack = back)
        ChildModeDestination.NEW_RECOVERY_KEY -> NewRecoveryKeyPage(themeColor, onDone = back)
        ChildModeDestination.LOG -> TamperLogPage(
            themeColor = themeColor,
            navigationIcon = if (startsOnLog) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
            onBack = if (startsOnLog) onDismiss else back,
        )
    }

    if (showSwitchAlert) {
        AlertDialog(
            onDismissRequest = { showSwitchAlert = false },
            title = { Text(stringResource(R.string.settings_child_to_normal)) },
            text = { Text(stringResource(R.string.settings_child_to_normal_body)) },
            confirmButton = {
                TextButton(onClick = { showSwitchAlert = false; onSwitchToNormal() }) {
                    Text(stringResource(R.string.settings_child_to_normal), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showSwitchAlert = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/** A header like Settings' (round button, then a big bold title), with [content] scrolling under it. */
@Composable
private fun ChildModePage(
    title: String,
    navigationIcon: ImageVector,
    onNavigation: () -> Unit,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    // Full height in its sheet, like Settings, instead of only as tall as its content.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
            .padding(bottom = 24.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(
                    onClick = onNavigation,
                    icon = navigationIcon,
                    contentDescription = stringResource(
                        if (navigationIcon == Icons.Filled.Close) R.string.settings_close_content_description else R.string.guided_back_content_description,
                    ),
                )
                Spacer(Modifier.weight(1f))
                trailing()
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun NavigationRow(title: String, icon: ImageVector, themeColor: Color, onClick: () -> Unit) {
    SettingsRow(
        title = title,
        leading = { Icon(icon, contentDescription = null, tint = themeColor) },
        onClick = onClick,
        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}

@Composable
private fun ChangeParentCodePage(themeColor: Color, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var firstCode by remember { mutableStateOf("") }
    var isConfirming by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    ChildModePage(stringResource(R.string.settings_child_change_code), Icons.AutoMirrored.Filled.ArrowBack, onBack) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(
                stringResource(if (isConfirming) R.string.child_pin_again else R.string.child_pin_create),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            key(isConfirming) {
                ParentCodeField(
                    code = code,
                    onCodeChange = { code = it },
                    themeColor = themeColor,
                    enabled = !isSaving,
                    onComplete = { entered ->
                        if (!isConfirming) {
                            if (ParentCredentials.isWeakCode(entered)) {
                                message = context.getString(R.string.child_pin_weak)
                                code = ""
                            } else {
                                message = null
                                firstCode = entered
                                code = ""
                                isConfirming = true
                            }
                        } else if (entered != firstCode) {
                            message = context.getString(R.string.child_pin_mismatch)
                            code = ""
                            firstCode = ""
                            isConfirming = false
                        } else {
                            isSaving = true
                            scope.launch {
                                withContext(Dispatchers.Default) { ParentCredentials.setParentCode(entered) }
                                onBack()
                            }
                        }
                    },
                )
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ParentTagsPage(themeColor: Color, nfcScanController: NfcScanController, onBack: () -> Unit) {
    val context = LocalContext.current
    val parentTags by ChildModeStore.parentTagsFlow.collectAsState()
    var isReadingTag by remember { mutableStateOf(false) }

    ChildModePage(stringResource(R.string.settings_child_parent_tags), Icons.AutoMirrored.Filled.ArrowBack, onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)),
        ) {
            parentTags.forEachIndexed { index, tag ->
                if (index > 0) SettingsDivider()
                key(tag.uid) {
                    ParentTagRow(
                        tag = tag,
                        themeColor = themeColor,
                        // There always has to be at least one.
                        canDelete = parentTags.size > 1,
                        onDelete = { ChildModeStore.parentTags = ChildModeStore.parentTags.filterNot { it.uid == tag.uid } },
                    )
                }
            }
        }
        Text(
            stringResource(R.string.settings_child_parent_tags_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)),
        ) {
            SettingsRow(
                title = stringResource(R.string.child_parent_tag_add_another),
                titleColor = themeColor,
                leading = { Icon(Icons.Filled.Add, contentDescription = null, tint = themeColor) },
                onClick = { isReadingTag = true },
            )
        }
    }

    if (isReadingTag) {
        TagReadDialog(
            nfcScanController = nfcScanController,
            message = stringResource(R.string.child_parent_tag_scan),
            onRead = { read ->
                isReadingTag = false
                val uid = ChildModeStore.normalizedUid(read.uid)
                val current = ChildModeStore.parentTags
                if (current.none { it.uid == uid }) {
                    val label = ChildModeStore.nextParentTagLabel(current) { context.getString(R.string.child_parent_tag_label, it) }
                    ChildModeStore.parentTags = current + ParentTag(uid = uid, url = read.url, label = label)
                }
            },
            onDismiss = { isReadingTag = false },
        )
    }
}

/** iOS 9b780d3: deleted with its own button rather than a swipe, which isn't obvious. */
@Composable
private fun ParentTagRow(tag: ParentTag, themeColor: Color, canDelete: Boolean, onDelete: () -> Unit) {
    SettingsRow(
        title = tag.label,
        leading = { Icon(Icons.Filled.Nfc, contentDescription = null, tint = themeColor) },
        trailing = if (canDelete) {
            {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.error)
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun NewRecoveryKeyPage(themeColor: Color, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var recoveryKey by remember { mutableStateOf<String?>(null) }
    var canConfirm by remember { mutableStateOf(false) }
    var isWorking by remember { mutableStateOf(false) }

    LaunchedEffect(recoveryKey) {
        if (recoveryKey != null) {
            delay(RECOVERY_KEY_CONFIRM_DELAY_MILLIS)
            canConfirm = true
        }
    }
    // Once a key is on screen there's no going back without confirming it was saved.
    BackHandler(enabled = recoveryKey != null) {}

    ChildModePage(
        title = stringResource(R.string.child_recovery_title),
        navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
        onNavigation = { if (recoveryKey == null) onDone() },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            val key = recoveryKey
            if (key == null) {
                Text(stringResource(R.string.settings_child_new_key_warning), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ThemedButton(stringResource(R.string.settings_child_new_key), themeColor, enabled = !isWorking) {
                    isWorking = true
                    scope.launch { recoveryKey = withContext(Dispatchers.Default) { ParentCredentials.makeRecoveryKey() } }
                }
            } else {
                Text(stringResource(R.string.child_recovery_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SelectionContainer {
                    Text(
                        ParentCredentials.formattedRecoveryKey(key),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 26.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                            .padding(vertical = 20.dp),
                    )
                }
                ThemedButton(stringResource(R.string.child_recovery_saved), themeColor, enabled = canConfirm, onClick = onDone)
            }
        }
    }
}

/** The attempt log (spec Part B, section 10). Opening it marks everything as seen. */
@Composable
fun TamperLogPage(themeColor: Color, navigationIcon: ImageVector, onBack: () -> Unit) {
    val entries by ChildModeStore.tamperLogFlow.collectAsState()
    var showClearAlert by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ChildModeStore.tamperLogUnseen = false }

    ChildModePage(
        title = stringResource(R.string.settings_child_log),
        navigationIcon = navigationIcon,
        onNavigation = onBack,
        trailing = {
            if (entries.isNotEmpty()) {
                TextButton(onClick = { showClearAlert = true }) { Text(stringResource(R.string.tamper_clear), color = themeColor) }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)),
        ) {
            if (entries.isEmpty()) {
                SettingsRow(title = stringResource(R.string.tamper_empty), titleColor = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val format = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
                entries.asReversed().forEachIndexed { index, entry ->
                    if (index > 0) SettingsDivider()
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(stringResource(entry.type.titleRes), style = MaterialTheme.typography.bodyLarge)
                        Text(format.format(Date(entry.at)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showClearAlert) {
        AlertDialog(
            onDismissRequest = { showClearAlert = false },
            title = { Text(stringResource(R.string.tamper_clear_title)) },
            text = { Text(stringResource(R.string.tamper_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearAlert = false
                    ChildModeStore.tamperLog = emptyList()
                    ChildModeStore.tamperLogUnseen = false
                }) { Text(stringResource(R.string.tamper_clear), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearAlert = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

@Composable
private fun ThemedButton(text: String, themeColor: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = themeColor, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { Text(text) }
}
