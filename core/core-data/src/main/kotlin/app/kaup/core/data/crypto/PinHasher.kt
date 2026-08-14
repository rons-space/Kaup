package app.kaup.core.data.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Derives and verifies PIN hashes.
 *
 * The PIN used to be written to the database verbatim in a column called
 * `pinHash` and compared with `==` inside a Composable, so anyone with the
 * database file had every staff credential in plaintext.
 *
 * PBKDF2 is used rather than a memory-hard function because it is in the
 * platform (no dependency, and nothing that would trouble an F-Droid source
 * review) and because the work factor is tunable. The iteration count is stored
 * per credential so it can be raised later without invalidating existing PINs.
 *
 * A 6 digit PIN has only a million possible values, so the hash is a defence
 * against database theft, not against an attacker who can drive the lock screen.
 * That is what the lockout in `PinLockoutPolicy` is for.
 */
@Singleton
class PinHasher @Inject constructor() {

    fun newSalt(): String = ByteArray(SALT_BYTES)
        .also { SecureRandom().nextBytes(it) }
        .toHex()

    fun hash(pin: String, saltHex: String, iterations: Int = DEFAULT_ITERATIONS): String {
        val spec = PBEKeySpec(pin.toCharArray(), saltHex.fromHex(), iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded.toHex()
        } finally {
            // PBEKeySpec copies the char array; clearing it stops the PIN from
            // lingering in the heap for the lifetime of the process.
            spec.clearPassword()
        }
    }

    /**
     * Constant-time comparison. A short-circuiting `==` on the hex string leaks
     * how many leading characters matched, which is enough to reconstruct the
     * hash one character at a time given enough attempts.
     */
    fun verify(pin: String, expectedHashHex: String, saltHex: String, iterations: Int): Boolean {
        val actual = hash(pin, saltHex, iterations)
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.US_ASCII),
            expectedHashHex.toByteArray(Charsets.US_ASCII)
        )
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(byte) }

    private fun String.fromHex(): ByteArray =
        ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    companion object {
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256

        /**
         * OWASP's 2023 floor for PBKDF2-HMAC-SHA256. Raising this later is safe:
         * existing rows keep the count they were written with.
         */
        const val DEFAULT_ITERATIONS: Int = 210_000
    }
}
