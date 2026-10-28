package mo.dev.ctrus.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.R
import mo.dev.ctrus.data.ProfileSchedule
import mo.dev.ctrus.data.ScheduleSkip
import mo.dev.ctrus.ui.common.GlassIconButton
import mo.dev.ctrus.util.DateFormatters
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Port of iOS's SkipUntilView: a calendar of today and the next [ScheduleSkip.MAX_DAYS_AHEAD]
 * days with nothing chosen at first, a summary of how many sessions the chosen day skips, and the
 * button that asks for the profile's Ctrus. Headed like Settings (close button, then a big title).
 *
 * [errorMessage] is shown here, in this sheet, since a skip that fails is reported while the
 * sheet is still open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkipUntilSheet(
    schedule: ProfileSchedule,
    themeColor: Color,
    errorMessage: String?,
    onDismissError: () -> Unit,
    onClose: () -> Unit,
    onSkip: (LocalDate) -> Unit,
) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val lastDay = today.plusDays(ScheduleSkip.MAX_DAYS_AHEAD)
    // DatePicker works in UTC-midnight millis for each calendar day.
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = null,
        initialDisplayedMonthMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = today.year..lastDay.year,
        selectableDates = remember(today) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val day = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return !day.isBefore(today) && !day.isAfter(lastDay)
                }

                override fun isSelectableYear(year: Int): Boolean = year in today.year..lastDay.year
            }
        },
    )
    val selectedDay = datePickerState.selectedDateMillis?.let {
        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
    }
    val skippedSessionCount = selectedDay?.let {
        schedule.upcomingOccurrenceStarts(ZonedDateTime.now(ZoneId.systemDefault()), it).size
    } ?: 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            GlassIconButton(
                onClick = onClose,
                icon = Icons.Filled.Close,
                contentDescription = stringResource(R.string.settings_close_content_description),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.skip_until),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        DatePicker(
            state = datePickerState,
            title = null,
            headline = null,
            showModeToggle = false,
            colors = DatePickerDefaults.colors(
                containerColor = Color.Transparent,
                selectedDayContainerColor = themeColor,
                selectedDayContentColor = Color.White,
                todayDateBorderColor = themeColor,
                todayContentColor = themeColor,
            ),
        )

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            HorizontalDivider()
            if (selectedDay != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    if (skippedSessionCount == 0) {
                        stringResource(R.string.skip_sheet_none)
                    } else {
                        val dayText = DateFormatters.formatSkipWeekdayAndDay(context, selectedDay.atStartOfDay(ZoneId.systemDefault()))
                        pluralStringResource(R.plurals.skip_sheet_summary, skippedSessionCount, skippedSessionCount, dayText)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { selectedDay?.let(onSkip) },
                enabled = skippedSessionCount > 0,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = themeColor, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp).height(56.dp),
            ) {
                Text(stringResource(R.string.skip_sheet_button))
            }
        }
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(stringResource(R.string.error_dialog_title)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = onDismissError) { Text(stringResource(R.string.common_ok)) } },
        )
    }
}
