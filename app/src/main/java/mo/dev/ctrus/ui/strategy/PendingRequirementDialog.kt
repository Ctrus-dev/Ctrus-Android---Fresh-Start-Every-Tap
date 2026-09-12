package mo.dev.ctrus.ui.strategy

import androidx.compose.runtime.Composable
import mo.dev.ctrus.nfc.NfcAvailability
import mo.dev.ctrus.session.PendingRequirement
import mo.dev.ctrus.strategy.StrategyRequirement
import mo.dev.ctrus.ui.common.NfcScanDialog
import mo.dev.ctrus.util.resolve

/**
 * iOS pops a system NFC sheet (`NFCTagReaderSession`'s built-in UI) while scanning; Android has
 * no equivalent system UI, so [NfcScanDialog] is the app-drawn stand-in — shown for exactly as
 * long as a scan handler is registered with [mo.dev.ctrus.nfc.NfcScanController] (see
 * MainActivity; reader mode itself stays claimed the whole time the app is foregrounded).
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

        StrategyRequirement.ScanNfcTag -> NfcScanDialog(
            availability = nfcAvailability,
            onDismiss = onCancel,
            onOpenNfcSettings = onOpenNfcSettings,
            message = pending.message?.resolve(),
        )
    }
}
