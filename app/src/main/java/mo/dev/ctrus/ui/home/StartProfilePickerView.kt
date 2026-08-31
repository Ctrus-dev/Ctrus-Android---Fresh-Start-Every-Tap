package mo.dev.ctrus.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.R
import mo.dev.ctrus.data.BlockedProfileEntity

/** Android equivalent of StartProfilePickerView.swift: shown when the launcher's "Start" button is tapped and more than one profile exists. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartProfilePickerView(profiles: List<BlockedProfileEntity>, themeColor: Color, onDismiss: () -> Unit, onProfileChosen: (BlockedProfileEntity) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.start_picker_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(profiles, key = { it.id }) { profile ->
                    Card(
                        onClick = { onProfileChosen(profile) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                            val subtitle = if (profile.selectedPackages.isEmpty()) {
                                stringResource(R.string.start_picker_no_apps_selected)
                            } else {
                                pluralStringResource(R.plurals.apps_blocked_count, profile.selectedPackages.size, profile.selectedPackages.size)
                            }
                            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
