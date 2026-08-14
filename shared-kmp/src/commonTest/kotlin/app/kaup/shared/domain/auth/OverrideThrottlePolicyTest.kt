package app.kaup.shared.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverrideThrottlePolicyTest {

    @Test
    fun `three attempts are free and the fourth costs a minute`() {
        // RFC 4226 section 7.3 requires a throttle; these are the numbers
        // ADR-021 settled on.
        assertEquals(0L, OverrideThrottlePolicy.backoffMillis(0))
        assertEquals(0L, OverrideThrottlePolicy.backoffMillis(3))
        assertEquals(60_000L, OverrideThrottlePolicy.backoffMillis(4))
    }

    @Test
    fun `backoff doubles to a half hour ceiling`() {
        assertEquals(120_000L, OverrideThrottlePolicy.backoffMillis(5))
        assertEquals(240_000L, OverrideThrottlePolicy.backoffMillis(6))
        assertEquals(480_000L, OverrideThrottlePolicy.backoffMillis(7))
        assertEquals(960_000L, OverrideThrottlePolicy.backoffMillis(8))
        assertEquals(OverrideThrottlePolicy.MAX_BACKOFF_MILLIS, OverrideThrottlePolicy.backoffMillis(9))
        assertEquals(OverrideThrottlePolicy.MAX_BACKOFF_MILLIS, OverrideThrottlePolicy.backoffMillis(500))
    }

    @Test
    fun `guessing the code space costs years rather than an afternoon`() {
        // The point of the throttle. With a look-ahead window of 10 a blind
        // guess lands with probability 11 in 10^6, so an attacker needs roughly
        // 63,000 attempts for even odds. Served at the ceiling that is about
        // three and a half years of lockouts, against a few hours unthrottled.
        val attemptsForEvenOdds = 63_000L
        val chargedAttempts = attemptsForEvenOdds - OverrideThrottlePolicy.FREE_ATTEMPTS
        val millis = chargedAttempts * OverrideThrottlePolicy.MAX_BACKOFF_MILLIS
        val years = millis / (365L * 24 * 60 * 60 * 1000)
        assertEquals(3L, years, "even-odds guessing should cost years of lockouts")
    }

    @Test
    fun `lockout helpers agree with the backoff schedule`() {
        assertEquals(0L, OverrideThrottlePolicy.lockoutUntil(5_000L, 3))
        assertEquals(65_000L, OverrideThrottlePolicy.lockoutUntil(5_000L, 4))
        assertTrue(OverrideThrottlePolicy.isLockedOut(5_000L, 65_000L))
        assertFalse(OverrideThrottlePolicy.isLockedOut(65_000L, 65_000L))
        assertEquals(60_000L, OverrideThrottlePolicy.remainingLockoutMillis(5_000L, 65_000L))
    }

    @Test
    fun `a reboot does not clear an override lockout`() {
        // Deadline recorded before a reboot sits impossibly far ahead of the
        // restarted uptime clock, so it is recomputed rather than trusted or
        // discarded.
        val resolved = OverrideThrottlePolicy.resolveLockoutAfterReboot(
            nowUptimeMillis = 200L,
            storedLockoutUntilUptimeMillis = 40_000_000L,
            failedAttempts = 4
        )
        assertEquals(60_200L, resolved)
        assertTrue(OverrideThrottlePolicy.isLockedOut(200L, resolved))
    }

    @Test
    fun `override throttling is never gentler than pin lockout`() {
        // The two policies are easy to edit independently, and an override
        // allowance looser than the PIN allowance would be a regression worth
        // failing a build over.
        for (attempts in 0..12) {
            assertTrue(
                OverrideThrottlePolicy.backoffMillis(attempts) >= PinLockoutPolicy.backoffMillis(attempts),
                "override backoff must not be gentler than pin backoff at $attempts attempts"
            )
        }
        assertTrue(OverrideThrottlePolicy.FREE_ATTEMPTS < PinLockoutPolicy.FREE_ATTEMPTS)
    }
}
