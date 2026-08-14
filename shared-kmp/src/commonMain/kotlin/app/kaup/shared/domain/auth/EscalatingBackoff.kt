package app.kaup.shared.domain.auth

/**
 * Escalating lockout shared by every credential the app rate limits.
 *
 * PIN entry and manager override entry want the same arithmetic with different
 * constants, and the two copies had already started to drift, so the rule lives
 * here once and each policy supplies its own numbers.
 *
 * Timing is expressed in uptime (Android's `SystemClock.elapsedRealtime`) and
 * never in wall clock, so that changing the device clock cannot shorten a
 * lockout. Uptime resets on reboot, which [resolveAfterReboot] handles.
 *
 * @param freeAttempts failures allowed before the first lockout.
 * @param firstBackoffMillis backoff applied at the first lockout.
 * @param maxBackoffMillis ceiling, so a store is never bricked by mistyping.
 */
class EscalatingBackoff(
    val freeAttempts: Int,
    val firstBackoffMillis: Long,
    val maxBackoffMillis: Long
) {
    init {
        require(freeAttempts >= 0) { "freeAttempts must be non-negative" }
        require(firstBackoffMillis > 0) { "firstBackoffMillis must be positive" }
        require(maxBackoffMillis >= firstBackoffMillis) {
            "maxBackoffMillis must not be below firstBackoffMillis"
        }
    }

    /**
     * Backoff owed after [failedAttempts] consecutive failures: zero while
     * still inside [freeAttempts], then doubling from [firstBackoffMillis],
     * capped at [maxBackoffMillis].
     */
    fun backoffMillis(failedAttempts: Int): Long {
        val over = failedAttempts - freeAttempts
        if (over <= 0) return 0L
        var backoff = firstBackoffMillis
        // The repeat bound stops the doubling from overflowing on an absurd
        // attempt count; the cap check below is what normally ends it.
        repeat(minOf(over - 1, 20)) {
            backoff *= 2
            if (backoff >= maxBackoffMillis) return maxBackoffMillis
        }
        return minOf(backoff, maxBackoffMillis)
    }

    /** The uptime at which the lockout ends, given the failure count. */
    fun lockoutUntil(nowUptimeMillis: Long, failedAttempts: Int): Long {
        val backoff = backoffMillis(failedAttempts)
        return if (backoff == 0L) 0L else nowUptimeMillis + backoff
    }

    /** Milliseconds still to wait, or zero when entry is allowed. */
    fun remainingLockoutMillis(nowUptimeMillis: Long, lockoutUntilUptimeMillis: Long): Long =
        (lockoutUntilUptimeMillis - nowUptimeMillis).coerceAtLeast(0L)

    fun isLockedOut(nowUptimeMillis: Long, lockoutUntilUptimeMillis: Long): Boolean =
        remainingLockoutMillis(nowUptimeMillis, lockoutUntilUptimeMillis) > 0L

    /**
     * Reconciles a stored deadline with the current uptime after a reboot.
     *
     * Uptime restarts at zero when the device does, so a stored deadline can
     * sit arbitrarily far in the "future" purely because the device has been
     * restarted. Rebooting must not clear a lockout, and it must not extend one
     * to the length of the previous uptime either. A deadline further away than
     * the largest backoff this policy can produce is therefore treated as stale
     * and recomputed from now, using the persisted failure count.
     */
    fun resolveAfterReboot(
        nowUptimeMillis: Long,
        storedLockoutUntilUptimeMillis: Long,
        failedAttempts: Int
    ): Long {
        val remaining = storedLockoutUntilUptimeMillis - nowUptimeMillis
        if (remaining <= maxBackoffMillis) return storedLockoutUntilUptimeMillis
        return lockoutUntil(nowUptimeMillis, failedAttempts)
    }
}
