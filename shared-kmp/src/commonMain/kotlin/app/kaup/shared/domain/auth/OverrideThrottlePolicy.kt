package app.kaup.shared.domain.auth

/**
 * Attempt throttling for manager override codes, required by RFC 4226
 * section 7.3.
 *
 * Throttling is not optional decoration here. A 6 digit code accepted anywhere
 * in a look-ahead window of 10 gives a blind guess an 11 in 10^6 chance, so an
 * unthrottled attacker tapping the overlay reaches even odds in roughly 63,000
 * tries. The RFC's answer is to make attempts expensive rather than to make the
 * code longer, because the code has to be read aloud.
 *
 * The limits are tighter than [PinLockoutPolicy] because the two credentials
 * fail differently. A cashier mistypes their own PIN routinely, and locking
 * them out of the till is a real cost. An override code is transcribed once
 * from a manager standing nearby, so repeated failure is much more likely to be
 * an attack than a fumble, and the fallback (ask the manager for a new code) is
 * cheap.
 *
 * The counters are persisted against the manager being asked to approve, not
 * the staff session, so closing the overlay or killing the app does not reset
 * them.
 */
object OverrideThrottlePolicy {

    /** Failures allowed before the first lockout. */
    const val FREE_ATTEMPTS: Int = 3

    /** Backoff applied at the first lockout. */
    const val FIRST_BACKOFF_MILLIS: Long = 60_000L

    /**
     * Ceiling on the backoff. A manager can always issue a fresh code, so a
     * long lockout is survivable, but an indefinite one would let a prankster
     * disable approvals for the rest of a shift.
     */
    const val MAX_BACKOFF_MILLIS: Long = 30 * 60 * 1000L

    private val backoff = EscalatingBackoff(
        freeAttempts = FREE_ATTEMPTS,
        firstBackoffMillis = FIRST_BACKOFF_MILLIS,
        maxBackoffMillis = MAX_BACKOFF_MILLIS
    )

    /**
     * Backoff owed after [failedAttempts] consecutive failures. Zero for the
     * first three, then doubling: 1m, 2m, 4m, 8m, 16m, capped at
     * [MAX_BACKOFF_MILLIS].
     */
    fun backoffMillis(failedAttempts: Int): Long = backoff.backoffMillis(failedAttempts)

    /** The uptime at which the lockout ends, given the failure count. */
    fun lockoutUntil(nowUptimeMillis: Long, failedAttempts: Int): Long =
        backoff.lockoutUntil(nowUptimeMillis, failedAttempts)

    /** Milliseconds still to wait, or zero when an attempt is allowed. */
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
