package mo.dev.ctrus.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import mo.dev.ctrus.R
import mo.dev.ctrus.picker.AppCategory
import mo.dev.ctrus.picker.InstalledApp
import mo.dev.ctrus.picker.InstalledAppsRepository
import mo.dev.ctrus.picker.appCategory

/**
 * Android equivalent of iOS's `.familyActivityPicker` sheet (see `BlockedProfileAppSelector.swift`,
 * which just opens the picker as its own full-screen surface instead of listing apps inline with
 * the rest of the profile form) — a dedicated full-screen picker instead of an inline checkbox
 * list mixed into the wizard step, with a search field since the Android list (real installed
 * apps, not opaque FamilyControls tokens) can be much longer than iOS's category tree.
 *
 * Grouped by [AppCategory], collapsed by default like iOS's own picker: tapping a category's
 * checkbox blocks the whole theme in one tap, tapping the row itself expands it to reveal (and
 * individually pick from) the apps inside. Search expands every category with a match so results
 * aren't hidden inside a collapsed one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerDialog(
    initialSelectedPackages: Set<String>,
    allowMode: Boolean,
    onDismiss: () -> Unit,
    onDone: (Set<String>) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val context = LocalContext.current
        var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
        var selected by remember { mutableStateOf(initialSelectedPackages) }
        var query by remember { mutableStateOf("") }
        var expandedCategories by remember { mutableStateOf(emptySet<AppCategory>()) }
        LaunchedEffect(Unit) { apps = InstalledAppsRepository(context).listLaunchableApps() }

        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                // No title here — the search field right below already says what this screen is
                // for, and the close/done actions are the only things that need top-bar space.
                topBar = {
                    TopAppBar(
                        title = {},
                        navigationIcon = {
                            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.app_picker_cancel_content_description)) }
                        },
                        actions = {
                            IconButton(onClick = { onDone(selected) }) {
                                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.app_picker_done_content_description))
                            }
                        },
                    )
                },
            ) { innerPadding ->
                val currentApps = apps
                if (currentApps == null) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    return@Scaffold
                }

                val filtered = remember(currentApps, query) {
                    if (query.isBlank()) currentApps else currentApps.filter { it.label.contains(query, ignoreCase = true) }
                }
                val grouped = remember(filtered) { filtered.groupBy { it.appCategory() } }
                val isSearching = query.isNotBlank()

                Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.app_picker_search_placeholder)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    Text(
                        if (selected.isEmpty()) stringResource(R.string.app_picker_no_apps_selected) else pluralStringResource(R.plurals.apps_selected_count, selected.size, selected.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        for (category in AppCategory.displayOrder) {
                            val appsInCategory = grouped[category].orEmpty()
                            if (appsInCategory.isEmpty()) continue
                            val isExpanded = isSearching || category in expandedCategories

                            item(key = "header_${category.name}") {
                                CategoryHeader(
                                    category = category,
                                    apps = appsInCategory,
                                    selected = selected,
                                    isExpanded = isExpanded,
                                    onToggleExpand = {
                                        expandedCategories = if (category in expandedCategories) {
                                            expandedCategories - category
                                        } else {
                                            expandedCategories + category
                                        }
                                    },
                                    onToggleCategory = { selectAll ->
                                        val packages = appsInCategory.map { it.packageName }
                                        selected = if (selectAll) selected + packages else selected - packages.toSet()
                                    },
                                )
                            }
                            if (isExpanded) {
                                items(appsInCategory, key = { it.packageName }) { app ->
                                    AppRow(
                                        app = app,
                                        isSelected = app.packageName in selected,
                                        onToggle = { checked ->
                                            selected = if (checked) selected + app.packageName else selected - app.packageName
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(
    category: AppCategory,
    apps: List<InstalledApp>,
    selected: Set<String>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleCategory: (selectAll: Boolean) -> Unit,
) {
    val selectedCount = apps.count { it.packageName in selected }
    val state = when (selectedCount) {
        0 -> ToggleableState.Off
        apps.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val categoryLabel = stringResource(category.labelRes)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = categoryLabel, onClick = onToggleExpand)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(
            state = state,
            onClick = { onToggleCategory(state != ToggleableState.On) },
        )
        Icon(category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Text(
            categoryLabel,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        Text(
            "$selectedCount/${apps.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val chevron: ImageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight
        Icon(chevron, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun AppRow(app: InstalledApp, isSelected: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Indented under the category's own checkbox+label, so each app reads as belonging
            // to the group header above it rather than sitting at the same level as it.
            .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = isSelected, onCheckedChange = onToggle)
        app.icon?.toBitmap()?.asImageBitmap()?.let {
            Image(bitmap = it, contentDescription = null, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
        }
        Text(app.label, modifier = Modifier.padding(start = 12.dp))
    }
}
