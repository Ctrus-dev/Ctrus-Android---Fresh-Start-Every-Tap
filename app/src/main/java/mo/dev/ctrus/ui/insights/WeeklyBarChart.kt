package mo.dev.ctrus.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.data.WeeklyDayAggregate

/** Android equivalent of WeeklySessionChart.swift's `Chart { BarMark }` — a hand-drawn Canvas bar chart, since Swift Charts has no direct Android counterpart. Tap or drag to select a day, matching iOS's `.chartXSelection` drag behavior. */
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

    fun selectAt(x: Float) {
        if (days.isEmpty() || canvasSize.width == 0) return
        val barWidth = canvasSize.width.toFloat() / days.size
        val index = (x / barWidth).toInt().coerceIn(0, days.size - 1)
        onDaySelected(days[index])
    }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .onSizeChanged { canvasSize = it }
                .pointerInput(days) { detectTapGestures { offset -> selectAt(offset.x) } }
                .pointerInput(days) { detectDragGestures { change, _ -> selectAt(change.position.x) } },
        ) {
            drawBars(days, selectedDay, maxDuration, themeColor)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            days.forEach { day ->
                Text(
                    day.dayName,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun DrawScope.drawBars(days: List<WeeklyDayAggregate>, selectedDay: WeeklyDayAggregate?, maxDuration: Double, themeColor: Color) {
    if (days.isEmpty()) return
    val barSlotWidth = size.width / days.size
    val barWidth = barSlotWidth * 0.6f

    days.forEachIndexed { index, day ->
        val heightFraction = (day.totalSessionSeconds / maxDuration).toFloat().coerceIn(0f, 1f)
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
