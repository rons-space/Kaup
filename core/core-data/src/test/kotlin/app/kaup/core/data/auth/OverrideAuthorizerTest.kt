package app.kaup.core.data.auth

import app.kaup.core.data.KaupDatabase
import app.kaup.core.data.testing.FakeSecretSealer
import app.kaup.core.data.testing.FakeTimeProvider
import app.kaup.core.data.testing.TEST_SECRET
import app.kaup.core.data.testing.inMemoryDatabase
import app.kaup.core.data.testing.insertUser
import app.kaup.shared.domain.HOTPGenerator
import app.kaup.shared.domain.auth.OverrideScope
import app.kaup.shared.domain.auth.OverrideThrottlePolicy
import app.kaup.shared.domain.models.auth.Permission
import app.kaup.shared.domain.models.auth.Role
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the manager override enforcement point (ADR-021).
 *
 * `HOTPGenerator` only says whether a code matches, and it already has its own
 * tests. Everything that makes a match *mean* something happens here and had
 * none: the approver's own authority, the throttle, consuming the counter, and
 * writing the audit row in the same transaction as the counter advance.
 */
@RunWith(RobolectricTestRunner::class)
class OverrideAuthorizerTest {

    private lateinit var database: KaupDatabase
    private lateinit var time: FakeTimeProvider
    private lateinit var authorizer: OverrideAuthorizer

    private val sealedSecret get() = FakeSecretSealer().encrypt(TEST_SECRET)

    @Before
    fun setUp() {
        database = inMemoryDatabase()
        time = FakeTimeProvider()
        authorizer = OverrideAuthorizer(
            database = database,
            userDao = database.userDao(),
            overrideLogDao = database.overrideLogDao(),
            secretSealer = FakeSecretSealer(),
            timeProvider = time
        )
        database.insertUser(MANAGER, Role.MANAGER, sealedSecret, counter = 0L)
        database.insertUser(CASHIER, Role.CASHIER)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun codeFor(counter: Long) = HOTPGenerator.generateCode(TEST_SECRET, counter)

    private suspend fun authorize(
        code: String,
        permission: Permission = APPROVABLE,
        scope: OverrideScope = OverrideScope.SPECIFIC_ACTION,
        elevationTokensEnabled: Boolean = true
    ) = authorizer.authorize(
        approverUserId = MANAGER,
        requestedByUserId = CASHIER,
        permission = permission,
        code = code,
        scope = scope,
        elevationTokensEnabled = elevationTokensEnabled
    )

    @Test
    fun `a valid code grants, consumes the counter and leaves an audit row`() = runTest {
        val result = authorize(codeFor(0L))

        assertTrue("expected Granted, got $result", result is OverrideResult.Granted)
        result as OverrideResult.Granted
        assertEquals(0L, result.counterUsed)
        assertNull("a specific-action grant carries no elevation token", result.token)

        assertEquals(
            "the counter must advance past the matched value",
            1L,
            database.userDao().getHotpCounter(MANAGER)
        )
        val logged = database.overrideLogDao().getById(result.logId)
        assertNotNull("the grant must be recorded", logged)
        assertEquals(MANAGER, logged!!.approvedByUserId)
        assertEquals(CASHIER, logged.requestedByUserId)
        assertEquals(APPROVABLE, logged.permission)
        assertEquals(
            "exactly one grant may exist per counter value",
            1,
            database.overrideLogDao().countGrantsAtCounter(MANAGER, 0L)
        )
    }

    @Test
    fun `a drifted code invalidates the codes it skipped`() = runTest {
        // The manager's authenticator has run ahead of the stored counter,
        // which the look-ahead window exists to tolerate. Accepting the drifted
        // code must still kill everything behind it, or the skipped codes stay
        // spendable.
        val result = authorize(codeFor(3L)) as OverrideResult.Granted

        assertEquals(3L, result.counterUsed)
        assertEquals(4L, database.userDao().getHotpCounter(MANAGER))
    }

    @Test
    fun `a wrong code is rejected and counts against the allowance`() = runTest {
        val result = authorize("000000")

        assertTrue("expected Rejected, got $result", result is OverrideResult.Rejected)
        assertEquals(
            OverrideThrottlePolicy.FREE_ATTEMPTS - 1,
            (result as OverrideResult.Rejected).attemptsBeforeLockout
        )
        assertEquals(
            "a failed attempt must not spend the counter",
            0L,
            database.userDao().getHotpCounter(MANAGER)
        )
    }

    @Test
    fun `enough wrong codes lock the approver out`() = runTest {
        repeat(OverrideThrottlePolicy.FREE_ATTEMPTS) { authorize("000000") }

        val locked = authorize("000000")
        assertTrue("expected LockedOut, got $locked", locked is OverrideResult.LockedOut)
        assertTrue((locked as OverrideResult.LockedOut).remainingMillis > 0L)

        // The right code is refused too, and without being consumed. Otherwise
        // the lockout is a speed bump rather than a limit.
        val correct = authorize(codeFor(0L))
        assertTrue("expected LockedOut, got $correct", correct is OverrideResult.LockedOut)
        assertEquals(0L, database.userDao().getHotpCounter(MANAGER))
    }

    @Test
    fun `the lockout lifts once its window has passed`() = runTest {
        repeat(OverrideThrottlePolicy.FREE_ATTEMPTS + 1) { authorize("000000") }
        assertTrue(authorize(codeFor(0L)) is OverrideResult.LockedOut)

        time.advance(OverrideThrottlePolicy.MAX_BACKOFF_MILLIS + 1)

        val result = authorize(codeFor(0L))
        assertTrue("expected Granted after the backoff, got $result", result is OverrideResult.Granted)
    }

    @Test
    fun `an approver without the permission is refused before the code is read`() = runTest {
        // MANAGER deliberately lacks the USERS_* permissions.
        val result = authorize(codeFor(0L), permission = Permission.USERS_EDIT)

        assertEquals(OverrideResult.ApproverNotPermitted(Permission.USERS_EDIT), result)
        assertEquals(
            "a code must never be spent proving something unapprovable",
            0L,
            database.userDao().getHotpCounter(MANAGER)
        )
        assertEquals(
            "and it must not count against the manager's allowance",
            0,
            database.userDao().getUserById(MANAGER)!!.failedOverrideAttempts
        )
    }

    @Test
    fun `an approver with no secret is not provisioned`() = runTest {
        database.insertUser(UNPROVISIONED, Role.MANAGER, sealedSecret = null)

        val result = authorizer.authorize(
            approverUserId = UNPROVISIONED,
            requestedByUserId = CASHIER,
            permission = APPROVABLE,
            code = codeFor(0L)
        )

        assertEquals(OverrideResult.NotProvisioned, result)
    }

    @Test
    fun `elevation tokens are refused when the store has them switched off`() = runTest {
        val result = authorize(
            codeFor(0L),
            scope = OverrideScope.ELEVATION_TOKEN,
            elevationTokensEnabled = false
        )

        assertEquals(OverrideResult.ElevationDisabled, result)
        assertEquals(
            "refusing the scope must not consume the code",
            0L,
            database.userDao().getHotpCounter(MANAGER)
        )
    }

    @Test
    fun `an elevation scope grant carries a token`() = runTest {
        val result = authorize(codeFor(0L), scope = OverrideScope.ELEVATION_TOKEN)

        assertTrue("expected Granted, got $result", result is OverrideResult.Granted)
        val token = (result as OverrideResult.Granted).token
        assertNotNull("ELEVATION_TOKEN scope must issue a token", token)
        assertEquals(MANAGER, token!!.grantedByUserId)
    }

    @Test
    fun `a successful override clears the failure state`() = runTest {
        repeat(2) { authorize("000000") }
        assertEquals(2, database.userDao().getUserById(MANAGER)!!.failedOverrideAttempts)

        authorize(codeFor(0L))

        val manager = database.userDao().getUserById(MANAGER)!!
        assertEquals(0, manager.failedOverrideAttempts)
        assertEquals(0L, manager.overrideLockoutUntilUptimeMillis)
    }

    @Test
    fun `verifyGrant accepts only the grant that was actually issued`() = runTest {
        val granted = authorize(codeFor(0L)) as OverrideResult.Granted

        assertTrue(authorizer.verifyGrant(granted.logId, APPROVABLE, CASHIER))
        assertFalse(
            "a grant for one permission must not be spendable on another",
            authorizer.verifyGrant(granted.logId, Permission.POS_ISSUE_REFUND, CASHIER)
        )
        assertFalse(
            "a grant belongs to the user it was requested for",
            authorizer.verifyGrant(granted.logId, APPROVABLE, "someone-else")
        )
        assertFalse(
            "an id that was never issued must not verify",
            authorizer.verifyGrant("not-a-log-id", APPROVABLE, CASHIER)
        )
    }

    @Test
    fun `a grant expires rather than staying spendable all day`() = runTest {
        val granted = authorize(codeFor(0L)) as OverrideResult.Granted
        assertTrue(authorizer.verifyGrant(granted.logId, APPROVABLE, CASHIER))

        time.advance(GRANT_VALIDITY_MILLIS + 1)

        assertFalse(
            "an approval obtained this morning must not be spendable this afternoon",
            authorizer.verifyGrant(granted.logId, APPROVABLE, CASHIER)
        )
    }

    @Test
    fun `the approver picker only offers managers who can actually approve`() = runTest {
        database.insertUser(PROVISIONED_CASHIER, Role.CASHIER, sealedSecret)

        val approvers = authorizer.approversFor(APPROVABLE).map { it.id }

        assertTrue("the manager can approve $APPROVABLE", MANAGER in approvers)
        assertFalse(
            "a provisioned cashier still cannot approve what they cannot do",
            PROVISIONED_CASHIER in approvers
        )
        assertFalse("an unprovisioned user cannot be offered", CASHIER in approvers)
    }

    private companion object {
        const val MANAGER = "manager-1"
        const val CASHIER = "cashier-1"
        const val UNPROVISIONED = "manager-2"
        const val PROVISIONED_CASHIER = "cashier-2"

        /** A manager holds this one; see Role.getDefaultPermissions. */
        val APPROVABLE = Permission.POS_VOID_TRANSACTION

        /** Mirrors OverrideAuthorizer.DEFAULT_GRANT_VALIDITY_MILLIS, which is private. */
        const val GRANT_VALIDITY_MILLIS = 5 * 60 * 1000L
    }
}
