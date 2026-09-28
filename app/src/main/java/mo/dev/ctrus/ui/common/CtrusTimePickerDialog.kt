package mo.dev.ctrus.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mo.dev.ctrus.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Same thickness as the profile balloons' border on Home (HomeScreen's BaseBorderWidth). */
private val SelectedBorderWidth = 3.5.dp
private val BoxShape = RoundedCornerShape(12.dp)
private val DialSize = 256.dp
private val SelectorRadius = 24.dp
private val OuterNumberRadius = 100.dp
private val InnerNumberRadius = 66.dp

private enum class TimeField { HOUR, MINUTE }

/** True when [label] sits under the selector circle (within 2 minutes, wrapping 59 → 0). */
private fun minuteUnderSelector(minute: Int, label: Int): Boolean {
    val diff = kotlin.math.abs(minute - label)
    return minOf(diff, 60 - diff) <= 2
}

/**
 * Hand-rolled replacement for Material 3's TimePicker, which can only fill the selected
 * hour/minute box and AM/PM segment and has no way to outline them. The layout is the same
 * (hour : minute boxes, AM/PM, then a clock dial), but the selected box/segment has no fill and a
 * theme-colored border instead, matching the profile balloons. Picking an hour moves on to
 * minutes, like the Material picker. Taps on the minute dial snap to 5-minute steps and drags
 * snap to single minutes. In 24h mode the dial uses two rings (00–11 outside, 12–23 inside).
 */
@Composable
fun CtrusTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    var hour by remember { mutableIntStateOf(initialHour) }
    var minute by remember { mutableIntStateOf(initialMinute) }
    var field by remember { mutableStateOf(TimeField.HOUR) }

    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val hourText = if (is24Hour) "%02d".format(hour) else "%02d".format(if (hour % 12 == 0) 12 else hour % 12)
                    TimeBox(hourText, selected = field == TimeField.HOUR) { field = TimeField.HOUR }
                    Text(":", fontSize = 57.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 4.dp))
                    TimeBox("%02d".format(minute), selected = field == TimeField.MINUTE) { field = TimeField.MINUTE }
                    if (!is24Hour) {
                        Spacer(Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            PeriodBox("AM", selected = hour < 12) { if (hour >= 12) hour -= 12 }
                            PeriodBox("PM", selected = hour >= 12) { if (hour < 12) hour += 12 }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
                ClockDial(
                    field = field,
                    hour = hour,
                    minute = minute,
                    is24Hour = is24Hour,
                    onHourChange = { h -> hour = if (is24Hour) h else h + (if (hour >= 12) 12 else 0) },
                    onMinuteChange = { minute = it },
                    onHourPicked = { field = TimeField.MINUTE },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(hour, minute) }) { Text(stringResource(R.string.common_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun Modifier.selectableBox(selected: Boolean): Modifier {
    val base = clip(BoxShape)
    return if (selected) {
        base.border(SelectedBorderWidth, MaterialTheme.colorScheme.primary, BoxShape)
    } else {
        base.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
    }
}

@Composable
private fun TimeBox(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(width = 96.dp, height = 80.dp).selectableBox(selected).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 57.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PeriodBox(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(width = 52.dp, height = 38.dp).selectableBox(selected).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClockDial(
    field: TimeField,
    hour: Int,
    minute: Int,
    is24Hour: Boolean,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onHourPicked: () -> Unit,
) {
    val themeColor = MaterialTheme.colorScheme.primary
    val dialColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val numberColor = MaterialTheme.colorScheme.onSurface
    val textMeasurer = rememberTextMeasurer()
    val numberStyle = MaterialTheme.typography.bodyLarge

    // Clockwise degrees from 12 o'clock, plus distance from the center, for a touch position.
    fun polar(position: Offset, center: Offset): Pair<Double, Float> {
        val dx = position.x - center.x
        val dy = position.y - center.y
        val degrees = (Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())) + 360) % 360
        return degrees to hypot(dx, dy)
    }

    fun select(position: Offset, center: Offset, innerThresholdPx: Float, snapMinutesTo: Int) {
        val (degrees, distance) = polar(position, center)
        when (field) {
            TimeField.HOUR -> {
                val index = (degrees / 30).roundToInt() % 12
                onHourChange(if (is24Hour && distance < innerThresholdPx) index + 12 else index)
            }
            TimeField.MINUTE -> {
                val raw = (degrees / 6).roundToInt() % 60
                onMinuteChange(((raw.toDouble() / snapMinutesTo).roundToInt() * snapMinutesTo) % 60)
            }
        }
    }

    Canvas(
        modifier = Modifier
            .size(DialSize)
            .pointerInput(field, is24Hour, hour) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val threshold = ((OuterNumberRadius + InnerNumberRadius) / 2).toPx()
                detectTapGestures { position ->
                    select(position, center, threshold, snapMinutesTo = 5)
                    if (field == TimeField.HOUR) onHourPicked()
                }
            }
            .pointerInput(field, is24Hour, hour) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val threshold = ((OuterNumberRadius + InnerNumberRadius) / 2).toPx()
                detectDragGestures(
                    onDragStart = { select(it, center, threshold, snapMinutesTo = 1) },
                    onDragEnd = { if (field == TimeField.HOUR) onHourPicked() },
                ) { change, _ -> select(change.position, center, threshold, snapMinutesTo = 1) }
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(dialColor, radius = size.minDimension / 2f)

        fun positionAt(index: Int, radius: Dp): Offset {
            val radians = Math.toRadians(index * 30.0)
            return Offset(center.x + radius.toPx() * sin(radians).toFloat(), center.y - radius.toPx() * cos(radians).toFloat())
        }

        // Selector: line from the center to the selected value, and a filled circle under it.
        val (selectedAngleIndex, selectedRadius) = when (field) {
            TimeField.HOUR -> (hour % 12).toDouble() to (if (is24Hour && hour >= 12) InnerNumberRadius else OuterNumberRadius)
            TimeField.MINUTE -> minute / 5.0 to OuterNumberRadius
        }
        val selectedRadians = Math.toRadians(selectedAngleIndex * 30.0)
        val selectorCenter = Offset(
            center.x + selectedRadius.toPx() * sin(selectedRadians).toFloat(),
            center.y - selectedRadius.toPx() * cos(selectedRadians).toFloat(),
        )
        drawLine(themeColor, center, selectorCenter, strokeWidth = 2.dp.toPx())
        drawCircle(themeColor, radius = 4.dp.toPx(), center = center)
        drawCircle(themeColor, radius = SelectorRadius.toPx(), center = selectorCenter)

        fun drawNumber(label: String, at: Offset, style: TextStyle) {
            val layout = textMeasurer.measure(label, style)
            drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
        }

        fun labelStyle(isSelected: Boolean, base: TextStyle) = base.copy(color = if (isSelected) Color.White else numberColor)

        when (field) {
            TimeField.HOUR -> {
                for (i in 0 until 12) {
                    val outerValue = i
                    val outerLabel = if (is24Hour) (if (i == 0) "00" else "$i") else (if (i == 0) "12" else "$i")
                    val outerSelected = if (is24Hour) hour == outerValue else hour % 12 == i
                    drawNumber(outerLabel, positionAt(i, OuterNumberRadius), labelStyle(outerSelected, numberStyle))
                    if (is24Hour) {
                        val innerValue = i + 12
                        drawNumber("$innerValue", positionAt(i, InnerNumberRadius), labelStyle(hour == innerValue, numberStyle.copy(fontSize = 14.sp)))
                    }
                }
            }
            TimeField.MINUTE -> {
                for (i in 0 until 12) {
                    drawNumber("${i * 5}", positionAt(i, OuterNumberRadius), labelStyle(minuteUnderSelector(minute, i * 5), numberStyle))
                }
            }
        }
    }
}
