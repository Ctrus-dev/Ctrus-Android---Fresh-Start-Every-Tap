package mo.dev.ctrus.childmode

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import mo.dev.ctrus.util.TrustedClock
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Port of iOS's ParentCredentials: the parent code and the recovery key, kept only as salted
 * PBKDF2-SHA256 hashes (100 000 iterations, 16-byte salt) in EncryptedSharedPreferences. Wrong
 * guesses lock entry for a while; the wait is counted in real time by [TrustedClock], so restarting
 * the phone or moving its date doesn't shorten it.
 *
 * Hashing takes a noticeable moment, so every call here belongs off the main thread.
 */
object ParentCredentials {
    const val CODE_LENGTH = 6
    const val RECOVERY_KEY_LENGTH = 12
    const val RECOVERY_KEY_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    const val ATTEMPTS_BEFORE_LOCK = 5
    /** 1 min, 5 min, 15 min, then 1 h for every lock after that. */
    val LOCK_DURATIONS_SECONDS = listOf(60f, 5 * 60f, 15 * 60f, 60 * 60f)

    private const val ITERATIONS = 100_000
    private const val SALT_LENGTH = 16
    private const val KEY_LENGTH_BITS = 256

    enum class Secret(private val prefix: String) {
        PARENT_CODE("pin"),
        RECOVERY_KEY("recoveryKey");

        // Spec names: parentPinHash/parentPinSalt, recoveryKeyHash/recoveryKeySalt.
        val hashKey get() = if (this == PARENT_CODE) "parentPinHash" else "recoveryKeyHash"
        val saltKey get() = if (this == PARENT_CODE) "parentPinSalt" else "recoveryKeySalt"
        val failedAttemptsKey get() = "${prefix}FailedAttempts"
        val lockLevelKey get() = "${prefix}LockLevel"
        /** Seconds still to wait. */
        val lockRemainingKey get() = "${prefix}LockRemaining"
        val lockClockKey get() = "${prefix}Lock"
    }

    sealed interface VerifyResult {
        data object Valid : VerifyResult
        data object Invalid : VerifyResult
        data class Locked(val remainingSeconds: Float) : VerifyResult
    }

    private lateinit var secrets: SharedPreferences
    private lateinit var counters: SharedPreferences
    private lateinit var clockStore: TrustedClock.Store

    fun init(context: Context) {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        secrets = EncryptedSharedPreferences.create(
            context,
            "mo.dev.ctrus.parent_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        counters = context.getSharedPreferences("mo.dev.ctrus.parent_credentials_attempts", Context.MODE_PRIVATE)
        clockStore = TrustedClock.SharedPreferencesStore(context)
    }

    private fun lockClock(secret: Secret) = TrustedClock(secret.lockClockKey, clockStore)

    // region Setting

    /** All the same digit, or a run going up (123456) or down (654321). */
    fun isWeakCode(code: String): Boolean {
        if (code.length != CODE_LENGTH || !code.all(Char::isDigit)) return true
        val digits = code.map { it - '0' }
        val pairs = digits.zipWithNext()
        return digits.toSet().size == 1 || pairs.all { (a, b) -> b == a + 1 } || pairs.all { (a, b) -> b == a - 1 }
    }

    val hasParentCode: Boolean get() = secrets.contains(Secret.PARENT_CODE.hashKey)

    fun setParentCode(code: String) = store(code, Secret.PARENT_CODE)

    /** A fresh key, shown once as XXXX-XXXX-XXXX; only its hash is kept. */
    fun makeRecoveryKey(): String {
        val random = SecureRandom()
        val key = String(CharArray(RECOVERY_KEY_LENGTH) { RECOVERY_KEY_ALPHABET[random.nextInt(RECOVERY_KEY_ALPHABET.length)] })
        store(key, Secret.RECOVERY_KEY)
        return key
    }

    fun formattedRecoveryKey(key: String): String = key.chunked(4).joinToString("-")

    fun normalizedRecoveryKey(input: String): String = input.uppercase().filter { it in RECOVERY_KEY_ALPHABET }

    // endregion

    // region Checking

    fun verify(input: String, secret: Secret): VerifyResult {
        val remaining = lockRemainingSeconds(secret)
        if (remaining > 0) return VerifyResult.Locked(remaining)

        val candidate = if (secret == Secret.RECOVERY_KEY) normalizedRecoveryKey(input) else input
        val storedHash = secrets.getString(secret.hashKey, null)
        val salt = secrets.getString(secret.saltKey, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        if (storedHash == null || salt == null || encode(hash(candidate, salt)) != storedHash) {
            return registerFailure(secret)
        }
        resetAttempts(secret)
        return VerifyResult.Valid
    }

    /** The parents' Ctrus always works, even during a wait, and clears it. */
    fun resetAttempts(secret: Secret) {
        counters.edit()
            .putInt(secret.failedAttemptsKey, 0)
            .putInt(secret.lockLevelKey, 0)
            .putFloat(secret.lockRemainingKey, 0f)
            .commit()
    }

    @Synchronized
    fun lockRemainingSeconds(secret: Secret): Float {
        val elapsedSeconds = lockClock(secret).trustedElapsedMillis() / 1000f
        val remaining = (counters.getFloat(secret.lockRemainingKey, 0f) - elapsedSeconds).coerceAtLeast(0f)
        counters.edit().putFloat(secret.lockRemainingKey, remaining).commit()
        return remaining
    }

    @Synchronized
    private fun registerFailure(secret: Secret): VerifyResult {
        val failures = counters.getInt(secret.failedAttemptsKey, 0) + 1
        if (failures < ATTEMPTS_BEFORE_LOCK) {
            counters.edit().putInt(secret.failedAttemptsKey, failures).commit()
            return VerifyResult.Invalid
        }

        val level = counters.getInt(secret.lockLevelKey, 0)
        val duration = LOCK_DURATIONS_SECONDS[minOf(level, LOCK_DURATIONS_SECONDS.lastIndex)]
        // Start the lock's clock now so the wait counts from this moment.
        lockClock(secret).trustedElapsedMillis()
        counters.edit()
            .putInt(secret.failedAttemptsKey, 0)
            .putInt(secret.lockLevelKey, level + 1)
            .putFloat(secret.lockRemainingKey, duration)
            .commit()

        if (secret == Secret.PARENT_CODE) ChildModeStore.logTamper(TamperLogEntry.Kind.PIN_LOCKOUT)
        return VerifyResult.Locked(duration)
    }

    // endregion

    fun clearAll() {
        Secret.entries.forEach { secret ->
            secrets.edit().remove(secret.hashKey).remove(secret.saltKey).commit()
            resetAttempts(secret)
        }
    }

    private fun store(value: String, secret: Secret) {
        val salt = ByteArray(SALT_LENGTH).also(SecureRandom()::nextBytes)
        secrets.edit()
            .putString(secret.saltKey, encode(salt))
            .putString(secret.hashKey, encode(hash(value, salt)))
            .commit()
        resetAttempts(secret)
    }

    private fun hash(value: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(value.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
