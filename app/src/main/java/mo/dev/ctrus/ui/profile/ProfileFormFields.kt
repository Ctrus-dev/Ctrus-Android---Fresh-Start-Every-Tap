package mo.dev.ctrus.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.R
import mo.dev.ctrus.data.PhysicalUnblockItem
import mo.dev.ctrus.data.PhysicalUnblockType
import mo.dev.ctrus.nfc.NfcAvailability
import mo.dev.ctrus.nfc.NfcScanController
import mo.dev.ctrus.strategy.BlockingStrategy
import mo.dev.ctrus.ui.settings.CustomToggleRow
import mo.dev.ctrus.ui.settings.SettingsDivider
import mo.dev.ctrus.ui.settings.SettingsRow
import mo.dev.ctrus.util.DomainValidator

/**
 * The actual field content for each profile-form section, shared between [ProfileFormScreen]
 * (all sections shown at once, matching BlockedProfileView.swift) and
 * [GuidedProfileCreationScreen] (one section per step, matching
 * GuidedBlockedProfileCreationView.swift's `BlockedProfile*Fields` reuse). Each is
 * self-contained for any section-local state (NFC scan status, the installed-app list, the
 * in-progress domain text field) so both hosts stay simple.
 */

@Composable
fun NameField(draft: ProfileDraft, onDraftChange: (ProfileDraft) -> Unit, disabled: Boolean) {
    OutlinedTextField(
        value = draft.name,
        onValueChange = { onDraftChange(draft.copy(name = it)) },
        placeholder = { Text(stringResource(R.string.field_profile_name_placeholder)) },
        singleLine = true,
        enabled = !disabled,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun StrategyFields(draft: ProfileDraft, onDraftChange: (ProfileDraft) -> Unit, availableStrategies: List<BlockingStrategy>, disabled: Boolean) {
    availableStrategies.forEachIndexed { index, strategy ->
        if (index > 0) SettingsDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = strategy.id == draft.strategyId,
                onClick = { onDraftChange(draft.copy(strategyId = strategy.id)) },
                enabled = !disabled,
            )
            Column {
                Text(stringResource(strategy.displayNameRes), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(strategy.descriptionRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AppsFields(draft: ProfileDraft, onDraftChange: (ProfileDraft) -> Unit, disabled: Boolean) {
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .let { if (disabled) it else it.clickable { showPicker = true } },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(if (draft.enableAllowMode) R.string.app_picker_title_allow else R.string.app_picker_title_restrict),
            color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(
        if (draft.selectedPackages.isEmpty()) stringResource(R.string.app_picker_no_apps_selected) else pluralStringResource(R.plurals.apps_selected_count, draft.selectedPackages.size, draft.selectedPackages.size),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SettingsDivider()
    CustomToggleRow(
        title = stringResource(R.string.field_allow_only_selected_apps_title),
        description = stringResource(R.string.field_allow_only_selected_apps_desc),
        checked = draft.enableAllowMode,
        enabled = !disabled,
        onCheckedChange = { onDraftChange(draft.copy(enableAllowMode = it, selectedPackages = emptySet())) },
    )
    SettingsDivider()
    CustomToggleRow(
        title = stringResource(R.string.field_block_websites_in_browser_title),
        description = stringResource(R.string.field_block_websites_in_browser_desc),
        checked = draft.enableBrowserBlocking,
        enabled = !disabled,
        onCheckedChange = { onDraftChange(draft.copy(enableBrowserBlocking = it)) },
    )

    if (showPicker) {
        AppPickerDialog(
            initialSelectedPackages = draft.selectedPackages,
            allowMode = draft.enableAllowMode,
            onDismiss = { showPicker = false },
            onDone = { packages ->
                onDraftChange(draft.copy(selectedPackages = packages))
                showPicker = false
            },
        )
    }
}

@Composable
fun DomainsFields(draft: ProfileDraft, onDraftChange: (ProfileDraft) -> Unit, disabled: Boolean) {
    var newDomainText by remember { mutableStateOf("") }
    var domainError by remember { mutableStateOf<String?>(null) }
    val domainAlreadyExists = stringResource(R.string.field_domain_already_exists)
    val domainInvalid = stringResource(R.string.field_domain_invalid)

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newDomainText,
            onValueChange = { newDomainText = it; domainError = null },
            placeholder = { Text(stringResource(R.string.field_domain_placeholder)) },
            singleLine = true,
            enabled = !disabled,
            isError = domainError != null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f),
        )
        TextButton(
            enabled = !disabled && newDomainText.isNotBlank() && draft.domains.size < 50,
            onClick = {
                val trimmed = newDomainText.trim().lowercase()
                when {
                    draft.domains.contains(trimmed) -> domainError = domainAlreadyExists
                    !DomainValidator.isValid(trimmed) -> domainError = domainInvalid
                    else -> {
                        onDraftChange(draft.copy(domains = draft.domains + trimmed))
                        newDomainText = ""
                    }
                }
            },
        ) { Text(stringResource(R.string.common_add)) }
    }
    domainError?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    if (draft.domains.isNotEmpty()) {
        SettingsDivider()
        draft.domains.forEach { domain ->
            SettingsRow(
                title = domain,
                trailing = {
                    IconButton(onClick = { onDraftChange(draft.copy(domains = draft.domains - domain)) }, enabled = !disabled) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.field_remove_domain_content_description, domain))
                    }
                },
            )
        }
    }
    SettingsDivider()
    CustomToggleRow(
        title = stringResource(R.string.field_allow_only_selected_domains_title),
        description = stringResource(R.string.field_allow_only_selected_domains_desc),
        checked = draft.enableAllowModeDomains,
        enabled = !disabled,
        onCheckedChange = { onDraftChange(draft.copy(enableAllowModeDomains = it, enableAdultContentBlocking = if (it) false else draft.enableAdultContentBlocking)) },
    )
}

@Composable
fun PhysicalUnlocksFields(draft: ProfileDraft, onDraftChange: (ProfileDraft) -> Unit, nfcScanController: NfcScanController, disabled: Boolean) {
    var isScanningTag by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    val scanEmptyError = stringResource(R.string.field_nfc_scan_empty)
    val scanDuplicateError = stringResource(R.string.field_nfc_scan_duplicate)

    DisposableEffect(isScanningTag) {
        if (isScanningTag) {
            nfcScanController.startScan { code ->
                val normalized = PhysicalUnblockItem.normalizedCodeValue(code, PhysicalUnblockType.NFC)
                val alreadyPresent = draft.physicalUnblockItems.any {
                    PhysicalUnblockItem.normalizedCodeValue(it.codeValue, it.type) == normalized
                }
                when {
                    normalized.isEmpty() -> scanError = scanEmptyError
                    alreadyPresent -> scanError = scanDuplicateError
                    else -> {
                        val nextIndex = draft.physicalUnblockItems.size + 1
                        onDraftChange(
                            draft.copy(physicalUnblockItems = draft.physicalUnblockItems + PhysicalUnblockItem(name = "NFC $nextIndex", codeValue = normalized)),
                        )
                    }
                }
                isScanningTag = false
            }
        }
        onDispose { nfcScanController.stopScan() }
    }

    if (draft.physicalUnblockItems.isNotEmpty()) {
        draft.physicalUnblockItems.forEachIndexed { index, item ->
            if (index > 0) SettingsDivider()
            SettingsRow(
                title = item.name,
                trailing = {
                    IconButton(
                        onClick = { onDraftChange(draft.copy(physicalUnblockItems = draft.physicalUnblockItems - item)) },
                        enabled = !disabled,
                    ) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.field_remove_tag_content_description, item.name)) }
                },
            )
        }
        SettingsDivider()
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        TextButton(enabled = !disabled, onClick = { isScanningTag = true }) { Text("+ " + stringResource(R.string.field_add_tag)) }
    }

    if (isScanningTag || scanError != null) {
        AlertDialog(
            onDismissRequest = { isScanningTag = false; scanError = null },
            title = { Text(stringResource(R.string.field_scan_tag_title)) },
            text = {
                when {
                    nfcScanController.availability == NfcAvailability.NO_HARDWARE -> Text(stringResource(R.string.field_nfc_no_hardware))
                    nfcScanController.availability == NfcAvailability.DISABLED -> Text(stringResource(R.string.field_nfc_disabled))
                    scanError != null -> Text(scanError!!)
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.field_nfc_hold_near_tag), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { isScanningTag = false; scanError = null }) { Text(stringResource(R.string.common_ok)) } },
        )
    }
}

@Composable
fun BreaksFields(draft: ProfileDraft, onDraftChange: (ProfileDraft) -> Unit, disabled: Boolean) {
    CustomToggleRow(
        title = stringResource(R.string.field_allow_timed_breaks_title),
        description = stringResource(R.string.field_allow_timed_breaks_desc),
        checked = draft.enableBreaks,
        enabled = !disabled,
        onCheckedChange = { onDraftChange(draft.copy(enableBreaks = it)) },
    )
    if (draft.enableBreaks) {
        SettingsDivider()
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15).forEach { minutes ->
                val selected = draft.breakTimeInMinutes == minutes
                TextButton(enabled = !disabled, onClick = { onDraftChange(draft.copy(breakTimeInMinutes = minutes)) }) {
                    Text(stringResource(R.string.common_min_short, minutes), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SettingsDivider()
        CustomToggleRow(
            title = stringResource(R.string.field_allow_multiple_breaks_title),
            description = stringResource(R.string.field_allow_multiple_breaks_desc),
            checked = draft.allowMultipleBreaks,
            enabled = !disabled,
            onCheckedChange = { onDraftChange(draft.copy(allowMultipleBreaks = it)) },
        )
    }
}

@Composable
fun SafeguardsFields(draft: ProfileDraft, onDraftChange: (ProfileDraft) -> Unit, disabled: Boolean) {
    CustomToggleRow(
        title = stringResource(R.string.field_prevent_app_deletion_title),
        description = stringResource(R.string.field_prevent_app_deletion_desc),
        checked = draft.enableStrictMode,
        enabled = !disabled,
        onCheckedChange = { onDraftChange(draft.copy(enableStrictMode = it)) },
    )
    SettingsDivider()
    CustomToggleRow(
        title = stringResource(R.string.field_prevent_installs_title),
        description = stringResource(R.string.field_prevent_installs_desc),
        checked = draft.enableBlockAppInstallation,
        enabled = !disabled,
        onCheckedChange = { onDraftChange(draft.copy(enableBlockAppInstallation = it)) },
    )
}
