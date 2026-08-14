package app.kaup.shared.domain.auth

import app.kaup.shared.domain.models.auth.Permission

/**
 * The outcome of asking whether the current session may perform an action.
 */
sealed interface AuthorizationDecision {

    /** The signed-in user holds the permission outright. */
    data object GrantedBySession : AuthorizationDecision

    /**
     * A live elevation token covers the action. The caller must spend the
     * token, meaning discard it and write the audit row, before proceeding.
     */
    data class GrantedByElevation(val token: ElevationToken) : AuthorizationDecision

    /**
     * The action is restricted and needs a manager override. This is not a
     * refusal: it is the instruction to raise `ManagerApprovalOverlay` for
     * [permission].
     */
    data class RequiresManagerApproval(val permission: Permission) : AuthorizationDecision
}

/**
 * The single place that decides whether an action may proceed.
 *
 * This is pure so that it can be tested without Room, a device or a running
 * app, and so that the same rule is used by the enforcement point in
 * `:core-data` and by the Compose helpers that grey buttons out. The UI copy of
 * the check is a convenience for the cashier, never the thing that protects the
 * till: every restricted operation asks the enforcement point again before it
 * commits. See ADR-021.
 */
object AuthorizationPolicy {

    /**
     * Decides how [permission] may be exercised by a session holding
     * [sessionPermissions], optionally carrying [elevationToken].
     *
     * The session's own permissions win before the token is even considered, so
     * an unrelated elevation token is not silently burned on an action the
     * cashier could already perform.
     */
    fun evaluate(
        permission: Permission,
        sessionPermissions: Set<Permission>,
        elevationToken: ElevationToken? = null,
        nowUptimeMillis: Long = 0L,
        elevationTokensEnabled: Boolean = true
    ): AuthorizationDecision {
        if (permission in sessionPermissions) {
            return AuthorizationDecision.GrantedBySession
        }
        if (elevationToken != null &&
            ElevationTokenPolicy.authorises(
                elevationToken,
                permission,
                nowUptimeMillis,
                elevationTokensEnabled
            )
        ) {
            return AuthorizationDecision.GrantedByElevation(elevationToken)
        }
        return AuthorizationDecision.RequiresManagerApproval(permission)
    }

    /**
     * Whether a manager may approve [permission] for someone else.
     *
     * A valid HOTP code proves only that the holder of a particular secret
     * generated it. It says nothing about that person's authority, so the
     * approver's own permissions are checked separately: a manager cannot
     * delegate what they do not have. Without this, provisioning an HOTP secret
     * to any account would quietly make it an administrator.
     */
    fun canApprove(approverPermissions: Set<Permission>, permission: Permission): Boolean =
        permission in approverPermissions
}
