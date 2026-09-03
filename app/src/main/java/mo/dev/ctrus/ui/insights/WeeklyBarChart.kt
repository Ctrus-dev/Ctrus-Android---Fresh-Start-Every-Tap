package mo.dev.ctrus.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mo.dev.ctrus.R
import mo.dev.ctrus.data.WeeklyDayAggregate
import java.util.Calendar

private val CHART_HEIGHT = 160.dp

/** Android equivalent of WeeklySessionChart.swift's `Chart { BarMark }` — a hand-drawn Canvas bar chart, since Swift Charts has no direct Android counterpart. Tap or drag to select a day, matching iOS's `.chartXSelection` drag behavior. Right-side axis labels mirror Swift Charts' default y-axis. */
@Composable
fun WeeklyBarChart(
    days: List<WeeklyDayAggregate>,
    selectedDay: WeeklyDayAggregate?,
    themeColor: Color,
    onDaySelected: (WeeklyDayAggregate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val maxDuration = (days.maxOfOrNull { it.totalSessionSeconds } ?: 0.0).coerceAtLeast(1.0)
    val axisTopMinutes = niceAxisMaxMinutes((maxDuration / 60.0).let { Math.ceil(it).toInt() })

    fun selectAt(x: Float) {
        if (days.isEmpty() || canvasSize.width == 0) return
        val barWidth = canvasSize.width.toFloat() / days.size
        val index = (x / barWidth).toInt().coerceIn(0, days.size - 1)
        onDaySelected(days[index])
    }

    // Fixed 3-letter abbreviations from resources rather than SimpleDateFormat's locale-dependent
    // "EEE" pattern, which on-device rendered full weekday names for pt-PT and wrapped mid-word
    // across these narrow per-day columns.
    val weekdayShort = stringArrayResource(R.array.weekday_short)
    val calendar = remember { Calendar.getInstance() }

    Row(modifier = modifier) {
        // Bars and day labels share this column so both are laid out against the exact same
        // width — putting the day-label row directly under the outer Row instead (full width,
        // including the axis gutter) made it wider than the bars above it, drifting the labels
        // out of alignment with their bars further right in the week.
        Column(modifier = Modifier.weight(1f)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CHART_HEIGHT)
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(days) { detectTapGestures { offset -> selectAt(offset.x) } }
                    .pointerInput(days) { detectDragGestures { change, _ -> selectAt(change.position.x) } },
            ) {
                drawBars(days, selectedDay, axisTopMinutes * 60.0, themeColor)
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                days.forEach { day ->
                    calendar.time = day.date
                    val label = "${weekdayShort[calendar.get(Calendar.DAY_OF_WEEK) - 1]} ${calendar.get(Calendar.DAY_OF_MONTH)}"
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        AxisLabels(topMinutes = axisTopMinutes, modifier = Modifier.height(CHART_HEIGHT).padding(start = 6.dp))
    }
}

@Composable
private fun AxisLabels(topMinutes: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(30.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.Start) {
        listOf(topMinutes, topMinutes * 2 / 3, topMinutes / 3, 0).forEach { minutes ->
            Text("${minutes}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Rounds up to a "nice" tick value, mirroring Swift Charts' automatic y-axis scaling. */
private fun niceAxisMaxMinutes(maxMinutes: Int): Int {
    if (maxMinutes <= 0) return 10
    val steps = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240, 300, 360, 480, 600)
    return steps.firstOrNull { it >= maxMinutes } ?: (((maxMinutes / 60) + 1) * 60)
}

private fun DrawScope.drawBars(days: List<WeeklyDayAggregate>, selectedDay: WeeklyDayAggregate?, axisMaxSeconds: Double, themeColor: Color) {
    if (days.isEmpty()) return
    val barSlotWidth = size.width / days.size
    val barWidth = barSlotWidth * 0.6f

    days.forEachIndexed { index, day ->
        val heightFraction = (day.totalSessionSeconds / axisMaxSeconds).toFloat().coerceIn(0f, 1f)
        val barHeight = (size.height * heightFraction).coerceAtLeast(if (day.totalSessionSeconds > 0) 4f else 0f)
        val isSelected = selectedDay?.date == day.date
        val left = index * barSlotWidth + (barSlotWidth - barWidth) / 2f

        drawRoundRect(
            color = if (isSelected) themeColor.copy(alpha = 0.7f) else themeColor,
            topLeft = Offset(left, size.height - barHeight),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(6f, 6f),
        )
    }
}
