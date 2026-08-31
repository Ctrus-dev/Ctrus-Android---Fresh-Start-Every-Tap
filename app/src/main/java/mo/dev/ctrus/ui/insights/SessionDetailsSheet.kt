package mo.dev.ctrus.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.data.BlockedProfileSessionEntity
import mo.dev.ctrus.data.duration
import mo.dev.ctrus.data.usedBreakDurationIncludingActive
import mo.dev.ctrus.util.DateFormatters
import java.util.Date

/**
 * Android equivalent of SessionDetailsView.swift, minus its Pause section — pause is only ever
 * set by the NFC+PauseTimer strategy, which isn't one of the two strategies actually reachable
 * in the shipped iOS UI (see [mo.dev.ctrus.data.BlockedProfileSessionEntity]'s doc comment).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailsSheet(
    session: BlockedProfileSessionEntity,
    profile: BlockedProfileEntity,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.session_details_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))

            SectionTitle(stringResource(R.string.session_details_info_section))
            DetailRow(stringResource(R.string.session_details_tag), session.tag)
            DetailRow(stringResource(R.string.session_details_profile), profile.name)
            DetailRow(stringResource(R.string.session_details_force_started), stringResource(if (session.forceStarted) R.string.common_yes else R.string.common_no))

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SectionTitle(stringResource(R.string.session_details_timing_section))
            DetailRow(stringResource(R.string.session_details_start), DateFormatters.formatDate(Date(session.startTimeEpochMilli)))
            DetailRow(stringResource(R.string.session_details_end), session.endTimeEpochMilli?.let { DateFormatters.formatDate(Date(it)) } ?: stringResource(R.string.session_details_in_progress))
            DetailRow(stringResource(R.string.session_details_duration), DateFormatters.formatDuration(session.duration() / 1000.0))

            if (session.breakStartTimeEpochMilli != null && session.breakEndTimeEpochMilli != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                SectionTitle(stringResource(R.string.session_details_break_section))
                DetailRow(stringResource(R.string.session_details_break_start), DateFormatters.formatDate(Date(session.breakStartTimeEpochMilli)))
                DetailRow(stringResource(R.string.session_details_break_end), DateFormatters.formatDate(Date(session.breakEndTimeEpochMilli)))
                DetailRow(stringResource(R.string.session_details_break_duration), DateFormatters.formatDuration(session.usedBreakDurationIncludingActive(profile)))
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
