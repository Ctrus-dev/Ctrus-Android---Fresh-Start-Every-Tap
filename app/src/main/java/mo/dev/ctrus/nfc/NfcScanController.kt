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
 * by an `NFCTagReaderSession`; Android has no equivalent system UI.
 *
 * `NfcAdapter.enableReaderMode` is claimed for as long as [attach]/[detach] say the Activity is
 * resumed (call from onResume/onPause) rather than only while a scan dialog is on screen — reader
 * mode used to be enabled/disabled per dialog, which left every other moment the app was open
 * (browsing Settings, the home screen, etc.) unclaimed, so a tag tap then fell through to the
 * OS's own "Tag" viewer (with its screen and tone) even with Ctrus in the foreground. [startScan]
 * / [stopScan] now only register/clear which screen's callback should react to the next tag —
 * reader mode itself stays on the whole time the app is in front, and an unexpected tap (no scan
 * currently pending) is simply swallowed rather than falling through to the system.
 */
class NfcScanController(private val activity: Activity, private val lifecycleScope: LifecycleCoroutineScope) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private var currentHandler: ((String) -> Unit)? = null

    val availability: NfcAvailability
        get() = when {
            adapter == null -> NfcAvailability.NO_HARDWARE
            !adapter.isEnabled -> NfcAvailability.DISABLED
            else -> NfcAvailability.AVAILABLE
        }

    /** Call from the Activity's onResume. */
    fun attach() {
        val adapter = adapter ?: return
        adapter.enableReaderMode(
            activity,
            { tag ->
                lifecycleScope.launch {
                    val code = NfcTagDecoder.decode(tag)
                    withContext(Dispatchers.Main) { currentHandler?.invoke(code) }
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

    /** Call from the Activity's onPause. */
    fun detach() {
        adapter?.disableReaderMode(activity)
    }

    fun startScan(onTag: (String) -> Unit) {
        currentHandler = onTag
    }

    fun stopScan() {
        currentHandler = null
    }
}
