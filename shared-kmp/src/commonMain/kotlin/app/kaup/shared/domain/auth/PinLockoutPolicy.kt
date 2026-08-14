package app.kaup.shared.domain.auth

/**
 * Escalating backoff for failed PIN entry.
 *
 * A 6 digit PIN is a million combinations, which a script driving the UI would
 * exhaust in hours. Rate limiting is what makes a short numeric credential
 * defensible, so the policy lives here in shared code where it can be tested,
 * rather than inline in a ViewModel.
 *
 * The arithmetic is [EscalatingBackoff]; this object only fixes the constants
 * and keeps the call sites unchanged.
 */
object PinLockoutPolicy {

    /** Failures allowed before the first lockout. */
    const val FREE_ATTEMPTS: Int = 5

    /** Backoff applied at the first lockout. */
    const val FIRST_BACKOFF_MILLIS: Long = 30_000L

    /** Backoff never grows past this, so a store is never bricked by mistyping. */
    const val MAX_BACKOFF_MILLIS: Long = 15 * 60 * 1000L

    private val backoff = EscalatingBackoff(
        freeAttempts = FREE_ATTEMPTS,
        firstBackoffMillis = FIRST_BACKOFF_MILLIS,
        maxBackoffMillis = MAX_BACKOFF_MILLIS
    )

    /**
     * Backoff owed after [failedAttempts] consecutive failures. Zero while the
     * user is still inside [FREE_ATTEMPTS], then doubling: 30s, 1m, 2m, 4m, 8m,
     * capped at [MAX_BACKOFF_MILLIS].
     */
    fun backoffMillis(failedAttempts: Int): Long = backoff.backoffMillis(failedAttempts)

    /** The uptime at which the lockout ends, given the failure count. */
    fun lockoutUntil(nowUptimeMillis: Long, failedAttempts: Int): Long =
        backoff.lockoutUntil(nowUptimeMillis, failedAttempts)

    /** Milliseconds still to wait, or zero when entry is allowed. */
    fun remainingLockoutMillis(nowUptimeMillis: Long, lockoutUntilUptimeMillis: Long): Long =
        backoff.remainingLockoutMillis(nowUptimeMillis, lockoutUntilUptimeMillis)

    fun isLockedOut(nowUptimeMillis: Long, lockoutUntilUptimeMillis: Long): Boolean =
        backoff.isLockedOut(nowUptimeMillis, lockoutUntilUptimeMillis)

    /**
     * Reconciles a stored deadline with the current uptime after a reboot.
     * See [EscalatingBackoff.resolveAfterReboot].
     */
    fun resolveLockoutAfterReboot(
        nowUptimeMillis: Long,
        storedLockoutUntilUptimeMillis: Long,
        failedAttempts: Int
    ): Long = backoff.resolveAfterReboot(
        nowUptimeMillis,
        storedLockoutUntilUptimeMillis,
        failedAttempts
    )
}
