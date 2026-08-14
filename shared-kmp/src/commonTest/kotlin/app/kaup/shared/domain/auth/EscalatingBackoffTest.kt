package app.kaup.shared.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EscalatingBackoffTest {

    private val backoff = EscalatingBackoff(
        freeAttempts = 3,
        firstBackoffMillis = 1_000L,
        maxBackoffMillis = 8_000L
    )

    @Test
    fun `free attempts owe nothing`() {
        for (attempts in 0..3) {
            assertEquals(0L, backoff.backoffMillis(attempts), "at $attempts attempts")
        }
    }

    @Test
    fun `backoff doubles from the first failure past the free allowance`() {
        assertEquals(1_000L, backoff.backoffMillis(4))
        assertEquals(2_000L, backoff.backoffMillis(5))
        assertEquals(4_000L, backoff.backoffMillis(6))
        assertEquals(8_000L, backoff.backoffMillis(7))
    }

    @Test
    fun `backoff saturates at the ceiling and never overflows`() {
        assertEquals(8_000L, backoff.backoffMillis(8))
        assertEquals(8_000L, backoff.backoffMillis(100))
        assertEquals(8_000L, backoff.backoffMillis(Int.MAX_VALUE))
    }

    @Test
    fun `lockoutUntil is zero while inside the free allowance`() {
        // Zero means "no deadline", not "a deadline at uptime zero", so it must
        // not become now + 0 and read as a lockout that just expired.
        assertEquals(0L, backoff.lockoutUntil(nowUptimeMillis = 50_000L, failedAttempts = 3))
        assertEquals(51_000L, backoff.lockoutUntil(nowUptimeMillis = 50_000L, failedAttempts = 4))
    }

    @Test
    fun `remaining time counts down and clamps at zero`() {
        assertEquals(400L, backoff.remainingLockoutMillis(600L, 1_000L))
        assertEquals(0L, backoff.remainingLockoutMillis(1_000L, 1_000L))
        assertEquals(0L, backoff.remainingLockoutMillis(9_000L, 1_000L))
        assertTrue(backoff.isLockedOut(600L, 1_000L))
        assertFalse(backoff.isLockedOut(1_000L, 1_000L))
    }

    @Test
    fun `a reboot neither clears a lockout nor extends it to the old uptime`() {
        // Stored deadline is plausible relative to the current uptime, so it
        // survives untouched.
        assertEquals(9_000L, backoff.resolveAfterReboot(5_000L, 9_000L, 4))

        // Stored deadline is further out than any backoff this policy can
        // produce, which only happens because uptime restarted. It is recomputed
        // from now, so the lockout is still served but is not multiplied by the
        // previous uptime.
        assertEquals(1_100L, backoff.resolveAfterReboot(100L, 4_000_000L, 4))
    }

    @Test
    fun `rejects nonsensical configuration`() {
        assertFailsWith<IllegalArgumentException> { EscalatingBackoff(-1, 1_000L, 8_000L) }
        assertFailsWith<IllegalArgumentException> { EscalatingBackoff(3, 0L, 8_000L) }
        assertFailsWith<IllegalArgumentException> { EscalatingBackoff(3, 8_000L, 1_000L) }
    }
}
