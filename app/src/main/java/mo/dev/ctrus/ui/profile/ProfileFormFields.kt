package mo.dev.ctrus.ui.profile

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.R
import mo.dev.ctrus.data.PhysicalUnblockItem
import mo.dev.ctrus.data.PhysicalUnblockType
import mo.dev.ctrus.nfc.NfcScanController
import mo.dev.ctrus.strategy.BlockingStrategy
import mo.dev.ctrus.ui.common.NfcScanDialog
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
        // The hosting card no longer supplies any content padding of its own (every field here
        // is expected to carry its own uniform 16.dp, matching every SettingsRow/CustomToggleRow
        // elsewhere) — this is the one field with no natural "row" to hang that padding off, so
        // it's applied directly here instead.
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            // Without a border to visually contain it, the default 56dp-tall field reads as an
            // oversized blank pill — trimmed down now that nothing else frames its empty space.
            .height(52.dp),
    )
}

// RadioButton's default 48dp touch target (way past its own 20dp circle) was stretching this
// row's height, pushing the description below it far away from the title it belongs to.
private val StrategyRadioBoxSize = 24.dp
private val StrategyRadioToTitleGap = 8.dp

@Composable
fun StrategyFields(draft: ProfileDraft, onDraftChange: (ProfileDraft) -> Unit, availableStrategies: List<BlockingStrategy>, disabled: Boolean) {
    availableStrategies.forEachIndexed { index, strategy ->
        if (index > 0) SettingsDivider()
        // Matches SettingsDivider's own 16dp inset — this column had none, so the radio circle
        // started to the left of where the divider above/below it begins.
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides StrategyRadioBoxSize) {
                    RadioButton(
                        selected = strategy.id == draft.strategyId,
                        onClick = { onDraftChange(draft.copy(strategyId = strategy.id)) },
                        enabled = !disabled,
                    )
                }
                Spacer(Modifier.width(StrategyRadioToTitleGap))
                Text(stringResource(strategy.displayNameRes), style = MaterialTheme.typography.bodyLarge)
            }
            // Tight, matching Device ID's own title-to-subtitle gap — this description is
            // describing the title right above it, not a separate block.
            Spacer(Modifier.height(2.dp))
            // Starts under the radio ball itself now, not indented to the title — the ball is
            // the thing being described just as much as the title text next to it is.
            Text(
                stringResource(strategy.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AppsFields(draft: ProfileDraft, onDraftChange: (ProfileDraft) -> Unit, disabled: Boolean) {
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Top matches the app's standard 16.dp card-edge inset (this is the card's first
            // row); bottom stays tight — 2.dp, matching Device ID's own title-to-subtitle gap —
            // since a matching 16.dp there would push the subtitle text far away from the title
            // it describes instead of reading as one group.
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 2.dp)
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
        // Matches CustomToggleRow's and SettingsDivider's own 16dp inset — this row and its
        // subtitle had none, so they started to the left of where the divider begins, and the
        // gap down to that divider was 6dp instead of the 16dp every other row/divider gap uses.
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
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

    // 16.dp on every side — was vertical-only, leaving this row flush against the card's left
    // edge (the OutlinedTextField's own internal padding isn't a substitute for an explicit
    // inset here since the trailing "Adicionar" button has no such built-in gap on its side).
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
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
    val context = LocalContext.current
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
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        // TextButton otherwise enforces a 48dp minimum touch target on top of its own content
        // padding, which read as a big blank balloon around this short "+ Add Tag" label.
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            TextButton(
                enabled = !disabled,
                onClick = { isScanningTag = true },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) { Text("+ " + stringResource(R.string.field_add_tag)) }
        }
    }

    if (isScanningTag || scanError != null) {
        NfcScanDialog(
            availability = nfcScanController.availability,
            onDismiss = { isScanningTag = false; scanError = null },
            onOpenNfcSettings = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
            errorMessage = scanError,
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
        // 16.dp on every side — was vertical-only, leaving these minute chips flush against the
        // card's left edge unlike every other row.
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

/** Matches the delete-button red in ManageProfilesScreen/HomeScreen's EmergencyRed. */
val ProtectionDisclaimerRed = Color(0xFFFF3B30)

/**
 * The "may not work on every Android brand" caption for "Prevent App Deletion", shown inside the
 * card/section right below the toggle it qualifies — only its "Warning" lead-in is colored, the
 * rest reads like any other description text.
 */
@Composable
fun AppDeletionOemDisclaimer(modifier: Modifier = Modifier) {
    val normalColor = MaterialTheme.colorScheme.onSurfaceVariant
    // "Aviso:"/"Warning:" — colon included — is the red label; only the sentence after it (and
    // the space before that sentence) reads in the normal description color.
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = ProtectionDisclaimerRed)) {
            append(stringResource(R.string.field_prevent_app_deletion_oem_disclaimer_prefix))
            append(":")
        }
        withStyle(SpanStyle(color = normalColor)) {
            append(" ")
            append(stringResource(R.string.field_prevent_app_deletion_oem_disclaimer))
        }
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier,
    )
}

/**
 * "Prevent New App Installs" (`enableBlockAppInstallation`) has no UI here anymore — dropped from
 * both the guided flow and Edit Profile since it's the toggle least likely to hold up, per
 * [AppDeletionOemDisclaimer]. The underlying `ProfileDraft`/entity field is untouched (still
 * plumbed through create/save) so a profile that already had it set from before this change keeps
 * that value; new/edited profiles just can't turn it on anymore.
 */
@Composable
fun SafeguardsFields(draft: ProfileDraft, onDraftChange: (ProfileDraft) -> Unit, disabled: Boolean) {
    CustomToggleRow(
        title = stringResource(R.string.field_prevent_app_deletion_title),
        description = stringResource(R.string.field_prevent_app_deletion_desc),
        checked = draft.enableStrictMode,
        enabled = !disabled,
        onCheckedChange = { onDraftChange(draft.copy(enableStrictMode = it)) },
    )
    // Bottom matches the app's standard 16.dp card-edge inset (this is the card's last element);
    // top stays tight — 4.dp — since it's still describing the toggle right above it.
    AppDeletionOemDisclaimer(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp))
}
