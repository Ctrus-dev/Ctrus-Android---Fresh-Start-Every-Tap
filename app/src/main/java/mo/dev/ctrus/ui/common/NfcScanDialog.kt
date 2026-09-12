package mo.dev.ctrus.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import mo.dev.ctrus.R
import mo.dev.ctrus.nfc.NfcAvailability

/**
 * Shared "scan your Ctrus tag" dialog — used both for starting/stopping a session
 * ([mo.dev.ctrus.ui.strategy.PendingRequirementDialog]) and for registering a physical-unblock
 * tag during profile creation ([mo.dev.ctrus.ui.profile.PhysicalUnlocksFields]), which used to be
 * two separately hand-written `AlertDialog`s that had drifted out of visual sync.
 */
@Composable
fun NfcScanDialog(
    availability: NfcAvailability,
    onDismiss: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    message: String? = null,
    errorMessage: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // Opened from both a plain full-screen destination and from inside a ModalBottomSheet
        // (guided profile creation) — usePlatformDefaultWidth's platform-theme-derived sizing
        // rendered visibly wider/taller in the latter, so this pins an explicit width instead of
        // leaving it to whatever window the dialog happens to be created against.
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.86f),
        title = { Text(stringResource(R.string.nfc_scan_title)) },
        text = {
            when {
                availability == NfcAvailability.NO_HARDWARE -> Text(stringResource(R.string.nfc_no_hardware))
                availability == NfcAvailability.DISABLED -> Text(stringResource(R.string.nfc_disabled))
                errorMessage != null -> Text(errorMessage)
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(
                        message ?: stringResource(R.string.nfc_hold_near_tag),
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (availability == NfcAvailability.DISABLED) {
                TextButton(onClick = onOpenNfcSettings) { Text(stringResource(R.string.nfc_open_settings)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
}
