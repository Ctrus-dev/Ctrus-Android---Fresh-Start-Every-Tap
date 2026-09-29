package mo.dev.ctrus.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.R
import mo.dev.ctrus.ui.common.GlassIconButton

/** Copyright lines are legal notices, so they're never translated (same as the repo's LICENSE file). */
private val CopyrightLines = listOf(
    "Copyright (c) 2024 Ali Waseem",
    "Copyright (c) 2026 Martim Oliveira",
)

/**
 * The project's MIT license (the repo-root LICENSE, copied from the iOS repo), opened from
 * Settings > About > License. It's a pop-up sheet like the Emergency and Edit Profile ones. The PT/ES
 * translations carry a note that the English original is the legally binding text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            GlassIconButton(onClick = onDismiss, icon = Icons.Filled.Close, contentDescription = stringResource(R.string.settings_close_content_description))
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.settings_license), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.license_heading), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                CopyrightLines.forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                listOf(R.string.license_permission, R.string.license_condition, R.string.license_disclaimer).forEach { res ->
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(res), style = MaterialTheme.typography.bodyMedium)
                }
            }

            val note = stringResource(R.string.license_translation_note)
            if (note.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}
