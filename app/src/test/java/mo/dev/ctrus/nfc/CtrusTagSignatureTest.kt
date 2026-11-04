package mo.dev.ctrus.nfc

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The maker's private key isn't here, so this signs with a throwaway P-256 key and hands the
 * signature over in CryptoKit's raw r ‖ s form, the way a Ctrus carries it.
 */
class CtrusTagSignatureTest {
    private val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val uid = byteArrayOf(0x04, 0x1A, 0x2B, 0x3C, 0x4D, 0x5E, 0x6F)

    private fun rawSignature(uid: ByteArray): ByteArray {
        val der = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(CtrusTagSignature.message(uid))
            sign()
        }
        // DER SEQUENCE { INTEGER r, INTEGER s } → 32-byte r ‖ 32-byte s.
        var i = 2
        fun next(): ByteArray {
            val length = der[i + 1].toInt()
            val value = der.copyOfRange(i + 2, i + 2 + length)
            i += 2 + length
            val unsigned = BigInteger(1, value).toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
            return ByteArray(32 - unsigned.size) + unsigned
        }
        return next() + next()
    }

    @Test
    fun acceptsTheSignatureOfItsOwnUid() {
        repeat(20) { // r or s with a high bit set needs the extra DER sign byte; this hits both
            assertTrue(CtrusTagSignature.verify(uid, rawSignature(uid), keyPair.public))
        }
    }

    @Test
    fun turnsAwayASignatureCopiedFromAnotherTag() {
        val other = uid.copyOf().also { it[6] = 0x70 }
        assertFalse(CtrusTagSignature.verify(other, rawSignature(uid), keyPair.public))
    }

    @Test
    fun turnsAwayMalformedSignatures() {
        assertFalse(CtrusTagSignature.verify(uid, ByteArray(0), keyPair.public))
        assertFalse(CtrusTagSignature.verify(uid, ByteArray(64), keyPair.public))
        assertFalse(CtrusTagSignature.verify(uid, rawSignature(uid).copyOf(63), keyPair.public))
    }

    @Test
    fun turnsAwayAnyoneElsesKey() {
        // Signed by the throwaway key, checked against the real Ctrus key (which also parses).
        assertFalse(CtrusTagSignature.verify(uid, rawSignature(uid)))
    }
}
