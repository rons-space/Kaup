package app.kaup.shared.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PinLockoutPolicyTest {

    @Test
    fun `mistyping within the free attempts costs nothing`() {
        for (attempt in 1..PinLockoutPolicy.FREE_ATTEMPTS) {
            assertEquals(0L, PinLockoutPolicy.backoffMillis(attempt), "attempt $attempt")
        }
    }

    @Test
    fun `backoff starts at thirty seconds and doubles`() {
        assertEquals(30_000L, PinLockoutPolicy.backoffMillis(PinLockoutPolicy.FREE_ATTEMPTS + 1))
        assertEquals(60_000L, PinLockoutPolicy.backoffMillis(PinLockoutPolicy.FREE_ATTEMPTS + 2))
        assertEquals(120_000L, PinLockoutPolicy.backoffMillis(PinLockoutPolicy.FREE_ATTEMPTS + 3))
    }

    @Test
    fun `backoff is capped so a store cannot be bricked by fat fingers`() {
        assertEquals(
            PinLockoutPolicy.MAX_BACKOFF_MILLIS,
            PinLockoutPolicy.backoffMillis(PinLockoutPolicy.FREE_ATTEMPTS + 40)
        )
    }

    @Test
    fun `brute force is slowed to a crawl`() {
        // A million combinations at this backoff is not a practical attack path.
        val attemptsToOneMillion = 1_000_000
        assertEquals(
            PinLockoutPolicy.MAX_BACKOFF_MILLIS,
            PinLockoutPolicy.backoffMillis(attemptsToOneMillion)
        )
    }

    @Test
    fun `lockout expires as uptime advances`() {
        val until = PinLockoutPolicy.lockoutUntil(1_000L, PinLockoutPolicy.FREE_ATTEMPTS + 1)
        assertTrue(PinLockoutPolicy.isLockedOut(1_000L, until))
        assertTrue(PinLockoutPolicy.isLockedOut(30_000L, until))
        assertFalse(PinLockoutPolicy.isLockedOut(31_001L, until))
        assertEquals(0L, PinLockoutPolicy.remainingLockoutMillis(31_001L, until))
    }

    @Test
    fun `a reboot does not clear an active lockout`() {
        // Locked out after 8 hours of uptime, then the device is restarted, so
        // uptime is back near zero and the stored deadline looks far away.
        val eightHours = 8 * 60 * 60 * 1000L
        val attempts = PinLockoutPolicy.FREE_ATTEMPTS + 2
        val storedDeadline = PinLockoutPolicy.lockoutUntil(eightHours, attempts)

        val afterReboot = 500L
        val resolved = PinLockoutPolicy.resolveLockoutAfterReboot(
            nowUptimeMillis = afterReboot,
            storedLockoutUntilUptimeMillis = storedDeadline,
            failedAttempts = attempts
        )

        assertTrue(PinLockoutPolicy.isLockedOut(afterReboot, resolved))
        // Rebooting must not extend the lockout to the length of the previous
        // uptime either: it is recomputed from the failure count.
        assertEquals(
            PinLockoutPolicy.backoffMillis(attempts),
            PinLockoutPolicy.remainingLockoutMillis(afterReboot, resolved)
        )
    }

    @Test
    fun `an ordinary deadline is left alone`() {
        val now = 60_000L
        val deadline = now + 20_000L
        assertEquals(
            deadline,
            PinLockoutPolicy.resolveLockoutAfterReboot(now, deadline, PinLockoutPolicy.FREE_ATTEMPTS + 1)
        )
    }
}
