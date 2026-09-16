package mo.dev.ctrus.ui.profile

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.data.PhysicalUnblockItem
import mo.dev.ctrus.nfc.NfcScanController
import mo.dev.ctrus.strategy.BlockingStrategy
import mo.dev.ctrus.ui.common.GlassIconButton
import mo.dev.ctrus.ui.settings.SettingsSection

/**
 * Edit form, matching BlockedProfileView.swift: every section shown at once (not paginated —
 * iOS only paginates for first-time *creation*, see [GuidedProfileCreationScreen]).
 * `physicalUnblockItems` is required to save, matching iOS's `.missingPhysicalUnlock` guard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileFormScreen(
    existingProfile: BlockedProfileEntity,
    availableStrategies: List<BlockingStrategy>,
    isBlockingGlobally: Boolean,
    nfcScanController: NfcScanController,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        selectedPackages: List<String>,
        strategyId: String,
        domains: List<String>,
        enableAllowMode: Boolean,
        enableBrowserBlocking: Boolean,
        enableAllowModeDomains: Boolean,
        enableAdultContentBlocking: Boolean,
        physicalUnblockItems: List<PhysicalUnblockItem>,
        enableBreaks: Boolean,
        breakTimeInMinutes: Int,
        allowMultipleBreaks: Boolean,
        enableStrictMode: Boolean,
        enableBlockAppInstallation: Boolean,
    ) -> Unit,
    onDelete: () -> Unit,
    onDuplicate: (newName: String) -> Unit,
    onInsightsTapped: () -> Unit,
) {
    val disabled = isBlockingGlobally
    val initial = remember { existingProfile.toDraft() }
    var draft by remember { mutableStateOf(initial) }
    val isDirty = draft != initial

    var showDiscardAlert by remember { mutableStateOf(false) }
    var showDeleteAlert by remember { mutableStateOf(false) }
    var showMissingUnlockAlert by remember { mutableStateOf(false) }
    var showDuplicatePrompt by remember { mutableStateOf(false) }
    var duplicateName by remember { mutableStateOf("") }

    fun attemptDismiss() {
        if (isDirty) showDiscardAlert = true else onDismiss()
    }

    fun attemptSave() {
        // TODO TEMP: NFC gate disabled for testing on hardware without NFC — restore this guard
        // before shipping.
        // if (draft.physicalUnblockItems.isEmpty()) {
        //     showMissingUnlockAlert = true
        //     return
        // }
        onSave(
            draft.name.trim(), draft.selectedPackages.toList(), draft.strategyId, draft.domains,
            draft.enableAllowMode, draft.enableBrowserBlocking, draft.enableAllowModeDomains, draft.enableAdultContentBlocking,
            draft.physicalUnblockItems, draft.enableBreaks, draft.breakTimeInMinutes, draft.allowMultipleBreaks,
            draft.enableStrictMode, draft.enableBlockAppInstallation,
        )
    }

    val duplicateSuffix = stringResource(R.string.profile_form_duplicate_name_suffix, draft.name)

    Scaffold(
        topBar = {
            // Matches ProfileInsightsScreen's header: close/action icons on their own row, with
            // the big bold title below it — not a Material TopAppBar's inline title.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(onClick = ::attemptDismiss, icon = Icons.Filled.Close, contentDescription = stringResource(R.string.profile_form_cancel_content_description))
                    Row {
                        GlassIconButton(onClick = onInsightsTapped, icon = Icons.Filled.BarChart, contentDescription = stringResource(R.string.profile_form_insights_content_description))
                        if (!disabled) {
                            Spacer(Modifier.width(8.dp))
                            var showMenu by remember { mutableStateOf(false) }
                            Box {
                                GlassIconButton(onClick = { showMenu = true }, icon = Icons.Filled.MoreVert, contentDescription = stringResource(R.string.profile_form_actions_content_description))
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    shape = RoundedCornerShape(20.dp),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.profile_form_duplicate_menu)) },
                                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                        onClick = { showMenu = false; duplicateName = duplicateSuffix; showDuplicatePrompt = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.profile_form_delete_menu)) },
                                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                        onClick = { showMenu = false; showDeleteAlert = true },
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            GlassIconButton(
                                onClick = ::attemptSave,
                                icon = Icons.Filled.Check,
                                contentDescription = stringResource(R.string.profile_form_update_content_description),
                                enabled = draft.name.isNotBlank(),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.profile_form_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        ) {
            if (disabled) {
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                        Text(stringResource(R.string.profile_form_active_session_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // No extra Modifier.padding(horizontal = ...) wrapper around any field below —
            // SettingsSection already gives its content a uniform 16.dp inset on every side, and
            // each field's own row padding is already normalized to match; adding another
            // horizontal-only layer here (as before) pushed these fields further in from the
            // sides than from the top/bottom, unlike their identical counterparts mid-wizard.
            item {
                SettingsSection(title = stringResource(R.string.profile_form_section_name)) {
                    NameField(draft, { draft = it }, disabled)
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.profile_form_section_strategy)) {
                    StrategyFields(draft, { draft = it }, availableStrategies, disabled)
                }
            }
            item {
                SettingsSection(title = stringResource(if (draft.enableAllowMode) R.string.profile_form_section_apps_allowed else R.string.profile_form_section_apps_blocked)) {
                    AppsFields(draft, { draft = it }, disabled)
                }
            }
            item {
                SettingsSection(title = stringResource(if (draft.enableAllowModeDomains) R.string.profile_form_section_domains_allowed else R.string.profile_form_section_domains_blocked)) {
                    DomainsFields(draft, { draft = it }, disabled)
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.profile_form_section_unlocks)) {
                    PhysicalUnlocksFields(draft, { draft = it }, nfcScanController, disabled)
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.profile_form_section_breaks)) {
                    BreaksFields(draft, { draft = it }, disabled)
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.profile_form_section_protection)) {
                    SafeguardsFields(draft, { draft = it }, disabled)
                }
            }
        }
    }

    if (showDiscardAlert) {
        AlertDialog(
            onDismissRequest = { showDiscardAlert = false },
            title = { Text(stringResource(R.string.profile_form_discard_title)) },
            text = { Text(stringResource(R.string.profile_form_discard_body)) },
            confirmButton = { TextButton(onClick = { showDiscardAlert = false; onDismiss() }) { Text(stringResource(R.string.profile_form_discard_confirm), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDiscardAlert = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    if (showDeleteAlert) {
        AlertDialog(
            onDismissRequest = { showDeleteAlert = false },
            title = { Text(stringResource(R.string.profile_form_delete_title)) },
            text = { Text(stringResource(R.string.profile_form_delete_body)) },
            confirmButton = { TextButton(onClick = { showDeleteAlert = false; onDelete() }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteAlert = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    if (showMissingUnlockAlert) {
        AlertDialog(
            onDismissRequest = { showMissingUnlockAlert = false },
            title = { Text(stringResource(R.string.profile_form_missing_unlock_title)) },
            text = { Text(stringResource(R.string.profile_form_missing_unlock_body)) },
            confirmButton = { TextButton(onClick = { showMissingUnlockAlert = false }) { Text(stringResource(R.string.common_ok)) } },
        )
    }

    if (showDuplicatePrompt) {
        AlertDialog(
            onDismissRequest = { showDuplicatePrompt = false },
            title = { Text(stringResource(R.string.profile_form_duplicate_title)) },
            text = {
                OutlinedTextField(
                    value = duplicateName,
                    onValueChange = { duplicateName = it },
                    placeholder = { Text(stringResource(R.string.field_profile_name_placeholder)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                    ),
                )
            },
            confirmButton = {
                TextButton(enabled = duplicateName.isNotBlank(), onClick = { showDuplicatePrompt = false; onDuplicate(duplicateName.trim()) }) { Text(stringResource(R.string.common_create)) }
            },
            dismissButton = { TextButton(onClick = { showDuplicatePrompt = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}
