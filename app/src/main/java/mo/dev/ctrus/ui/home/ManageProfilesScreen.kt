package mo.dev.ctrus.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.R
import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.ui.common.GlassIconButton

/**
 * Android equivalent of BlockedProfileListView.swift's "Manage" sheet: tap a row to edit, "+" to
 * create (disabled while a profile is actively blocking, matching iOS's `canCreateProfiles`), and
 * a pencil toggle that turns on edit mode: edit on the leading side, delete on the trailing side,
 * and a drag handle at the far trailing edge to reorder (iOS d849aac, where the system's reorder
 * handle sits after delete). Delete still guards against removing the currently-active profile,
 * matching iOS's alert, and closes this sheet automatically once the last profile is gone.
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
    onDeleteProfile: (BlockedProfileEntity) -> Unit,
    onReorderProfiles: (List<BlockedProfileEntity>) -> Unit,
    onAllInsights: () -> Unit,
) {
    var editMode by remember { mutableStateOf(false) }
    // The order shown while a row is dragged; otherwise it follows `profiles`.
    var orderedProfiles by remember { mutableStateOf(profiles) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    LaunchedEffect(profiles) {
        if (draggingId == null) orderedProfiles = profiles
    }
    // Swaps the dragged row with its neighbour once its centre passes the neighbour's centre.
    fun dragBy(amount: Float) {
        val id = draggingId ?: return
        dragOffset += amount
        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == id } ?: return
        val center = current.offset + dragOffset + current.size / 2f
        val target = items.firstOrNull { other ->
            other.key != id && other.key is String &&
                if (dragOffset > 0) other.index > current.index && center > other.offset + other.size / 2f
                else other.index < current.index && center < other.offset + other.size / 2f
        } ?: return
        val from = orderedProfiles.indexOfFirst { it.id == id }
        val to = orderedProfiles.indexOfFirst { it.id == target.key }
        if (from < 0 || to < 0) return
        orderedProfiles = orderedProfiles.toMutableList().apply { add(to, removeAt(from)) }
        dragOffset += (current.offset - target.offset).toFloat()
    }
    var showActiveProfileError by remember { mutableStateOf(false) }
    val canCreateProfiles = activeProfileId == null

    // Nothing left to manage — close instead of leaving an empty sheet open behind whatever
    // deleted the last profile.
    LaunchedEffect(profiles) {
        if (profiles.isEmpty()) onDismiss()
    }

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
                    GlassIconButton(onClick = onDismiss, icon = Icons.Filled.Close, contentDescription = stringResource(R.string.manage_close_content_description))
                    Row {
                        if (profiles.isNotEmpty()) {
                            // Combined insights across every profile (same screen as a single
                            // profile's, same chart icon as Edit Profile's toolbar).
                            GlassIconButton(
                                onClick = onAllInsights,
                                icon = Icons.Filled.BarChart,
                                contentDescription = stringResource(R.string.manage_all_insights_content_description),
                            )
                            Spacer(Modifier.width(8.dp))
                            GlassIconButton(
                                onClick = { editMode = !editMode },
                                icon = if (editMode) Icons.Filled.Check else Icons.Filled.Edit,
                                contentDescription = stringResource(if (editMode) R.string.manage_done_content_description else R.string.manage_edit_move_content_description),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        if (canCreateProfiles) {
                            GlassIconButton(onClick = onAddProfile, icon = Icons.Filled.Add, contentDescription = stringResource(R.string.manage_add_profile_content_description))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.manage_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(orderedProfiles, key = { it.id }) { profile ->
                val isDragging = profile.id == draggingId
                ManageProfileRow(
                    modifier = if (isDragging) {
                        Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset }
                    } else {
                        Modifier.animateItem()
                    },
                    profile = profile,
                    isActive = profile.id == activeProfileId,
                    themeColor = themeColor,
                    editMode = editMode,
                    onClick = { if (!editMode) onEditProfile(profile) },
                    onEdit = { onEditProfile(profile) },
                    onDelete = {
                        if (profile.id == activeProfileId) {
                            showActiveProfileError = true
                        } else {
                            onDeleteProfile(profile)
                        }
                    },
                    handleModifier = Modifier.pointerInput(profile.id) {
                        detectDragGestures(
                            onDragStart = {
                                draggingId = profile.id
                                dragOffset = 0f
                            },
                            onDragEnd = {
                                draggingId = null
                                dragOffset = 0f
                                if (orderedProfiles.map { it.id } != profiles.map { it.id }) onReorderProfiles(orderedProfiles)
                            },
                            onDragCancel = {
                                draggingId = null
                                dragOffset = 0f
                                orderedProfiles = profiles
                            },
                        ) { change, amount ->
                            change.consume()
                            dragBy(amount.y)
                        }
                    },
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
    modifier: Modifier,
    profile: BlockedProfileEntity,
    isActive: Boolean,
    themeColor: Color,
    editMode: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    handleModifier: Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Matches BlockedProfileListView.swift's edit-mode row: the pencil/delete controls sit
        // outside the card, flanking it, rather than sharing its own internal Row. The pencil
        // needs its own onEdit — reusing the card's onClick (guarded by `!editMode` so a browsing
        // tap doesn't fire while these controls are showing) would make it a silent no-op here,
        // since editMode is always true while this button is visible at all.
        if (editMode) {
            ManageCircleIconButton(
                onClick = onEdit,
                icon = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.manage_edit_profile_content_description, profile.name),
                backgroundColor = Color(0xFF48484A),
            )
        }

        Card(
            onClick = onClick,
            enabled = !editMode,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(3.5.dp, themeColor),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
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
        }

        if (editMode) {
            ManageCircleIconButton(
                onClick = onDelete,
                icon = Icons.Filled.Remove,
                contentDescription = stringResource(R.string.manage_delete_content_description, profile.name),
                // A more saturated red than MaterialTheme's own error color, which read as too
                // muted/dark next to the pencil circle's neutral grey.
                backgroundColor = Color(0xFFFF3B30),
            )
            // The reorder handle at the far trailing edge, after delete (iOS d849aac).
            Box(
                modifier = handleModifier.size(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = stringResource(R.string.manage_move_content_description, profile.name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The solid grey (edit) / red (delete) circle flanking a row in edit mode. */
@Composable
private fun ManageCircleIconButton(onClick: () -> Unit, icon: ImageVector, contentDescription: String, backgroundColor: Color) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}
