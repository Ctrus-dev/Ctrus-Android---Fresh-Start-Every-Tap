package mo.dev.ctrus.ui.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.R
import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.data.BlockedProfileSessionEntity
import mo.dev.ctrus.data.InsightsSummary
import mo.dev.ctrus.data.MonthlyDayAggregate
import mo.dev.ctrus.data.WeeklyDayAggregate
import mo.dev.ctrus.data.duration
import mo.dev.ctrus.data.usedBreakDurationIncludingActive
import mo.dev.ctrus.theme.CtrusRoundedBold
import mo.dev.ctrus.util.DateFormatters
import java.util.Calendar
import java.util.Date

private enum class InsightsFilter { THIS_WEEK, LAST_WEEK, THIS_MONTH, LAST_MONTH, SPECIFIC_WEEK, SPECIFIC_MONTH, ALL_SESSIONS }
private enum class InsightsViewMode { WEEK, MONTH, ALL_SESSIONS }

private fun InsightsFilter.viewMode() = when (this) {
    InsightsFilter.THIS_WEEK, InsightsFilter.LAST_WEEK, InsightsFilter.SPECIFIC_WEEK -> InsightsViewMode.WEEK
    InsightsFilter.THIS_MONTH, InsightsFilter.LAST_MONTH, InsightsFilter.SPECIFIC_MONTH -> InsightsViewMode.MONTH
    InsightsFilter.ALL_SESSIONS -> InsightsViewMode.ALL_SESSIONS
}

/**
 * Android equivalent of ProfileInsightsView.swift: a chart (weekly bar / monthly heatmap,
 * matching the same 5-bucket coloring and drag-select as iOS), a Summary section, and the
 * session list grouped by day. Week/Month-picker sheets and "specific date" filters use Material
 * 3's `DatePickerDialog` in place of iOS's native graphical `DatePicker`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileInsightsScreen(
    profile: BlockedProfileEntity,
    sessions: List<BlockedProfileSessionEntity>,
    themeColor: Color,
    onDismiss: () -> Unit,
    onDeleteSession: (BlockedProfileSessionEntity) -> Unit,
    onDeleteAllSessions: () -> Unit,
) {
    var filter by remember { mutableStateOf(InsightsFilter.THIS_WEEK) }
    var selectedDate by remember { mutableStateOf(Date()) }
    var selectedWeekDay by remember { mutableStateOf<WeeklyDayAggregate?>(null) }
    var selectedMonthDay by remember { mutableStateOf<MonthlyDayAggregate?>(null) }
    var sessionPendingDelete by remember { mutableStateOf<BlockedProfileSessionEntity?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var showWeekPicker by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var selectedSession by remember { mutableStateOf<BlockedProfileSessionEntity?>(null) }

    val viewMode = filter.viewMode()
    val completedSessions = remember(sessions) { sessions.filter { it.endTimeEpochMilli != null } }

    val weeklySummary = remember(completedSessions, selectedDate) { InsightsSummary.weeklySummary(completedSessions, selectedDate) }
    val monthlySummary = remember(completedSessions, selectedDate) { InsightsSummary.monthlySummary(completedSessions, selectedDate) }
    val metrics = remember(completedSessions) { InsightsSummary.metrics(completedSessions, profile) }

    fun clearDaySelection() {
        selectedWeekDay = null
        selectedMonthDay = null
    }

    val filteredSessions = remember(completedSessions, viewMode, selectedWeekDay, selectedMonthDay, weeklySummary, monthlySummary) {
        when (viewMode) {
            InsightsViewMode.WEEK -> filterByRange(completedSessions, weeklySummary.weekStartDate, addDaysExclusive(weeklySummary.weekEndDate, 1), selectedWeekDay?.date)
            InsightsViewMode.MONTH -> filterByRange(completedSessions, monthlySummary.monthStartDate, addDaysExclusive(monthlySummary.monthEndDate, 1), selectedMonthDay?.date)
            InsightsViewMode.ALL_SESSIONS -> completedSessions
        }
    }

    val groupedSessions = remember(filteredSessions) { groupByDay(filteredSessions) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.insights_screen_title, profile.name)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close)) }
                },
                actions = {
                    IconButton(onClick = { showFilterMenu = true }) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = stringResource(R.string.insights_filter_content_description))
                    }
                    DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.insights_filter_this_week)) }, onClick = { showFilterMenu = false; filter = InsightsFilter.THIS_WEEK; clearDaySelection(); selectedDate = Date() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.insights_filter_last_week)) }, onClick = {
                            showFilterMenu = false; filter = InsightsFilter.LAST_WEEK; clearDaySelection()
                            selectedDate = shiftDate(Date(), Calendar.WEEK_OF_YEAR, -1)
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.insights_filter_this_month)) }, onClick = { showFilterMenu = false; filter = InsightsFilter.THIS_MONTH; clearDaySelection(); selectedDate = Date() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.insights_filter_last_month)) }, onClick = {
                            showFilterMenu = false; filter = InsightsFilter.LAST_MONTH; clearDaySelection()
                            selectedDate = shiftDate(Date(), Calendar.MONTH, -1)
                        })
                        DropdownMenuItem(
                            text = { Text(stringResource(if (viewMode == InsightsViewMode.MONTH) R.string.insights_filter_select_month else R.string.insights_filter_select_week)) },
                            onClick = {
                                showFilterMenu = false
                                if (viewMode == InsightsViewMode.MONTH) showMonthPicker = true else showWeekPicker = true
                            },
                        )
                        DropdownMenuItem(text = { Text(stringResource(R.string.insights_filter_all_sessions)) }, onClick = { showFilterMenu = false; filter = InsightsFilter.ALL_SESSIONS; clearDaySelection() })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.insights_filter_delete_all_sessions), color = MaterialTheme.colorScheme.error) },
                            onClick = { showFilterMenu = false; showDeleteAllConfirm = true },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (viewMode != InsightsViewMode.ALL_SESSIONS) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        ChartHeader(
                            selectedLabel = when (viewMode) {
                                InsightsViewMode.WEEK -> selectedWeekDay?.let { DateFormatters.formatSelectedDayHeader(it.date) }
                                else -> selectedMonthDay?.let { DateFormatters.formatSelectedDayHeader(it.date) }
                            },
                            selectedValueSeconds = when (viewMode) {
                                InsightsViewMode.WEEK -> selectedWeekDay?.totalSessionSeconds
                                else -> selectedMonthDay?.totalSessionSeconds
                            },
                            averageSeconds = if (viewMode == InsightsViewMode.WEEK) weeklySummary.averageSessionDuration else monthlySummary.averageSessionDuration,
                            onClear = ::clearDaySelection,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (viewMode == InsightsViewMode.WEEK) {
                            WeeklyBarChart(
                                days = weeklySummary.days,
                                selectedDay = selectedWeekDay,
                                themeColor = themeColor,
                                onDaySelected = { selectedWeekDay = it },
                            )
                        } else {
                            MonthlyHeatmapGrid(
                                days = monthlySummary.days,
                                selectedDay = selectedMonthDay,
                                themeColor = themeColor,
                                onDaySelected = { selectedMonthDay = it },
                            )
                        }
                    }
                }
            }

            if (selectedWeekDay == null && selectedMonthDay == null) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(stringResource(R.string.insights_summary_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
                        InsightsSummaryRow(stringResource(R.string.insights_total_focus_time), DateFormatters.formatDurationHoursMinutes(metrics.totalFocusTime))
                        InsightsSummaryRow(stringResource(R.string.insights_total_break_time), DateFormatters.formatDurationHoursMinutes(metrics.totalBreakTime))
                        InsightsSummaryRow(stringResource(R.string.insights_profile_id), profile.id.take(8) + "...")
                    }
                }
            }

            groupedSessions.forEach { (day, daySessions) ->
                item {
                    Text(
                        DateFormatters.formatSessionDate(day),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                items(daySessions, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        profile = profile,
                        onClick = { selectedSession = session },
                        onDeleteRequested = { sessionPendingDelete = session },
                    )
                }
            }
        }
    }

    selectedSession?.let { session ->
        SessionDetailsSheet(session = session, profile = profile, onDismiss = { selectedSession = null })
    }

    sessionPendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionPendingDelete = null },
            title = { Text(stringResource(R.string.insights_delete_session_title)) },
            text = { Text(stringResource(R.string.insights_delete_session_body)) },
            confirmButton = {
                TextButton(onClick = { onDeleteSession(session); sessionPendingDelete = null }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { sessionPendingDelete = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    if (showDeleteAllConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text(stringResource(R.string.insights_delete_all_title)) },
            text = { Text(stringResource(R.string.insights_delete_all_body)) },
            confirmButton = {
                TextButton(onClick = { onDeleteAllSessions(); showDeleteAllConfirm = false }) {
                    Text(stringResource(R.string.insights_delete_all_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteAllConfirm = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    if (showWeekPicker) {
        DatePickerPromptDialog(onDismiss = { showWeekPicker = false }) { date ->
            filter = InsightsFilter.SPECIFIC_WEEK
            selectedDate = date
            clearDaySelection()
            showWeekPicker = false
        }
    }
    if (showMonthPicker) {
        DatePickerPromptDialog(onDismiss = { showMonthPicker = false }) { date ->
            filter = InsightsFilter.SPECIFIC_MONTH
            selectedDate = date
            clearDaySelection()
            showMonthPicker = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerPromptDialog(onDismiss: () -> Unit, onPicked: (Date) -> Unit) {
    val state = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPicked(Date(it)) } ?: onDismiss() }) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

@Composable
private fun ChartHeader(selectedLabel: String?, selectedValueSeconds: Double?, averageSeconds: Double, onClear: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        if (selectedLabel != null && selectedValueSeconds != null) {
            Column {
                Text(selectedLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(DateFormatters.formatDurationHoursMinutes(selectedValueSeconds), style = MaterialTheme.typography.headlineMedium, fontFamily = CtrusRoundedBold)
                    Text(stringResource(R.string.insights_total_label), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onClear) { Text(stringResource(R.string.insights_reset)) }
        } else {
            Column {
                Text(stringResource(R.string.insights_avg_focus_session), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(DateFormatters.formatDurationHoursMinutes(averageSeconds), style = MaterialTheme.typography.headlineMedium, fontFamily = CtrusRoundedBold)
            }
        }
    }
}

@Composable
private fun InsightsSummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
    }
}

@Composable
private fun SessionRow(session: BlockedProfileSessionEntity, profile: BlockedProfileEntity, onClick: () -> Unit, onDeleteRequested: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(vertical = 6.dp),
        ) {
            val durationSeconds = session.duration() / 1000.0
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    DateFormatters.formatDurationHoursMinutes(durationSeconds),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontFamily = CtrusRoundedBold,
                )
                Text(stringResource(R.string.insights_total_label), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val breakSeconds = session.usedBreakDurationIncludingActive(profile)
            if (breakSeconds > 0) {
                Text("☕ ${(breakSeconds / 60).toInt()}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onDeleteRequested) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.insights_delete_session_content_description), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun shiftDate(date: Date, field: Int, amount: Int): Date {
    val cal = Calendar.getInstance()
    cal.time = date
    cal.add(field, amount)
    return cal.time
}

private fun addDaysExclusive(date: Date, days: Int): Date {
    val cal = Calendar.getInstance()
    cal.time = date
    cal.add(Calendar.DAY_OF_YEAR, days)
    return cal.time
}

private fun filterByRange(sessions: List<BlockedProfileSessionEntity>, start: Date, endExclusive: Date, selectedDay: Date?): List<BlockedProfileSessionEntity> {
    val rangeFiltered = sessions.filter { session ->
        val end = session.endTimeEpochMilli ?: return@filter false
        session.startTimeEpochMilli < endExclusive.time && end > start.time
    }
    if (selectedDay == null) return rangeFiltered

    val cal = Calendar.getInstance().apply { time = selectedDay }
    cal[Calendar.HOUR_OF_DAY] = 0; cal[Calendar.MINUTE] = 0; cal[Calendar.SECOND] = 0; cal[Calendar.MILLISECOND] = 0
    val dayStart = cal.timeInMillis
    val dayEnd = dayStart + 24L * 60 * 60 * 1000

    return rangeFiltered.filter { session ->
        val end = session.endTimeEpochMilli ?: return@filter false
        session.startTimeEpochMilli < dayEnd && end > dayStart
    }
}

private fun groupByDay(sessions: List<BlockedProfileSessionEntity>): List<Pair<Date, List<BlockedProfileSessionEntity>>> {
    val sorted = sessions.sortedByDescending { it.startTimeEpochMilli }
    val calendar = Calendar.getInstance()
    val groups = LinkedHashMap<Long, MutableList<BlockedProfileSessionEntity>>()
    for (session in sorted) {
        calendar.timeInMillis = session.startTimeEpochMilli
        calendar[Calendar.HOUR_OF_DAY] = 0; calendar[Calendar.MINUTE] = 0; calendar[Calendar.SECOND] = 0; calendar[Calendar.MILLISECOND] = 0
        groups.getOrPut(calendar.timeInMillis) { mutableListOf() }.add(session)
    }
    return groups.map { (dayMillis, list) -> Date(dayMillis) to list }
}
