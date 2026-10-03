package mo.dev.ctrus.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor

/**
 * A single-line text field with no built-in padding or minimum height, for rows inside a card
 * (Settings' recovery code, the profile name). Material's OutlinedTextField always reserves 56dp
 * plus its own content padding on top of the row's 16dp inset, which left a tall blank band
 * above and below the text. Here the row's own padding is the only spacing, so these rows match
 * every other row's height and the text starts flush with the other rows' text.
 */
@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier,
        decorationBox = { innerTextField ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                }
                innerTextField()
            }
        },
    )
}
