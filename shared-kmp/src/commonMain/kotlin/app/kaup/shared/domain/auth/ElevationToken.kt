package app.kaup.shared.domain.auth

import app.kaup.shared.domain.models.auth.Permission

/**
 * A time-boxed, single-use grant produced by an approved
 * [OverrideScope.ELEVATION_TOKEN] override.
 *
 * The token is deliberately not persisted. It lives in memory for the length of
 * one staff session, so that killing the app, switching user or rebooting
 * destroys it. Writing it to Room would make it survivable and would put a
 * standing privilege escalation in a database that is not yet encrypted.
 *
 * @param grantedByUserId the manager whose code was validated, recorded so the
 *   audit row for the action the token is eventually spent on can still name a
 *   human.
 * @param grantedPermissions what the token may authorise. This is the granting
 *   manager's own permission set, captured at issue time: an elevation token
 *   lets staff borrow a manager's authority, never exceed it.
 * @param expiresAtUptimeMillis uptime deadline, for the same reason lockouts
 *   use uptime, so moving the device clock forward cannot extend the window.
 */
data class ElevationToken(
    val grantedByUserId: String,
    val grantedPermissions: Set<Permission>,
    val expiresAtUptimeMillis: Long
)

/**
 * Issuing and checking rules for [ElevationToken].
 */
object ElevationTokenPolicy {

    /** ADR-005's default window. */
    const val DEFAULT_WINDOW_MILLIS: Long = 5 * 60 * 1000L

    /**
     * Hard ceiling on a configured window. The window is admin-configurable per
     * ADR-005, but "valid for any action for the rest of the day" is not an
     * elevation token, it is a second login, so the configuration cannot reach
     * that far.
     */
    const val MAX_WINDOW_MILLIS: Long = 15 * 60 * 1000L

    fun issue(
        grantedByUserId: String,
        grantedPermissions: Set<Permission>,
        nowUptimeMillis: Long,
        windowMillis: Long = DEFAULT_WINDOW_MILLIS
    ): ElevationToken {
        require(windowMillis in 1..MAX_WINDOW_MILLIS) {
            "Elevation window must be within 1..$MAX_WINDOW_MILLIS ms, got $windowMillis"
        }
        return ElevationToken(
            grantedByUserId = grantedByUserId,
            grantedPermissions = grantedPermissions,
            expiresAtUptimeMillis = nowUptimeMillis + windowMillis
        )
    }

    /**
     * Whether [token] can still authorise [permission].
     *
     * [elevationTokensEnabled] is the admin switch from SECURITY.md and is
     * checked here as well as at issue time, so that turning the feature off
     * kills tokens already in flight instead of waiting for them to lapse.
     *
     * Single use is not expressed here because a value type cannot enforce it.
     * The holder discards the token the moment it authorises something, and
     * that discipline is what makes it single use.
     */
    fun authorises(
        token: ElevationToken,
        permission: Permission,
        nowUptimeMillis: Long,
        elevationTokensEnabled: Boolean
    ): Boolean =
        elevationTokensEnabled &&
            nowUptimeMillis < token.expiresAtUptimeMillis &&
            permission in token.grantedPermissions

    /** Milliseconds of life left, or zero once it has lapsed. */
    fun remainingMillis(token: ElevationToken, nowUptimeMillis: Long): Long =
        (token.expiresAtUptimeMillis - nowUptimeMillis).coerceAtLeast(0L)
}
