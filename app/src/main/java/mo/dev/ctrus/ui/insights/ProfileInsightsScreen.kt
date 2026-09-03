package mo.dev.ctrus.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import mo.dev.ctrus.theme.FixedLightPrimaryText
import mo.dev.ctrus.theme.FixedLightSecondaryText
import mo.dev.ctrus.ui.common.GlassIconButton
import mo.dev.ctrus.ui.settings.SettingsSection
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
) {
    var filter by remember { mutableStateOf(InsightsFilter.THIS_WEEK) }
    var selectedDate by remember { mutableStateOf(Date()) }
    var selectedWeekDay by remember { mutableStateOf<WeeklyDayAggregate?>(null) }
    var selectedMonthDay by remember { mutableStateOf<MonthlyDayAggregate?>(null) }
    var showWeekPicker by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    val viewMode = filter.viewMode()
    val completedSessions = remember(sessions) { sessions.filter { it.endTimeEpochMilli != null } }

    val weeklySummary = remember(completedSessions, selectedDate) { InsightsSummary.weeklySummary(completedSessions, selectedDate) }
    val monthlySummary = remember(completedSessions, selectedDate) { InsightsSummary.monthlySummary(completedSessions, selectedDate) }

    fun clearDaySelection() {
        selectedWeekDay = null
        selectedMonthDay = null
    }

    // Scoped to the currently selected range (this week / this month / all sessions) — the
    // Summary card below reads its totals from this, not from every session ever recorded.
    val filteredSessions = remember(completedSessions, viewMode, selectedWeekDay, selectedMonthDay, weeklySummary, monthlySummary) {
        when (viewMode) {
            InsightsViewMode.WEEK -> filterByRange(completedSessions, weeklySummary.weekStartDate, addDaysExclusive(weeklySummary.weekEndDate, 1), selectedWeekDay?.date)
            InsightsViewMode.MONTH -> filterByRange(completedSessions, monthlySummary.monthStartDate, addDaysExclusive(monthlySummary.monthEndDate, 1), selectedMonthDay?.date)
            InsightsViewMode.ALL_SESSIONS -> completedSessions
        }
    }

    val metrics = remember(filteredSessions) { InsightsSummary.metrics(filteredSessions, profile) }

    // Sub-minute sessions would only ever display as "0m" in the list — not worth showing.
    val groupedSessions = remember(filteredSessions) { groupByDay(filteredSessions.filter { it.duration() >= 60_000L }) }

    val filterLabel = when (filter) {
        InsightsFilter.THIS_WEEK -> stringResource(R.string.insights_filter_this_week)
        InsightsFilter.LAST_WEEK -> stringResource(R.string.insights_filter_last_week)
        InsightsFilter.THIS_MONTH -> stringResource(R.string.insights_filter_this_month)
        InsightsFilter.LAST_MONTH -> stringResource(R.string.insights_filter_last_month)
        InsightsFilter.ALL_SESSIONS -> stringResource(R.string.insights_filter_all_sessions)
        InsightsFilter.SPECIFIC_WEEK -> stringResource(R.string.insights_filter_select_week)
        InsightsFilter.SPECIFIC_MONTH -> stringResource(R.string.insights_filter_select_month)
    }
    val todayLabel = stringResource(R.string.common_today)
    val yesterdayLabel = stringResource(R.string.common_yesterday)

    Scaffold(
        topBar = {
            // Mirrors ProfileInsightsView.swift's header: a close button and a filter control on
            // one row, with the big bold "<profile> Insights" title below it — not a Material
            // TopAppBar, which reads much flatter/more utilitarian by comparison.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                // Dark mode puts this header on a near-black sheet background — a fixed dark tint
                // there would be nearly invisible against the barely-there glass pill.
                val isDark = isSystemInDarkTheme()
                val pillIconTint = if (isDark) Color.White else FixedLightSecondaryText
                val pillTextColor = if (isDark) Color.White else FixedLightPrimaryText

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(onClick = onDismiss, icon = Icons.Filled.Close, contentDescription = stringResource(R.string.common_close))
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.35f))
                                .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(50))
                                .clickable { showFilterMenu = true }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        ) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = stringResource(R.string.insights_filter_content_description), tint = pillIconTint, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(filterLabel, style = MaterialTheme.typography.labelLarge, color = pillTextColor)
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false },
                            shape = RoundedCornerShape(20.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                        ) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.insights_filter_this_week)) }, onClick = { showFilterMenu = false; filter = InsightsFilter.THIS_WEEK; clearDaySelection(); selectedDate = Date() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.insights_filter_last_week)) }, onClick = {
                                showFilterMenu = false; filter = InsightsFilter.LAST_WEEK; clearDaySelection()
                                selectedDate = shiftDate(Date(), Calendar.WEEK_OF_YEAR, -1)
                            })
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                            DropdownMenuItem(text = { Text(stringResource(R.string.insights_filter_this_month)) }, onClick = { showFilterMenu = false; filter = InsightsFilter.THIS_MONTH; clearDaySelection(); selectedDate = Date() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.insights_filter_last_month)) }, onClick = {
                                showFilterMenu = false; filter = InsightsFilter.LAST_MONTH; clearDaySelection()
                                selectedDate = shiftDate(Date(), Calendar.MONTH, -1)
                            })
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                            DropdownMenuItem(
                                text = { Text(stringResource(if (viewMode == InsightsViewMode.MONTH) R.string.insights_filter_select_month else R.string.insights_filter_select_week)) },
                                onClick = {
                                    showFilterMenu = false
                                    if (viewMode == InsightsViewMode.MONTH) showMonthPicker = true else showWeekPicker = true
                                },
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                            DropdownMenuItem(text = { Text(stringResource(R.string.insights_filter_all_sessions)) }, onClick = { showFilterMenu = false; filter = InsightsFilter.ALL_SESSIONS; clearDaySelection() })
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.insights_screen_title, profile.name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
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
                    SettingsSection(title = stringResource(R.string.insights_summary_title)) {
                        InsightsSummaryRow(Icons.Filled.AccessTime, stringResource(R.string.insights_total_focus_time), DateFormatters.formatDurationHoursMinutes(metrics.totalFocusTime))
                        InsetDivider()
                        InsightsSummaryRow(Icons.Filled.Coffee, stringResource(R.string.insights_total_break_time), DateFormatters.formatDurationHoursMinutes(metrics.totalBreakTime))
                    }
                }
            }

            groupedSessions.forEach { (day, daySessions) ->
                item {
                    SettingsSection(title = DateFormatters.formatSessionDate(day, todayLabel, yesterdayLabel)) {
                        daySessions.forEachIndexed { index, session ->
                            SessionRow(session = session, profile = profile)
                            if (index != daySessions.lastIndex) InsetDivider()
                        }
                    }
                }
            }
        }
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
                Row {
                    Text(
                        DateFormatters.formatDurationHoursMinutes(selectedValueSeconds),
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = CtrusRoundedBold,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.insights_total_label),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Undo, contentDescription = stringResource(R.string.insights_reset))
            }
        } else {
            Column {
                Text(stringResource(R.string.insights_avg_focus_session), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(DateFormatters.formatDurationHoursMinutes(averageSeconds), style = MaterialTheme.typography.headlineMedium, fontFamily = CtrusRoundedBold)
            }
        }
    }
}

/** Matches [INSET]'s horizontal padding so the row's icon/text and value align with where
 *  [InsetDivider] starts and ends, instead of running flush to the card's raw edges. */
private val INSET = 16.dp

@Composable
private fun InsightsSummaryRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = INSET, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
    }
}

/** A HorizontalDivider inset to align with [INSET]-padded row content, instead of spanning the
 *  full card width edge-to-edge. */
@Composable
private fun InsetDivider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(horizontal = INSET),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun SessionRow(session: BlockedProfileSessionEntity, profile: BlockedProfileEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = INSET, vertical = 10.dp),
    ) {
        val durationSeconds = session.duration() / 1000.0
        Row {
            Text(
                DateFormatters.formatDurationHoursMinutes(durationSeconds),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontFamily = CtrusRoundedBold,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.insights_total_label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
        val breakSeconds = session.usedBreakDurationIncludingActive(profile)
        if (breakSeconds > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Coffee, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("${(breakSeconds / 60).toInt()}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
