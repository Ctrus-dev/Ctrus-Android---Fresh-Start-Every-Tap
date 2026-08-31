package mo.dev.ctrus.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.R
import mo.dev.ctrus.data.BlockedProfileEntity

/**
 * Android equivalent of BlockedProfileListView.swift's "Manage" sheet: tap a row to edit, "+" to
 * create (disabled while a profile is actively blocking, matching iOS's `canCreateProfiles`), and
 * a pencil toggle that turns on a delete-and-reorder edit mode. **Simplified vs. iOS**: reordering
 * uses up/down arrow buttons per row instead of iOS's native drag handle — this project has no
 * drag-reorder library dependency, and a hand-rolled pointer-drag reimplementation was judged not
 * worth the fragility for what's a rarely-used affordance; `ProfileRepository.reorder` (already
 * used here) is the same call either way, so upgrading to real drag-and-drop later is a pure UI
 * swap. Delete still guards against removing the currently-active profile, matching iOS's alert.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageProfilesScreen(
    profiles: List<BlockedProfileEntity>,
    activeProfileId: String?,
    themeColor: Color,
    onDismiss: () -> Unit,
    onEditProfile: (BlockedProfileEntity) -> Unit,
    onAddProfile: () -> Unit,
    onReorder: (List<BlockedProfileEntity>) -> Unit,
    onDeleteProfile: (BlockedProfileEntity) -> Unit,
) {
    var editMode by remember { mutableStateOf(false) }
    var showActiveProfileError by remember { mutableStateOf(false) }
    val canCreateProfiles = activeProfileId == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manage_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.manage_close_content_description)) }
                },
                actions = {
                    if (profiles.isNotEmpty()) {
                        IconButton(onClick = { editMode = !editMode }) {
                            Icon(
                                if (editMode) Icons.Filled.Check else Icons.Filled.Edit,
                                contentDescription = stringResource(if (editMode) R.string.manage_done_content_description else R.string.manage_edit_move_content_description),
                            )
                        }
                    }
                    if (canCreateProfiles) {
                        IconButton(onClick = onAddProfile) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.manage_add_profile_content_description)) }
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(profiles, key = { it.id }) { profile ->
                val index = profiles.indexOf(profile)
                ManageProfileRow(
                    profile = profile,
                    isActive = profile.id == activeProfileId,
                    themeColor = themeColor,
                    editMode = editMode,
                    canMoveUp = index > 0,
                    canMoveDown = index < profiles.lastIndex,
                    onClick = { if (!editMode) onEditProfile(profile) },
                    onDelete = {
                        if (profile.id == activeProfileId) {
                            showActiveProfileError = true
                        } else {
                            onDeleteProfile(profile)
                        }
                    },
                    onMoveUp = { onReorder(profiles.toMutableList().apply { add(index - 1, removeAt(index)) }) },
                    onMoveDown = { onReorder(profiles.toMutableList().apply { add(index + 1, removeAt(index)) }) },
                )
            }
        }
    }

    if (showActiveProfileError) {
        AlertDialog(
            onDismissRequest = { showActiveProfileError = false },
            title = { Text(stringResource(R.string.manage_cannot_delete_active_title)) },
            text = { Text(stringResource(R.string.manage_cannot_delete_active_body)) },
            confirmButton = { TextButton(onClick = { showActiveProfileError = false }) { Text(stringResource(R.string.common_ok)) } },
        )
    }
}

@Composable
private fun ManageProfileRow(
    profile: BlockedProfileEntity,
    isActive: Boolean,
    themeColor: Color,
    editMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(3.5.dp, themeColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editMode) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.RemoveCircle, contentDescription = stringResource(R.string.manage_delete_content_description, profile.name), tint = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.width(4.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium)
                    if (isActive) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(50))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        ) {
                            Text(stringResource(R.string.common_active), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                val appsLabel = pluralStringResource(R.plurals.apps_count, profile.selectedPackages.size, profile.selectedPackages.size)
                val domainsLabel = pluralStringResource(R.plurals.domains_count, profile.domains.orEmpty().size, profile.domains.orEmpty().size)
                Text(
                    "$appsLabel | $domainsLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (editMode) {
                Column {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.manage_move_up_content_description))
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.manage_move_down_content_description))
                    }
                }
            }
        }
    }
}
