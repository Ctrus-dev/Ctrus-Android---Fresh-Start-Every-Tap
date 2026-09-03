package mo.dev.ctrus.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.R
import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.theme.FixedLightPrimaryText
import mo.dev.ctrus.theme.FixedLightSecondaryText

/**
 * Android equivalent of StartProfilePickerView.swift: shown when the launcher's "Start" button
 * is tapped and more than one profile exists. Tapping a row only selects it (mirroring iOS —
 * the list isn't itself the action); the "Go" button at the bottom is what actually starts the
 * selected profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartProfilePickerView(
    profiles: List<BlockedProfileEntity>,
    themeColor: Color,
    isBlocking: Boolean,
    activeProfileId: String?,
    onDismiss: () -> Unit,
    onProfileChosen: (BlockedProfileEntity) -> Unit,
) {
    var selectedProfileId by remember { mutableStateOf(profiles.firstOrNull()?.id) }
    val selectedProfile = profiles.firstOrNull { it.id == selectedProfileId }
    val canGo = selectedProfile != null && !isBlocking

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isBlocking) {
                ActiveSessionNotice()
                Spacer(Modifier.height(10.dp))
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(profiles, key = { it.id }) { profile ->
                    StartProfilePickerRow(
                        profile = profile,
                        isSelected = profile.id == selectedProfileId,
                        isActive = profile.id == activeProfileId,
                        themeColor = themeColor,
                        onTap = { selectedProfileId = profile.id },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { selectedProfile?.let { onProfileChosen(it); onDismiss() } },
                enabled = canGo,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = themeColor, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Text(stringResource(R.string.start_picker_go_button), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ActiveSessionNotice() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = FixedLightSecondaryText)
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.start_picker_active_session_notice),
            style = MaterialTheme.typography.bodyMedium,
            color = FixedLightSecondaryText,
        )
    }
}

@Composable
private fun StartProfilePickerRow(
    profile: BlockedProfileEntity,
    isSelected: Boolean,
    isActive: Boolean,
    themeColor: Color,
    onTap: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(
                BorderStroke(if (isSelected) 2.5.dp else 2.dp, themeColor.copy(alpha = if (isSelected) 0.7f else 0.35f)),
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onTap)
            .padding(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = FixedLightPrimaryText)
                if (isActive) {
                    Spacer(Modifier.width(6.dp))
                    Row(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.08f), RoundedCornerShape(50))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Text(stringResource(R.string.common_active), style = MaterialTheme.typography.labelSmall, color = FixedLightPrimaryText)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            val appsLabel = pluralStringResource(R.plurals.apps_count, profile.selectedPackages.size, profile.selectedPackages.size)
            val domainsLabel = pluralStringResource(R.plurals.domains_count, profile.domains.orEmpty().size, profile.domains.orEmpty().size)
            Text("$appsLabel | $domainsLabel", style = MaterialTheme.typography.bodySmall, color = FixedLightSecondaryText)
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.Circle,
            contentDescription = null,
            tint = if (isSelected) themeColor else FixedLightSecondaryText.copy(alpha = 0.5f),
        )
    }
}
