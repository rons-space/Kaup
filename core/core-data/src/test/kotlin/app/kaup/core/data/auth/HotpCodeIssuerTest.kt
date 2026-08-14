package app.kaup.core.data.auth

import app.kaup.core.data.KaupDatabase
import app.kaup.core.data.crypto.HotpSecretUnrecoverableException
import app.kaup.core.data.testing.FakeSecretSealer
import app.kaup.core.data.testing.TEST_SECRET
import app.kaup.core.data.testing.inMemoryDatabase
import app.kaup.core.data.testing.insertUser
import app.kaup.shared.domain.HOTPGenerator
import app.kaup.shared.domain.models.auth.Role
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the counter ownership that PR #273 moved into `HotpCodeIssuer`.
 *
 * The bug being guarded against is specific and was live: the ViewModel used to
 * read the counter from `SessionManager.currentUser`, a snapshot taken at login
 * and never refreshed, so every code generated during a session came from the
 * same counter and was therefore the same code. A one-time password that is not
 * one-time is just a password.
 */
@RunWith(RobolectricTestRunner::class)
class HotpCodeIssuerTest {

    private lateinit var database: KaupDatabase
    private lateinit var sealer: FakeSecretSealer
    private lateinit var issuer: HotpCodeIssuer

    private val sealedSecret get() = FakeSecretSealer().encrypt(TEST_SECRET)

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        sealer = FakeSecretSealer()
        issuer = HotpCodeIssuer(database, database.userDao(), sealer)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `issues the code the counter points at and moves the row past it`() = runTest {
        database.insertUser(MANAGER, Role.MANAGER, sealedSecret, counter = 0L)

        val result = issuer.issue(MANAGER)

        assertTrue("expected Issued, got $result", result is HotpCodeResult.Issued)
        result as HotpCodeResult.Issued
        assertEquals(0L, result.counter)
        assertEquals(HOTPGenerator.generateCode(TEST_SECRET, 0L), result.code)
        assertEquals(
            "the counter must be spent, not just read",
            1L,
            database.userDao().getHotpCounter(MANAGER)
        )
    }

    @Test
    fun `two codes from one session are different codes`() = runTest {
        database.insertUser(MANAGER, Role.MANAGER, sealedSecret, counter = 0L)

        val first = issuer.issue(MANAGER) as HotpCodeResult.Issued
        val second = issuer.issue(MANAGER) as HotpCodeResult.Issued

        assertEquals(0L, first.counter)
        assertEquals(1L, second.counter)
        assertNotEquals(
            "this is the regression that made the OTP reusable within a session",
            first.code,
            second.code
        )
    }

    @Test
    fun `concurrent callers never share a counter`() = runTest {
        database.insertUser(MANAGER, Role.MANAGER, sealedSecret, counter = 0L)

        val results = (1..CONCURRENT_CALLERS)
            .map { async { issuer.issue(MANAGER) } }
            .awaitAll()

        // Not every caller has to win. Losing the compare-and-set is a
        // legitimate outcome the issuer reports as Unavailable, and asserting
        // that all of them succeed would be asserting on scheduling. What must
        // never happen is two callers being handed the same counter, because
        // that hands out the same code twice.
        val issuedCounters = results.filterIsInstance<HotpCodeResult.Issued>().map { it.counter }
        assertEquals(
            "counters handed out concurrently must be unique: $issuedCounters",
            issuedCounters.size,
            issuedCounters.distinct().size
        )
        assertEquals(
            "the row must have advanced exactly once per issued code",
            issuedCounters.size.toLong(),
            database.userDao().getHotpCounter(MANAGER)
        )
    }

    @Test
    fun `a manager with no secret is not provisioned`() = runTest {
        database.insertUser(MANAGER, Role.MANAGER, sealedSecret = null)

        assertEquals(HotpCodeResult.NotProvisioned, issuer.issue(MANAGER))
    }

    @Test
    fun `an unreadable secret is reported, not thrown`() = runTest {
        database.insertUser(MANAGER, Role.MANAGER, sealedSecret, counter = 4L)
        sealer.failure = HotpSecretUnrecoverableException("key invalidated")

        val result = issuer.issue(MANAGER)

        assertTrue("expected Unavailable, got $result", result is HotpCodeResult.Unavailable)
        assertEquals(
            "a secret that cannot be read must not burn a counter",
            4L,
            database.userDao().getHotpCounter(MANAGER)
        )
    }

    @Test
    fun `an unknown user is unavailable`() = runTest {
        val result = issuer.issue("nobody")

        assertTrue("expected Unavailable, got $result", result is HotpCodeResult.Unavailable)
    }

    private companion object {
        const val MANAGER = "manager-1"
        const val CONCURRENT_CALLERS = 4
    }
}
