package mo.dev.ctrus.ui.session

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val HOLD_DURATION_MS = 800

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
    onConfirm: () -> Unit
) {
    val animatable = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(backgroundColor.copy(alpha = 0.18f))
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
                .background(backgroundColor.copy(alpha = 0.45f))
        )
        Text(title, color = contentColor, style = MaterialTheme.typography.titleMedium)
    }
}
