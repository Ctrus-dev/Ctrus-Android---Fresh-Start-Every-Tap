package mo.dev.ctrus.nfc

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
 *
 * Reader mode requested while NFC is off doesn't take effect once NFC is turned on (e.g. from the
 * Quick Settings tile, which doesn't pause the Activity), so a tag tapped right after enabling NFC
 * — typically the first scan — fell through to the system's tag screen. [attach] therefore also
 * listens for the adapter turning on and claims reader mode again, and [reassert] lets the
 * Activity re-claim it whenever its window regains focus.
 */
class NfcScanController(private val activity: Activity, private val lifecycleScope: LifecycleCoroutineScope) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private var currentHandler: ((String) -> Unit)? = null
    private var attached = false

    private val adapterStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF)
            if (state == NfcAdapter.STATE_ON) reassert()
        }
    }

    val availability: NfcAvailability
        get() = when {
            adapter == null -> NfcAvailability.NO_HARDWARE
            !adapter.isEnabled -> NfcAvailability.DISABLED
            else -> NfcAvailability.AVAILABLE
        }

    /** Call from the Activity's onResume. */
    fun attach() {
        if (adapter == null) return
        attached = true
        ContextCompat.registerReceiver(
            activity,
            adapterStateReceiver,
            IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        enableReaderMode()
    }

    /** Re-claims reader mode while attached; call when the Activity's window regains focus. */
    fun reassert() {
        if (attached) enableReaderMode()
    }

    private fun enableReaderMode() {
        val adapter = adapter ?: return
        if (!adapter.isEnabled) return
        adapter.enableReaderMode(
            activity,
            // The tag is read here, on the reader-mode binder thread, before the callback returns.
            // Reading it afterwards from a coroutine raced the NFC stack's own presence check,
            // which on some phones dropped and re-discovered the tag; that second discovery could
            // reach the system's "tag read" screen even though Ctrus had already used the tag.
            { tag ->
                val code = NfcTagDecoder.decodeBlocking(tag)
                Log.d(TAG, "Tag read in reader mode (handler set: ${currentHandler != null})")
                lifecycleScope.launch(Dispatchers.Main) { currentHandler?.invoke(code) }
            },
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                // iOS's NFCTagReaderSession is silent too — reader mode plays the system
                // "tag discovered" tone by default, which this suppresses.
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            // Checks less often whether the tag is still there (default 125 ms), so a tag held
            // against the phone is less likely to be dropped and re-discovered.
            Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, PRESENCE_CHECK_DELAY_MILLIS) },
        )
        Log.d(TAG, "Reader mode enabled")
    }

    /** Call from the Activity's onPause. */
    fun detach() {
        if (!attached) return
        attached = false
        runCatching { activity.unregisterReceiver(adapterStateReceiver) }
        adapter?.disableReaderMode(activity)
        Log.d(TAG, "Reader mode disabled")
    }

    fun startScan(onTag: (String) -> Unit) {
        currentHandler = onTag
    }

    fun stopScan() {
        currentHandler = null
    }

    private companion object {
        const val TAG = "CtrusNfc"
        const val PRESENCE_CHECK_DELAY_MILLIS = 1_000
    }
}
