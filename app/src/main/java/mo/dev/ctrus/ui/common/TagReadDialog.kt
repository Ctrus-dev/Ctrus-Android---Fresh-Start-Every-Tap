package mo.dev.ctrus.ui.common

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import mo.dev.ctrus.nfc.NfcScanController
import mo.dev.ctrus.nfc.NfcTagRead

/**
 * Reads one tag while shown (port of iOS's TagReader): registers with [nfcScanController] and
 * shows [NfcScanDialog] until a tag is read or [onDismiss] closes it. [onRead] gets the tag and
 * should usually close this dialog too.
 */
@Composable
fun TagReadDialog(
    nfcScanController: NfcScanController,
    message: String,
    onRead: (NfcTagRead) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        nfcScanController.startScan(onRead)
        onDispose { nfcScanController.stopScan() }
    }
    NfcScanDialog(
        availability = nfcScanController.availability,
        onDismiss = onDismiss,
        onOpenNfcSettings = { mo.dev.ctrus.nfc.openNfcSettings(context) },
        message = message,
    )
}
