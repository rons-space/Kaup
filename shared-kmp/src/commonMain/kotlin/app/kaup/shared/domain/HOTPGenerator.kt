package app.kaup.shared.domain

import app.kaup.shared.domain.crypto.CryptoUtils

/**
 * RFC 4226 HOTP, used for the offline manager override described in ADR-005.
 *
 * Validation here is deliberately dumb: it says whether a code matches, and at
 * which counter. It does not persist anything and it does not decide whether
 * the holder is allowed to do anything. Advancing the counter and writing the
 * audit row belong to the caller, in one transaction, because only the caller
 * can make those atomic. See ADR-021.
 */
object HOTPGenerator {

    /**
     * ADR-005 specifies 10. The window exists because a manager can generate
     * codes that are never entered, which pushes their counter ahead of the
     * validating device's copy.
     */
    const val DEFAULT_LOOK_AHEAD_WINDOW: Int = 10

    /** RFC 4226 section 5.3 defines truncation for 6 to 8 digits. */
    private val POWERS_OF_TEN = intArrayOf(1_000_000, 10_000_000, 100_000_000)

    /**
     * Generates an RFC 4226 HOTP code.
     *
     * The modulus is taken from an integer table rather than `10.0.pow(digits)`
     * because the floating point form silently loses precision above eight
     * digits and hides the fact that only 6 to 8 are actually defined.
     */
    fun generateCode(secret: ByteArray, counter: Long, digits: Int = 6): String {
        require(digits in 6..8) { "RFC 4226 defines 6 to 8 digit codes, got $digits" }
        require(counter >= 0) { "Counter must be non-negative, got $counter" }
        require(secret.isNotEmpty()) { "Secret must not be empty" }

        val hmac = CryptoUtils.hmacSha1(secret, counterToByteArray(counter))

        // Dynamic truncation (RFC 4226 section 5.3).
        val offset = hmac.last().toInt() and 0x0F
        val binary = ((hmac[offset].toInt() and 0x7F) shl 24) or
            ((hmac[offset + 1].toInt() and 0xFF) shl 16) or
            ((hmac[offset + 2].toInt() and 0xFF) shl 8) or
            (hmac[offset + 3].toInt() and 0xFF)

        val otp = binary % POWERS_OF_TEN[digits - 6]
        return otp.toString().padStart(digits, '0')
    }

    /**
     * Validates [inputCode] against counters `currentCounter` through
     * `currentCounter + lookAheadWindow`, and returns the counter that produced
     * it, or null.
     *
     * Two properties matter here and both cost something in readability:
     *
     * The comparison is constant time, so a caller cannot learn how many
     * leading digits of a guess were correct by measuring how long the
     * rejection took. That alone would turn a 10^6 search into 10 * 6.
     *
     * The loop does not stop at the first match. Returning early would leak
     * which offset matched through the response time, which tells an attacker
     * how far the manager's counter has drifted, and drift is exactly what
     * narrows a guess. Every call therefore does `lookAheadWindow + 1` HMACs.
     *
     * The returned value is the counter that matched, not the next one. The
     * caller persists `matched + 1`, which is what consumes the code.
     */
    fun validateCode(
        secret: ByteArray,
        currentCounter: Long,
        inputCode: String,
        lookAheadWindow: Int = DEFAULT_LOOK_AHEAD_WINDOW,
        digits: Int = 6
    ): Long? {
        require(lookAheadWindow >= 0) { "Look-ahead window must be non-negative" }
        require(currentCounter >= 0) { "Counter must be non-negative, got $currentCounter" }

        // -1 is a safe "no match" sentinel because counters are non-negative.
        var matched = -1L
        for (offset in 0..lookAheadWindow) {
            val target = currentCounter + offset
            val equal = constantTimeEqualsBit(generateCode(secret, target, digits), inputCode)
            // Select arithmetically rather than with an `if`, so that a match
            // and a miss execute the same instructions.
            val mask = -equal.toLong()
            matched = (matched and mask.inv()) or (target and mask)
        }
        return if (matched >= 0) matched else null
    }

    /**
     * Returns 1 when the strings are equal and 0 when they are not, in time
     * that depends only on the length.
     *
     * Length is not secret: an HOTP code is a fixed number of digits and the
     * digit count is a build-time constant, so returning early on a length
     * mismatch reveals nothing an attacker does not already know.
     */
    private fun constantTimeEqualsBit(a: String, b: String): Int {
        if (a.length != b.length) return 0
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].code xor b[i].code)
        }
        // diff is non-negative, so `diff or -diff` has its sign bit set exactly
        // when diff is non-zero. Shifting it down gives 1 for "different", and
        // the xor flips that into 1 for "equal".
        return ((diff or -diff) ushr 31) xor 1
    }

    private fun counterToByteArray(counter: Long): ByteArray {
        val result = ByteArray(8)
        for (i in 7 downTo 0) {
            result[i] = (counter ushr (8 * (7 - i))).toByte()
        }
        return result
    }
}
