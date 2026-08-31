package mo.dev.ctrus.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class NfcAvailability { AVAILABLE, NO_HARDWARE, DISABLED }

/**
 * Android replacement for NFCScannerUtil.swift's read path. iOS pops a system NFC sheet backed
 * by an `NFCTagReaderSession`; Android has no equivalent system UI, so this uses
 * `NfcAdapter.enableReaderMode` scoped to exactly when a scan is actually pending (see
 * MainActivity's `LaunchedEffect` on `pendingRequirement`) rather than a manifest-declared
 * foreground dispatch that would be active for the whole Activity lifecycle.
 */
class NfcScanController(private val activity: Activity, private val lifecycleScope: LifecycleCoroutineScope) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    val availability: NfcAvailability
        get() = when {
            adapter == null -> NfcAvailability.NO_HARDWARE
            !adapter.isEnabled -> NfcAvailability.DISABLED
            else -> NfcAvailability.AVAILABLE
        }

    fun startScan(onTag: (String) -> Unit) {
        val adapter = adapter ?: return
        adapter.enableReaderMode(
            activity,
            { tag ->
                lifecycleScope.launch {
                    val code = NfcTagDecoder.decode(tag)
                    withContext(Dispatchers.Main) { onTag(code) }
                }
            },
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                // iOS's NFCTagReaderSession is silent too — reader mode plays the system
                // "tag discovered" tone by default, which this suppresses.
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null,
        )
    }

    fun stopScan() {
        adapter?.disableReaderMode(activity)
    }
}
