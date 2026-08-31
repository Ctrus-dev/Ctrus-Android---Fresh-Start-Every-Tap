package mo.dev.ctrus.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mo.dev.ctrus.data.MonthlyDayAggregate
import java.util.Calendar

/** Android equivalent of MonthlySessionChart.swift's calendar-grid heatmap. Tap or drag across cells to select a day, matching iOS's `DragGesture` cell hit-testing. */
@Composable
fun MonthlyHeatmapGrid(
    days: List<MonthlyDayAggregate>,
    selectedDay: MonthlyDayAggregate?,
    themeColor: Color,
    onDaySelected: (MonthlyDayAggregate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weeks = remember(days) { buildWeeks(days) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    fun selectAt(x: Float, y: Float) {
        if (weeks.isEmpty() || canvasSize.width == 0) return
        val cellSize = canvasSize.width.toFloat() / 7
        val col = (x / cellSize).toInt().coerceIn(0, 6)
        val row = (y / cellSize).toInt()
        if (row < 0 || row >= weeks.size) return
        val day = weeks[row].getOrNull(col) ?: return
        onDaySelected(day)
    }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height((weeks.size * 44).dp)
                .onSizeChanged { canvasSize = it }
                .pointerInput(weeks) { detectTapGestures { offset -> selectAt(offset.x, offset.y) } }
                .pointerInput(weeks) { detectDragGestures { change, _ -> selectAt(change.position.x, change.position.y) } },
        ) {
            drawGrid(weeks, selectedDay, themeColor)
        }
        Spacer(Modifier.height(8.dp))
        Legend(themeColor)
    }
}

@Composable
private fun Legend(themeColor: Color) {
    Row {
        listOf(
            stringResource(mo.dev.ctrus.R.string.insights_legend_under_1h) to 0.3f,
            stringResource(mo.dev.ctrus.R.string.insights_legend_1_3h) to 0.5f,
            stringResource(mo.dev.ctrus.R.string.insights_legend_3_5h) to 0.7f,
            stringResource(mo.dev.ctrus.R.string.insights_legend_over_5h) to 0.9f,
        ).forEach { (label, alpha) ->
            Row(modifier = Modifier.padding(end = 12.dp)) {
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp))) {
                    Canvas(modifier = Modifier.size(10.dp)) {
                        drawRect(themeColor.copy(alpha = alpha))
                    }
                }
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun colorForHours(hours: Double, themeColor: Color): Color = when {
    hours <= 0 -> Color.Gray.copy(alpha = 0.15f)
    hours < 1 -> themeColor.copy(alpha = 0.3f)
    hours < 3 -> themeColor.copy(alpha = 0.5f)
    hours < 5 -> themeColor.copy(alpha = 0.7f)
    else -> themeColor.copy(alpha = 0.9f)
}

private fun DrawScope.drawGrid(weeks: List<List<MonthlyDayAggregate?>>, selectedDay: MonthlyDayAggregate?, themeColor: Color) {
    if (weeks.isEmpty()) return
    val cellSize = size.width / 7
    val gap = cellSize * 0.06f
    val squareSize = cellSize - gap

    weeks.forEachIndexed { row, week ->
        week.forEachIndexed { col, day ->
            if (day == null) return@forEachIndexed
            val hours = day.totalSessionSeconds / 3600.0
            val topLeft = Offset(col * cellSize + gap / 2, row * cellSize + gap / 2)
            val isSelected = selectedDay?.date == day.date

            drawRoundRect(
                color = colorForHours(hours, themeColor),
                topLeft = topLeft,
                size = Size(squareSize, squareSize),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
            if (isSelected) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = topLeft,
                    size = Size(squareSize, squareSize),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )
            }

            val textColor = if (hours > 3) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY
            drawContext.canvas.nativeCanvas.drawText(
                day.dayOfMonth.toString(),
                topLeft.x + squareSize / 2,
                topLeft.y + squareSize / 2 + 4.dp.toPx(),
                android.graphics.Paint().apply {
                    color = textColor
                    textSize = 10.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                },
            )
        }
    }
}

private fun buildWeeks(days: List<MonthlyDayAggregate>): List<List<MonthlyDayAggregate?>> {
    if (days.isEmpty()) return emptyList()
    val calendar = Calendar.getInstance()
    val firstDayOfWeek = calendar.firstDayOfWeek
    val firstDayCal = (calendar.clone() as Calendar).apply { time = days.first().date }
    val firstWeekday = firstDayCal.get(Calendar.DAY_OF_WEEK)
    val leadingPad = ((firstWeekday - firstDayOfWeek) + 7) % 7

    val weeks = mutableListOf<MutableList<MonthlyDayAggregate?>>()
    var currentWeek = MutableList<MonthlyDayAggregate?>(leadingPad) { null }
    for (day in days) {
        currentWeek.add(day)
        if (currentWeek.size == 7) {
            weeks.add(currentWeek)
            currentWeek = mutableListOf()
        }
    }
    if (currentWeek.isNotEmpty()) {
        while (currentWeek.size < 7) currentWeek.add(null)
        weeks.add(currentWeek)
    }
    return weeks
}
