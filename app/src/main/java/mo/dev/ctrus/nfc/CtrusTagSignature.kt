package mo.dev.ctrus.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import java.util.Base64
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec

/**
 * Tells a real Ctrus from any other NFC tag (port of iOS's CtrusTagSignature.swift). Each Ctrus
 * carries a P-256 ECDSA (SHA-256) signature of `"ctrus-tag-v1:" + its factory UID`, made with a
 * key only the maker has; copying it onto another tag doesn't work, as that tag's UID differs.
 * Only checking is here: writing tags is a developer-only tool kept out of this repo.
 *
 * The signature sits in an NDEF external-type record `ctrus.pt:sig`, as CryptoKit's raw
 * representation (r ‖ s, 32 bytes each); the public key is CryptoKit's raw form too (x ‖ y).
 */
object CtrusTagSignature {
    private const val RECORD_TYPE = "ctrus.pt:sig"
    private const val PUBLIC_KEY_BASE64 =
        "Z2dxTxUA+sYV9MsI8Lidphw0CK8XOY6MG4WpnlGu+YhczCW/wi5m2JwAlEOHQ3zq3HGCI8ydoqyjhApDxskoBA=="

    private val publicKey: PublicKey by lazy {
        val raw = Base64.getDecoder().decode(PUBLIC_KEY_BASE64)
        val params = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }
            .getParameterSpec(ECParameterSpec::class.java)
        val point = ECPoint(BigInteger(1, raw.copyOfRange(0, 32)), BigInteger(1, raw.copyOfRange(32, 64)))
        KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, params))
    }

    fun message(uid: ByteArray): ByteArray = "ctrus-tag-v1:".toByteArray(Charsets.UTF_8) + uid

    fun isGenuine(uid: ByteArray, message: NdefMessage?): Boolean {
        val record = message?.records?.firstOrNull(::isSignatureRecord) ?: return false
        return verify(uid, record.payload)
    }

    internal fun verify(uid: ByteArray, rawSignature: ByteArray, key: PublicKey = publicKey): Boolean {
        val der = rawToDer(rawSignature) ?: return false
        return runCatching {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(key)
                update(message(uid))
                verify(der)
            }
        }.getOrDefault(false)
    }

    private fun isSignatureRecord(record: NdefRecord): Boolean =
        record.tnf == NdefRecord.TNF_EXTERNAL_TYPE && record.type.contentEquals(RECORD_TYPE.toByteArray(Charsets.US_ASCII))

    /** CryptoKit's raw r ‖ s to the DER SEQUENCE { INTEGER r, INTEGER s } Java expects. */
    private fun rawToDer(raw: ByteArray): ByteArray? {
        if (raw.size != 64) return null
        fun integer(bytes: ByteArray): ByteArray {
            val value = BigInteger(1, bytes).toByteArray() // minimal two's complement, sign byte if needed
            return byteArrayOf(0x02, value.size.toByte()) + value
        }
        val body = integer(raw.copyOfRange(0, 32)) + integer(raw.copyOfRange(32, 64))
        return byteArrayOf(0x30, body.size.toByte()) + body
    }
}
