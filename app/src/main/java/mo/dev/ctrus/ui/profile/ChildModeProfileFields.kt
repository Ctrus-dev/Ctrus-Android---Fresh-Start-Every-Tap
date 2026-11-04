package mo.dev.ctrus.ui.profile

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.R
import mo.dev.ctrus.childmode.ChildModeStore
import mo.dev.ctrus.childmode.ChildTag
import mo.dev.ctrus.nfc.NfcScanController
import mo.dev.ctrus.ui.common.TagReadDialog
import mo.dev.ctrus.ui.settings.CustomToggleRow
import mo.dev.ctrus.ui.settings.SettingsDivider
import mo.dev.ctrus.ui.settings.SettingsRow

/**
 * Child Mode's Ctrus step (spec Part B, 6.1; iOS's ChildModeProfileTagFields): the parents' Ctrus
 * is always there — the master key of every profile, never copied into it — and can't be changed
 * here; the child's own Ctrus is optional.
 */
@Composable
fun ChildModeProfileTagFields(
    draft: ProfileDraft,
    onDraftChange: (ProfileDraft) -> Unit,
    nfcScanController: NfcScanController,
    disabled: Boolean,
) {
    val themeColor = MaterialTheme.colorScheme.primary
    var isReadingTag by remember { mutableStateOf(false) }

    SettingsRow(
        title = stringResource(R.string.profile_parent_tag_row),
        leading = { Icon(Icons.Filled.Nfc, contentDescription = null, tint = themeColor) },
        trailing = { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = themeColor) },
    )
    SettingsDivider()

    if (draft.childTag != null) {
        SettingsRow(
            title = stringResource(R.string.profile_child_tag_row),
            leading = { Icon(Icons.Filled.Nfc, contentDescription = null, tint = themeColor) },
            trailing = {
                IconButton(
                    onClick = { onDraftChange(draft.copy(childTag = null, childTagCanStop = false)) },
                    enabled = !disabled,
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.field_remove_tag_content_description, stringResource(R.string.profile_child_tag_row)),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )
        SettingsDivider()
        CustomToggleRow(
            title = stringResource(R.string.profile_child_tag_can_stop),
            description = "",
            checked = draft.childTagCanStop,
            enabled = !disabled,
            onCheckedChange = { onDraftChange(draft.copy(childTagCanStop = it)) },
        )
    } else {
        SettingsRow(
            title = stringResource(R.string.profile_child_tag_add),
            titleColor = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant else themeColor,
            leading = { Icon(Icons.Filled.Add, contentDescription = null, tint = themeColor) },
            onClick = if (disabled) null else ({ isReadingTag = true }),
        )
    }

    if (isReadingTag) {
        TagReadDialog(
            nfcScanController = nfcScanController,
            message = stringResource(R.string.profile_child_tag_add),
            onRead = { read ->
                isReadingTag = false
                // A parents' Ctrus is already the master key; it isn't the child's.
                if (!ChildModeStore.isParentTag(read.uid)) {
                    onDraftChange(draft.copy(childTag = ChildTag(uid = ChildModeStore.normalizedUid(read.uid), url = read.url)))
                }
            },
            onDismiss = { isReadingTag = false },
            requiresGenuineCtrus = true,
        )
    }
}

/** The fixed note every Child Mode profile shows: sessions end on their own after 24 hours. */
@Composable
fun ChildModeSessionLimitNote() {
    // Starts at the row's edge like the other rows, so the divider below lines up too.
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.profile_child_mode_24h),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
