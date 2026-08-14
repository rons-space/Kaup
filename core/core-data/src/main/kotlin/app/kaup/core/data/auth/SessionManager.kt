package app.kaup.core.data.auth

import app.kaup.core.data.entities.UserEntity
import app.kaup.shared.domain.auth.AuthorizationDecision
import app.kaup.shared.domain.auth.AuthorizationPolicy
import app.kaup.shared.domain.auth.ElevationToken
import app.kaup.shared.domain.models.auth.Permission
import app.kaup.shared.domain.models.auth.getDefaultPermissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds who is signed in, what they may do, and any elevation they have been
 * lent.
 *
 * Permissions come from the role and nothing else. They used to come from a
 * `permissionsOverride` column read out of an unencrypted database, which meant
 * a single row edit in a pulled database file promoted any account to owner.
 * ADR-021 removes the column rather than authenticating it: the `role` column
 * beside it is exactly as forgeable, so a MAC over one and not the other would
 * only have looked like protection. Per-user grants return when sync can sign
 * them.
 *
 * The elevation token lives here, in memory, and dies with the session.
 * Persisting it would leave a standing privilege escalation in a database that
 * is not yet encrypted, and would let it survive the process death it should
 * not survive.
 */
@Singleton
class SessionManager @Inject constructor() {
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _permissions = MutableStateFlow<Set<Permission>>(emptySet())
    val permissions: StateFlow<Set<Permission>> = _permissions.asStateFlow()

    private val _elevationToken = MutableStateFlow<ElevationToken?>(null)

    /** Exposed so the UI can show that borrowed authority is in effect. */
    val elevationToken: StateFlow<ElevationToken?> = _elevationToken.asStateFlow()

    fun login(user: UserEntity) {
        _currentUser.value = user
        _permissions.value = user.role.getDefaultPermissions()
        // Elevation belongs to one person's session, never to the device.
        _elevationToken.value = null
    }

    fun logout() {
        _currentUser.value = null
        _permissions.value = emptySet()
        _elevationToken.value = null
    }

    fun hasPermission(permission: Permission): Boolean {
        return _permissions.value.contains(permission)
    }

    fun hasAnyPermission(vararg permissions: Permission): Boolean {
        val current = _permissions.value
        return permissions.any { current.contains(it) }
    }

    fun hasAllPermissions(vararg permissions: Permission): Boolean {
        val current = _permissions.value
        return permissions.all { current.contains(it) }
    }

    /**
     * Decides how [permission] may be exercised right now.
     *
     * This does not spend an elevation token. A caller that acts on
     * [AuthorizationDecision.GrantedByElevation] must call [spendElevation]
     * before proceeding, so that a token cannot authorise two actions.
     */
    fun authorize(
        permission: Permission,
        nowUptimeMillis: Long,
        elevationTokensEnabled: Boolean = true
    ): AuthorizationDecision = AuthorizationPolicy.evaluate(
        permission = permission,
        sessionPermissions = _permissions.value,
        elevationToken = _elevationToken.value,
        nowUptimeMillis = nowUptimeMillis,
        elevationTokensEnabled = elevationTokensEnabled
    )

    /** Stores a token minted by a validated elevation-scope override. */
    fun grantElevation(token: ElevationToken) {
        _elevationToken.value = token
    }

    /**
     * Takes the token and clears it in one step, returning null if there was
     * none.
     *
     * The atomic swap is what makes an elevation token single use. Reading it
     * and clearing it separately would let two actions started at once both see
     * a live token and both proceed.
     */
    fun spendElevation(): ElevationToken? = _elevationToken.getAndUpdate { null }

    /** Drops any elevation, for the admin switch and for lock-on-idle. */
    fun clearElevation() {
        _elevationToken.value = null
    }
}
