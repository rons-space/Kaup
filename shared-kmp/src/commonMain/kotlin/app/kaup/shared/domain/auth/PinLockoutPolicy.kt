package app.kaup.shared.domain.auth

/**
 * Escalating backoff for failed PIN entry.
 *
 * A 6 digit PIN is a million combinations, which a script driving the UI would
 * exhaust in hours. Rate limiting is what makes a short numeric credential
 * defensible, so the policy lives here in shared code where it can be tested,
 * rather than inline in a ViewModel.
 *
 * Timing uses uptime (Android's `SystemClock.elapsedRealtime`), not wall clock,
 * so that changing the device clock cannot shorten a lockout. Uptime resets on
 * reboot, which is handled explicitly by [resolveLockoutAfterReboot].
 */
object PinLockoutPolicy {

    /** Failures allowed before the first lockout. */
    const val FREE_ATTEMPTS: Int = 5

    /** Backoff applied at the first lockout. */
    const val FIRST_BACKOFF_MILLIS: Long = 30_000L

    /** Backoff never grows past this, so a store is never bricked by mistyping. */
    const val MAX_BACKOFF_MILLIS: Long = 15 * 60 * 1000L

    /**
     * Backoff owed after [failedAttempts] consecutive failures. Zero while the
     * user is still inside [FREE_ATTEMPTS], then doubling: 30s, 1m, 2m, 4m, 8m,
     * capped at [MAX_BACKOFF_MILLIS].
     */
    fun backoffMillis(failedAttempts: Int): Long {
        val over = failedAttempts - FREE_ATTEMPTS
        if (over <= 0) return 0L
        var backoff = FIRST_BACKOFF_MILLIS
        repeat(minOf(over - 1, 20)) {
            backoff *= 2
            if (backoff >= MAX_BACKOFF_MILLIS) return MAX_BACKOFF_MILLIS
        }
        return minOf(backoff, MAX_BACKOFF_MILLIS)
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
     * the largest backoff the policy can produce is therefore treated as stale
     * and recomputed from now, using the persisted failure count.
     */
    fun resolveLockoutAfterReboot(
        nowUptimeMillis: Long,
        storedLockoutUntilUptimeMillis: Long,
        failedAttempts: Int
    ): Long {
        val remaining = storedLockoutUntilUptimeMillis - nowUptimeMillis
        if (remaining <= MAX_BACKOFF_MILLIS) return storedLockoutUntilUptimeMillis
        return lockoutUntil(nowUptimeMillis, failedAttempts)
    }
}
