package app.kaup.core.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import app.kaup.shared.domain.auth.AuthorizationDecision
import app.kaup.shared.domain.auth.AuthorizationPolicy
import app.kaup.shared.domain.models.auth.Permission

val LocalPermissions = staticCompositionLocalOf<Set<Permission>> { emptySet() }

/**
 * Convenience checks for drawing the UI.
 *
 * These are a courtesy to the cashier, not access control. They decide what to
 * grey out or hide; they cannot decide whether an operation runs, because
 * anything with a reference to a ViewModel can call straight past them. Every
 * restricted operation asks the enforcement point in `:core-data` again
 * immediately before it commits. See ADR-021.
 *
 * They route through [AuthorizationPolicy] rather than testing set membership
 * directly, so the rule the UI applies and the rule the enforcement point
 * applies are the same code and cannot drift.
 */
@Composable
fun hasPermission(permission: Permission): Boolean =
    AuthorizationPolicy.evaluate(permission, LocalPermissions.current) is
        AuthorizationDecision.GrantedBySession

@Composable
fun hasAnyPermission(vararg permissions: Permission): Boolean {
    val current = LocalPermissions.current
    return permissions.any {
        AuthorizationPolicy.evaluate(it, current) is AuthorizationDecision.GrantedBySession
    }
}

@Composable
fun hasAllPermissions(vararg permissions: Permission): Boolean {
    val current = LocalPermissions.current
    return permissions.all {
        AuthorizationPolicy.evaluate(it, current) is AuthorizationDecision.GrantedBySession
    }
}

/**
 * Draws [content] only when the session holds [permission].
 *
 * Hiding rather than disabling is deliberate and is what SECURITY.md promises:
 * a disabled control still tells a cashier the feature exists and invites them
 * to go looking for the manager. Note that this hides on the session's own
 * permissions, so it will hide a control that a manager override could
 * legitimately unlock; use an explicit approval flow where that matters.
 */
@Composable
fun RequirePermission(permission: Permission, content: @Composable () -> Unit) {
    if (hasPermission(permission)) {
        content()
    }
}
