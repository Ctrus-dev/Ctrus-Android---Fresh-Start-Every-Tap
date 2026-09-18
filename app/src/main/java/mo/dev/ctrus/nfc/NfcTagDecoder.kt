package mo.dev.ctrus.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Port of the read side of Ctrus/Utils/NFCScannerUtil.swift: prefers a single well-known-type
 * NDEF URI record whose host is `ctrus.pt` (scheme `https`) — the same record iOS tags carry —
 * falling back to the tag's raw UID as uppercase hex when there's no such record (blank/foreign
 * tags, or a tag with no NDEF data at all). Only reading is ported: NFCWriter.swift/
 * NFCScannerUtil.writeURL have no real call site anywhere in the iOS Views/Components — nothing
 * in the shipped app ever writes a tag — so there's no NFC-write feature to port.
 */
object NfcTagDecoder {
    suspend fun decode(tag: Tag): String = withContext(Dispatchers.IO) {
        val message = readNdefMessage(tag)
        message?.let(::extractCtrusUrl) ?: tag.id.toHexString()
    }

    private fun readNdefMessage(tag: Tag): NdefMessage? {
        val ndef = Ndef.get(tag) ?: return null
        return try {
            ndef.connect()
            ndef.cachedNdefMessage ?: ndef.ndefMessage
        } catch (e: Exception) {
            null
        } finally {
            runCatching { ndef.close() }
        }
    }

    /** Mirrors updateWithNDEFMessageURL: exactly one matching ctrus.pt URI record, or none. */
    private fun extractCtrusUrl(message: NdefMessage): String? {
        val matches = message.records.mapNotNull { record ->
            if (record.tnf != NdefRecord.TNF_WELL_KNOWN || !record.type.contentEquals(NdefRecord.RTD_URI)) {
                return@mapNotNull null
            }
            val uri = record.toUri() ?: return@mapNotNull null
            if (uri.scheme == "https" && uri.host == "ctrus.pt") uri.toString() else null
        }
        return matches.singleOrNull()
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02X".format(it) }
}
