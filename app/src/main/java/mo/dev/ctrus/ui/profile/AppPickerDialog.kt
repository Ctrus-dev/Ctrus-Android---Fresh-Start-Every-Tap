package mo.dev.ctrus.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import mo.dev.ctrus.R
import mo.dev.ctrus.picker.InstalledApp
import mo.dev.ctrus.picker.InstalledAppsRepository

/**
 * Android equivalent of iOS's `.familyActivityPicker` sheet (see `BlockedProfileAppSelector.swift`,
 * which just opens the picker as its own full-screen surface instead of listing apps inline with
 * the rest of the profile form) — a dedicated full-screen picker instead of an inline checkbox
 * list mixed into the wizard step, with a search field since the Android list (real installed
 * apps, not opaque FamilyControls tokens) can be much longer than iOS's category tree.
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
        LaunchedEffect(Unit) { apps = InstalledAppsRepository(context).listLaunchableApps() }

        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(if (allowMode) R.string.app_picker_title_allow else R.string.app_picker_title_restrict)) },
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

                androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
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
                        items(filtered, key = { it.packageName }) { app ->
                            val isSelected = app.packageName in selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + app.packageName else selected - app.packageName
                                    },
                                )
                                app.icon?.toBitmap()?.asImageBitmap()?.let {
                                    Image(bitmap = it, contentDescription = null, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
                                }
                                Text(app.label, modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
