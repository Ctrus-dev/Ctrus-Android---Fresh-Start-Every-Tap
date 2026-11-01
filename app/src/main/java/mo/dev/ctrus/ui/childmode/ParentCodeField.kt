package mo.dev.ctrus.ui.childmode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.R
import mo.dev.ctrus.childmode.ParentCredentials

/**
 * Port of iOS's ParentCodeField: six dots that fill in the theme colour as the code is typed on
 * the number pad. The real input is an invisible text field laid over the dots; reaching the
 * sixth digit calls [onComplete] by itself.
 */
@Composable
fun ParentCodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    themeColor: Color,
    onComplete: (String) -> Unit,
    enabled: Boolean = true,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(enabled) {
        if (enabled) {
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.alpha(if (enabled) 1f else 0.4f),
        ) {
            repeat(ParentCredentials.CODE_LENGTH) { index ->
                Box(
                    Modifier
                        .size(18.dp)
                        .background(if (index < code.length) themeColor else Color.Transparent, CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape),
                )
            }
        }
        val description = stringResource(R.string.parent_confirm_code)
        BasicTextField(
            value = code,
            onValueChange = { newValue ->
                val digits = newValue.filter(Char::isDigit).take(ParentCredentials.CODE_LENGTH)
                onCodeChange(digits)
                if (digits.length == ParentCredentials.CODE_LENGTH) onComplete(digits)
            },
            enabled = enabled,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier
                .matchParentSize()
                .alpha(0f)
                .focusRequester(focusRequester)
                .semantics { contentDescription = description },
        )
    }
}
