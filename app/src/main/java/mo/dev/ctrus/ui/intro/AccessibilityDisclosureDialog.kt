package mo.dev.ctrus.ui.intro

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.R

/**
 * Google Play requires a prominent in-app disclosure and explicit consent before sending users
 * to the system Accessibility permission screen — this dialog is that gate. Modeled on Switchly's
 * `AccessibilityDisclosure` (`gitlab.com/Saltyy/switchly-public`), which uses the same
 * required-checkbox pattern for the same policy reason.
 *
 * Text is scoped to what BlockingAccessibilityService actually does today (read the foreground
 * app's package name to decide whether to show the block screen) — update it if browser/domain
 * inspection is added later.
 */
@Composable
fun AccessibilityDisclosureDialog(onAgree: () -> Unit, onDismiss: () -> Unit) {
    var agreed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.a11y_disclosure_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.a11y_disclosure_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = agreed, onCheckedChange = { agreed = it })
                    Text(stringResource(R.string.a11y_disclosure_agree_checkbox), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAgree, enabled = agreed) { Text(stringResource(R.string.a11y_disclosure_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
