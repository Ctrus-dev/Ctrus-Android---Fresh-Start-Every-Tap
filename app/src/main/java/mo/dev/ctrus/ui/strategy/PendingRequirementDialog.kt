package mo.dev.ctrus.ui.strategy

import androidx.compose.foundation.layout.Row
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
import mo.dev.ctrus.R
import mo.dev.ctrus.nfc.NfcAvailability
import mo.dev.ctrus.session.PendingRequirement
import mo.dev.ctrus.strategy.StrategyRequirement
import mo.dev.ctrus.util.resolve

/**
 * iOS pops a system NFC sheet (`NFCTagReaderSession`'s built-in UI) while scanning; Android has
 * no equivalent system UI, so this is the app-drawn stand-in — shown for exactly as long as
 * [mo.dev.ctrus.nfc.NfcScanController] is actively in reader mode (see MainActivity).
 */
@Composable
fun PendingRequirementDialog(
    pending: PendingRequirement,
    nfcAvailability: NfcAvailability,
    onOpenNfcSettings: () -> Unit,
    onCancel: () -> Unit,
) {
    when (pending.requirement) {
        StrategyRequirement.None -> Unit

        StrategyRequirement.ScanNfcTag -> AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(stringResource(R.string.nfc_scan_title)) },
            text = {
                when (nfcAvailability) {
                    NfcAvailability.NO_HARDWARE -> Text(stringResource(R.string.nfc_no_hardware))
                    NfcAvailability.DISABLED -> Text(stringResource(R.string.nfc_disabled))
                    NfcAvailability.AVAILABLE -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(
                            pending.message?.resolve() ?: stringResource(R.string.nfc_hold_near_tag),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                if (nfcAvailability == NfcAvailability.DISABLED) {
                    TextButton(onClick = onOpenNfcSettings) { Text(stringResource(R.string.nfc_open_settings)) }
                } else {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
                }
            },
        )
    }
}
