package mo.dev.ctrus.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Port of iOS's ToastBanner: a short confirmation that slides in at the bottom, over everything
 * else, and goes away by itself after [displayMillis].
 */
@Composable
fun ToastBanner(message: String?, onDismiss: () -> Unit, displayMillis: Long = 3_000) {
    // Keeps the last text on screen while it slides out, after [message] is already null.
    var shownMessage by remember { mutableStateOf(message) }
    if (message != null) shownMessage = message

    LaunchedEffect(message) {
        if (message != null) {
            delay(displayMillis)
            onDismiss()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = message != null,
            enter = slideInVertically(tween(250)) { it } + fadeIn(tween(250)),
            exit = slideOutVertically(tween(250)) { it } + fadeOut(tween(250)),
        ) {
            Text(
                shownMessage.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                    .background(Color.Black.copy(alpha = 0.85f), CircleShape)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }
    }
}
