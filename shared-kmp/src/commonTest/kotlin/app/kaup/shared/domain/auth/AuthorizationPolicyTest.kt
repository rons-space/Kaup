package app.kaup.shared.domain.auth

import app.kaup.shared.domain.models.auth.Permission
import app.kaup.shared.domain.models.auth.Role
import app.kaup.shared.domain.models.auth.getDefaultPermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthorizationPolicyTest {

    private val cashier = Role.CASHIER.getDefaultPermissions()
    private val manager = Role.MANAGER.getDefaultPermissions()

    @Test
    fun `a permission the session holds is granted outright`() {
        val decision = AuthorizationPolicy.evaluate(Permission.POS_CHECKOUT, cashier)
        assertIs<AuthorizationDecision.GrantedBySession>(decision)
    }

    @Test
    fun `a permission the session lacks asks for manager approval`() {
        val decision = AuthorizationPolicy.evaluate(Permission.POS_VOID_TRANSACTION, cashier)
        assertEquals(
            AuthorizationDecision.RequiresManagerApproval(Permission.POS_VOID_TRANSACTION),
            decision
        )
    }

    @Test
    fun `a live elevation token covers a permission the session lacks`() {
        val token = ElevationTokenPolicy.issue("manager-1", manager, nowUptimeMillis = 0L)
        val decision = AuthorizationPolicy.evaluate(
            permission = Permission.POS_VOID_TRANSACTION,
            sessionPermissions = cashier,
            elevationToken = token,
            nowUptimeMillis = 1_000L
        )
        assertEquals(AuthorizationDecision.GrantedByElevation(token), decision)
    }

    @Test
    fun `the session is consulted before the token so the token is not wasted`() {
        // POS_CHECKOUT is something the cashier can already do. Spending an
        // elevation token on it would burn a single-use grant for nothing.
        val token = ElevationTokenPolicy.issue("manager-1", manager, nowUptimeMillis = 0L)
        val decision = AuthorizationPolicy.evaluate(
            permission = Permission.POS_CHECKOUT,
            sessionPermissions = cashier,
            elevationToken = token,
            nowUptimeMillis = 1_000L
        )
        assertIs<AuthorizationDecision.GrantedBySession>(decision)
    }

    @Test
    fun `an expired token falls back to asking for approval`() {
        val token = ElevationTokenPolicy.issue("manager-1", manager, nowUptimeMillis = 0L)
        val decision = AuthorizationPolicy.evaluate(
            permission = Permission.POS_VOID_TRANSACTION,
            sessionPermissions = cashier,
            elevationToken = token,
            nowUptimeMillis = ElevationTokenPolicy.DEFAULT_WINDOW_MILLIS
        )
        assertIs<AuthorizationDecision.RequiresManagerApproval>(decision)
    }

    @Test
    fun `turning elevation tokens off kills the ones already issued`() {
        val token = ElevationTokenPolicy.issue("manager-1", manager, nowUptimeMillis = 0L)
        val decision = AuthorizationPolicy.evaluate(
            permission = Permission.POS_VOID_TRANSACTION,
            sessionPermissions = cashier,
            elevationToken = token,
            nowUptimeMillis = 1_000L,
            elevationTokensEnabled = false
        )
        assertIs<AuthorizationDecision.RequiresManagerApproval>(decision)
    }

    @Test
    fun `a token cannot grant more than the manager who issued it had`() {
        // A manager does not hold the USERS_* permissions, so an elevation
        // token from one cannot be spent on user administration.
        val token = ElevationTokenPolicy.issue("manager-1", manager, nowUptimeMillis = 0L)
        assertFalse(Permission.USERS_DELETE in manager)
        val decision = AuthorizationPolicy.evaluate(
            permission = Permission.USERS_DELETE,
            sessionPermissions = cashier,
            elevationToken = token,
            nowUptimeMillis = 1_000L
        )
        assertIs<AuthorizationDecision.RequiresManagerApproval>(decision)
    }

    @Test
    fun `an approver cannot delegate a permission they do not hold`() {
        assertTrue(AuthorizationPolicy.canApprove(manager, Permission.POS_VOID_TRANSACTION))
        assertFalse(AuthorizationPolicy.canApprove(manager, Permission.USERS_DELETE))
        assertFalse(AuthorizationPolicy.canApprove(cashier, Permission.POS_VOID_TRANSACTION))
        assertTrue(
            AuthorizationPolicy.canApprove(Role.OWNER.getDefaultPermissions(), Permission.USERS_DELETE)
        )
    }

    @Test
    fun `token windows are bounded`() {
        assertEquals(5 * 60 * 1000L, ElevationTokenPolicy.DEFAULT_WINDOW_MILLIS)
        assertFailsWith<IllegalArgumentException> {
            ElevationTokenPolicy.issue("m", manager, 0L, windowMillis = 0L)
        }
        assertFailsWith<IllegalArgumentException> {
            ElevationTokenPolicy.issue("m", manager, 0L, ElevationTokenPolicy.MAX_WINDOW_MILLIS + 1)
        }
    }

    @Test
    fun `remaining token life counts down and clamps`() {
        val token = ElevationTokenPolicy.issue("manager-1", manager, nowUptimeMillis = 1_000L)
        assertEquals(ElevationTokenPolicy.DEFAULT_WINDOW_MILLIS, ElevationTokenPolicy.remainingMillis(token, 1_000L))
        assertEquals(0L, ElevationTokenPolicy.remainingMillis(token, 9_999_999L))
    }

    @Test
    fun `the token expiry boundary is exclusive`() {
        val token = ElevationTokenPolicy.issue("manager-1", manager, nowUptimeMillis = 0L)
        val expiry = token.expiresAtUptimeMillis
        assertTrue(
            ElevationTokenPolicy.authorises(token, Permission.POS_VOID_TRANSACTION, expiry - 1, true)
        )
        assertFalse(
            ElevationTokenPolicy.authorises(token, Permission.POS_VOID_TRANSACTION, expiry, true)
        )
    }
}
