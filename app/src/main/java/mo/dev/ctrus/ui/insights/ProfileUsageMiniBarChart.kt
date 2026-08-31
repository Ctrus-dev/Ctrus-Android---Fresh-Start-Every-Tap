package mo.dev.ctrus.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mo.dev.ctrus.data.BlockedProfileSessionEntity
import mo.dev.ctrus.data.WeeklySessionAggregator
import mo.dev.ctrus.data.WeeklySessionInterval
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Android equivalent of ProfileSummaryRow.swift's `ProfileUsageMiniBarChart` — the "barrinha dos
 * dias" weekly sparkline shown next to each profile row on Home, sized 118x62dp per iOS
 * (HomeProfilesListView.swift), tappable to open Insights.
 */
@Composable
fun ProfileUsageMiniBarChart(sessions: List<BlockedProfileSessionEntity>, themeColor: Color, forcedLight: Boolean = true) {
    val weekStart = remember { WeeklySessionAggregator.startOfWeek(Date()) }
    val values = remember(sessions, weekStart) {
        val intervals = sessions.mapNotNull { session ->
            val end = session.endTimeEpochMilli ?: return@mapNotNull null
            WeeklySessionInterval(Date(session.startTimeEpochMilli), Date(end))
        }
        WeeklySessionAggregator.aggregate(intervals, weekStart).dailyDurations
    }
    val maxValue = remember(values) { max(values.maxOrNull() ?: 0.0, 1.0) }
    val dayLabels = remember(weekStart) {
        val format = SimpleDateFormat("EEEEE", Locale.getDefault())
        val cal = Calendar.getInstance().apply { time = weekStart }
        (0 until 7).map { offset ->
            cal.time = weekStart
            cal.add(Calendar.DAY_OF_YEAR, offset)
            format.format(cal.time)
        }
    }
    val labelColor = if (forcedLight) Color(0xFF6B6B70).copy(alpha = 0.7f) else Color.Gray.copy(alpha = 0.7f)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth().height(45.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEach { value ->
                val normalized = (value / maxValue).toFloat()
                val alpha = if (value > 0) 0.36f + normalized * 0.64f else 0.14f
                val barHeight = if (value > 0) max(5f, normalized * 25f) else 3f
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(barHeight.dp)
                        .clip(RoundedCornerShape(2.dp)),
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(barHeight.dp)) {
                        drawRect(color = themeColor.copy(alpha = alpha))
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().height(9.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            dayLabels.forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = TextStyle(fontSize = 6.sp, fontWeight = FontWeight.SemiBold, color = labelColor),
                )
            }
        }
    }
}
