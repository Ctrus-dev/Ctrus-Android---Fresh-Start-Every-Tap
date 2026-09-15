package mo.dev.ctrus.ui.session

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import mo.dev.ctrus.ui.common.AutoSizeText

private const val HOLD_DURATION_MS = 800

// Keeps long, localized labels (e.g. Portuguese's "Pressiona para começar a pausa") from
// crowding — let alone clipping against — this pill's rounded ends.
private val LabelHorizontalPadding = 16.dp

/**
 * Mirrors ActiveSessionActionButton(requiresLongPress: true) on iOS: the user must
 * hold for HOLD_DURATION_MS before the action fires, with a fill animation as feedback.
 */
@Composable
fun HoldToConfirmButton(
    title: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    contentColor: Color = Color.White,
    icon: ImageVector? = null,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    height: Dp = 56.dp,
    restingAlpha: Float = 0.18f,
    fillColor: Color = backgroundColor,
    fillAlpha: Float = 0.45f,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
    iconSize: Dp = 18.dp,
    onConfirm: () -> Unit
) {
    val animatable = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor.copy(alpha = restingAlpha))
            .then(if (borderColor != null) Modifier.border(borderWidth, borderColor, RoundedCornerShape(20.dp)) else Modifier)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val holdJob = scope.launch {
                            animatable.snapTo(0f)
                            animatable.animateTo(1f, tween(HOLD_DURATION_MS, easing = LinearEasing))
                            // Only reached if the hold ran to completion without being cancelled.
                            onConfirm()
                            animatable.snapTo(0f)
                        }
                        tryAwaitRelease()
                        holdJob.cancel()
                        if (animatable.value < 0.999f) {
                            animatable.snapTo(0f)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = animatable.value.coerceIn(0f, 1f))
                .background(fillColor.copy(alpha = fillAlpha))
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = LabelHorizontalPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(iconSize))
                Spacer(Modifier.width(6.dp))
            }
            // weight(fill = false): short titles keep their natural size (so the icon+text pair
            // stays centered as a unit via the Row's own Arrangement.Center), while a title too
            // long to fit the remaining space shrinks via AutoSizeText instead of overflowing.
            AutoSizeText(
                text = title,
                color = contentColor,
                style = textStyle,
                minFontSize = 12.sp,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
