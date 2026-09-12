package mo.dev.ctrus.ui.common

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit

/**
 * Single-line text that shrinks to fit whatever width it's given instead of overflowing or
 * wrapping — mirrors SwiftUI's `.lineLimit(1).minimumScaleFactor(...)`, which the iOS action
 * buttons use so longer localized strings (e.g. Portuguese's "Pressiona para começar a pausa")
 * shrink instead of clipping against a pill's rounded ends.
 */
@Composable
fun AutoSizeText(
    text: String,
    color: Color,
    style: TextStyle,
    minFontSize: TextUnit,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = textAlign),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = style.fontSize),
    )
}
