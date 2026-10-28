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
/**
 * One tag read. [uid] is the tag's identifier as lowercase hex with no separators — the format
 * both apps compare child mode's parents' and child's Ctrus in (by UID only, since a URL can be
 * copied onto any tag). [code] is what normal mode stores and compares: the ctrus.pt URL if the
 * tag has one, else the UID as uppercase hex.
 */
data class NfcTagRead(val uid: String, val url: String?) {
    val code: String get() = url ?: uid.uppercase()
}

object NfcTagDecoder {
    suspend fun decode(tag: Tag): NfcTagRead = withContext(Dispatchers.IO) { decodeBlocking(tag) }

    /** Blocking read; call off the main thread (e.g. from a reader-mode callback). */
    fun decodeBlocking(tag: Tag): NfcTagRead =
        NfcTagRead(uid = tag.id.toHexString().lowercase(), url = readNdefMessage(tag)?.let(::extractCtrusUrl))

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
