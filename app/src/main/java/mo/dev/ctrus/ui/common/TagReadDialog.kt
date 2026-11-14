package mo.dev.ctrus.ui.common

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import mo.dev.ctrus.R
import androidx.compose.ui.platform.LocalContext
import mo.dev.ctrus.nfc.NfcScanController
import mo.dev.ctrus.nfc.NfcTagRead

/**
 * Reads one tag while shown (port of iOS's TagReader): registers with [nfcScanController] and
 * shows [NfcScanDialog] until a tag is read or [onDismiss] closes it. [onRead] gets the tag and
 * should usually close this dialog too. With [requiresGenuineCtrus] (registering a Ctrus) a tag
 * without a valid Ctrus signature is turned away with "This isn't a Ctrus." instead, like iOS.
 * [rejectionMessage] turns away a tag that can't be used here, showing the reason it returns
 * (null for a tag that's fine), like iOS's `TagReader.read(rejectionMessage:)`.
 */
@Composable
fun TagReadDialog(
    nfcScanController: NfcScanController,
    message: String,
    onRead: (NfcTagRead) -> Unit,
    onDismiss: () -> Unit,
    requiresGenuineCtrus: Boolean = false,
    rejectionMessage: ((NfcTagRead) -> String?)? = null,
) {
    val context = LocalContext.current
    val notCtrusMessage = stringResource(R.string.nfc_not_a_ctrus)
    var errorMessage by remember { mutableStateOf<String?>(null) }
    DisposableEffect(errorMessage) {
        if (errorMessage == null) {
            nfcScanController.startScan { read ->
                errorMessage = when {
                    requiresGenuineCtrus && !read.isGenuineCtrus -> notCtrusMessage
                    else -> rejectionMessage?.invoke(read)
                }
                if (errorMessage == null) onRead(read)
            }
        }
        onDispose { nfcScanController.stopScan() }
    }
    NfcScanDialog(
        availability = nfcScanController.availability,
        onDismiss = onDismiss,
        onOpenNfcSettings = { mo.dev.ctrus.nfc.openNfcSettings(context) },
        message = message,
        errorMessage = errorMessage,
    )
}
