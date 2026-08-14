package app.kaup.core.data.auth

import androidx.room.withTransaction
import app.kaup.core.data.KaupDatabase
import app.kaup.core.data.crypto.SealedSecretUnrecoverableException
import app.kaup.core.data.crypto.SecretSealer
import app.kaup.core.data.dao.OverrideLogDao
import app.kaup.core.data.dao.UserDao
import app.kaup.core.data.entities.OverrideLogEntity
import app.kaup.core.data.entities.UserEntity
import app.kaup.core.data.time.TimeProvider
import app.kaup.shared.domain.HOTPGenerator
import app.kaup.shared.domain.auth.AuthorizationPolicy
import app.kaup.shared.domain.auth.ElevationToken
import app.kaup.shared.domain.auth.ElevationTokenPolicy
import app.kaup.shared.domain.auth.OverrideScope
import app.kaup.shared.domain.auth.OverrideThrottlePolicy
import app.kaup.shared.domain.models.auth.Permission
import app.kaup.shared.domain.models.auth.getDefaultPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a manager override attempt. */
sealed interface OverrideResult {
    /**
     * The action may proceed. The counter has been consumed and the audit row
     * written, both inside one transaction, before this was returned.
     *
     * [token] is non-null only for [OverrideScope.ELEVATION_TOKEN] and must be
     * handed to `SessionManager.grantElevation`.
     */
    data class Granted(
        val logId: String,
        val counterUsed: Long,
        val token: ElevationToken?
    ) : OverrideResult

    /** Wrong code. [attemptsBeforeLockout] is zero when the next try locks. */
    data class Rejected(val attemptsBeforeLockout: Int) : OverrideResult

    /** Too many wrong codes. [remainingMillis] is how long the caller must wait. */
    data class LockedOut(val remainingMillis: Long) : OverrideResult

    /** The chosen approver has no HOTP secret and cannot approve anything yet. */
    data object NotProvisioned : OverrideResult

    /**
     * The code was not even checked, because the chosen approver does not hold
     * [permission] themselves.
     */
    data class ApproverNotPermitted(val permission: Permission) : OverrideResult

    /** Elevation tokens are switched off for this store. */
    data object ElevationDisabled : OverrideResult

    data class Unavailable(val reason: String) : OverrideResult
}

/**
 * Validates a manager override code and, if it holds up, records the grant.
 *
 * This is the enforcement point. `HOTPGenerator` only says whether a code
 * matches; everything that makes the match mean something happens here:
 * throttling, checking the approver's own authority, consuming the counter and
 * writing the audit row.
 *
 * The grant is atomic. The counter advance and the `override_log` insert share
 * one transaction, so there is no window in which a code has been spent with no
 * record of what it bought, or a record exists for a code that could still be
 * replayed. See ADR-021.
 */
@Singleton
class OverrideAuthorizer @Inject constructor(
    private val database: KaupDatabase,
    private val userDao: UserDao,
    private val overrideLogDao: OverrideLogDao,
    private val secretSealer: SecretSealer,
    private val timeProvider: TimeProvider
) {

    /**
     * Re-checks that a grant this operation was handed actually exists and
     * covers what is about to be done.
     *
     * This is what makes an approval a capability rather than a boolean the UI
     * remembered. An operation is given the id of an `override_log` row and
     * looks it up here, so a screen cannot claim to have been approved, and a
     * grant for one permission cannot be spent on another.
     *
     * [maxAgeMillis] bounds how long a grant can sit unused before the action
     * it authorised has to be approved again. Wall clock is correct here
     * because the row's timestamp is wall clock; an operator winding the clock
     * back can extend this window, which is a fair trade against an audit trail
     * that has to be comparable across devices, and the code itself was already
     * consumed either way.
     */
    suspend fun verifyGrant(
        logId: String,
        permission: Permission,
        requestedByUserId: String,
        maxAgeMillis: Long = DEFAULT_GRANT_VALIDITY_MILLIS
    ): Boolean = withContext(Dispatchers.IO) {
        val entry = overrideLogDao.getById(logId) ?: return@withContext false
        val age = timeProvider.epochMillis() - entry.grantedAtEpochMillis
        entry.permission == permission &&
            entry.requestedByUserId == requestedByUserId &&
            age in 0..maxAgeMillis
    }

    /** Managers who have a secret provisioned, for the approver picker. */
    suspend fun approversFor(permission: Permission): List<UserEntity> =
        withContext(Dispatchers.IO) {
            userDao.getUsersWithHotpSecret().filter {
                AuthorizationPolicy.canApprove(it.role.getDefaultPermissions(), permission)
            }
        }

    suspend fun authorize(
        approverUserId: String,
        requestedByUserId: String,
        permission: Permission,
        code: String,
        scope: OverrideScope = OverrideScope.SPECIFIC_ACTION,
        transactionId: String? = null,
        elevationTokensEnabled: Boolean = true,
        elevationWindowMillis: Long = ElevationTokenPolicy.DEFAULT_WINDOW_MILLIS
    ): OverrideResult = withContext(Dispatchers.IO) {
        if (scope == OverrideScope.ELEVATION_TOKEN && !elevationTokensEnabled) {
            return@withContext OverrideResult.ElevationDisabled
        }

        val approver = userDao.getUserById(approverUserId)
            ?: return@withContext OverrideResult.Unavailable("No such approver")
        val encrypted = approver.hotpSecretEncrypted
            ?: return@withContext OverrideResult.NotProvisioned

        val approverPermissions = approver.role.getDefaultPermissions()
        // Checked before the code is looked at, so that a code is never
        // consumed proving something that could not have been authorised
        // anyway, and so a mistyped approver does not burn the manager's
        // attempt allowance.
        if (!AuthorizationPolicy.canApprove(approverPermissions, permission)) {
            return@withContext OverrideResult.ApproverNotPermitted(permission)
        }

        val now = timeProvider.uptimeMillis()
        val lockoutUntil = OverrideThrottlePolicy.resolveLockoutAfterReboot(
            nowUptimeMillis = now,
            storedLockoutUntilUptimeMillis = approver.overrideLockoutUntilUptimeMillis,
            failedAttempts = approver.failedOverrideAttempts
        )
        if (lockoutUntil != approver.overrideLockoutUntilUptimeMillis) {
            userDao.updateOverrideAttempts(approver.id, approver.failedOverrideAttempts, lockoutUntil)
        }
        val remaining = OverrideThrottlePolicy.remainingLockoutMillis(now, lockoutUntil)
        if (remaining > 0L) return@withContext OverrideResult.LockedOut(remaining)

        val secret = try {
            secretSealer.decrypt(encrypted)
        } catch (e: SealedSecretUnrecoverableException) {
            return@withContext OverrideResult.Unavailable(
                e.message ?: "The stored HOTP secret cannot be read"
            )
        }

        val currentCounter = userDao.getHotpCounter(approverUserId)
            ?: return@withContext OverrideResult.Unavailable("No such approver")

        val matched = try {
            HOTPGenerator.validateCode(
                secret = secret,
                currentCounter = currentCounter,
                inputCode = code.trim()
            )
        } finally {
            secret.fill(0)
        }

        if (matched == null) {
            return@withContext recordFailure(approver, now)
        }

        grant(
            approver = approver,
            requestedByUserId = requestedByUserId,
            permission = permission,
            scope = scope,
            transactionId = transactionId,
            currentCounter = currentCounter,
            matchedCounter = matched,
            approverPermissions = approverPermissions,
            nowUptimeMillis = now,
            elevationWindowMillis = elevationWindowMillis
        )
    }

    private fun recordFailure(approver: UserEntity, nowUptimeMillis: Long): OverrideResult {
        val attempts = approver.failedOverrideAttempts + 1
        val nextLockout = OverrideThrottlePolicy.lockoutUntil(nowUptimeMillis, attempts)
        userDao.updateOverrideAttempts(approver.id, attempts, nextLockout)

        val nextRemaining = OverrideThrottlePolicy.remainingLockoutMillis(nowUptimeMillis, nextLockout)
        return if (nextRemaining > 0L) {
            OverrideResult.LockedOut(nextRemaining)
        } else {
            OverrideResult.Rejected(
                attemptsBeforeLockout =
                    (OverrideThrottlePolicy.FREE_ATTEMPTS - attempts).coerceAtLeast(0)
            )
        }
    }

    private suspend fun grant(
        approver: UserEntity,
        requestedByUserId: String,
        permission: Permission,
        scope: OverrideScope,
        transactionId: String?,
        currentCounter: Long,
        matchedCounter: Long,
        approverPermissions: Set<Permission>,
        nowUptimeMillis: Long,
        elevationWindowMillis: Long
    ): OverrideResult {
        val logId = UUID.randomUUID().toString()
        // The requesting user's location is where the action is happening. The
        // approver may well be standing somewhere else, or be a regional
        // manager attached to another site.
        val locationId = userDao.getUserById(requestedByUserId)?.locationId ?: approver.locationId

        val committed = database.withTransaction<Boolean> {
            // Advancing past the matched counter, rather than by one, is what
            // kills the codes between the old value and the accepted one. A
            // drifted code being valid must not leave the ones behind it valid.
            val advanced = userDao.advanceHotpCounter(
                userId = approver.id,
                expectedCounter = currentCounter,
                newCounter = matchedCounter + 1
            )
            if (advanced != 1) return@withTransaction false

            overrideLogDao.insert(
                OverrideLogEntity(
                    id = logId,
                    approvedByUserId = approver.id,
                    requestedByUserId = requestedByUserId,
                    permission = permission,
                    transactionId = transactionId,
                    scope = scope,
                    counterUsed = matchedCounter,
                    grantedAtEpochMillis = timeProvider.epochMillis(),
                    locationId = locationId
                )
            )
            userDao.updateOverrideAttempts(approver.id, failedAttempts = 0, lockoutUntil = 0L)
            true
        }

        if (!committed) {
            // Something else consumed this counter between validation and the
            // write. The code is spent either way, so this is a refusal rather
            // than a retry, and it is not a failed attempt against the manager.
            return OverrideResult.Unavailable("That code was already used; ask for a new one")
        }

        val token = if (scope == OverrideScope.ELEVATION_TOKEN) {
            ElevationTokenPolicy.issue(
                grantedByUserId = approver.id,
                grantedPermissions = approverPermissions,
                nowUptimeMillis = nowUptimeMillis,
                windowMillis = elevationWindowMillis
            )
        } else {
            null
        }

        return OverrideResult.Granted(logId = logId, counterUsed = matchedCounter, token = token)
    }

    private companion object {
        /**
         * How long a specific-action grant stays spendable. Long enough to
         * finish the thing that was approved, short enough that an approval
         * obtained this morning cannot be spent this afternoon.
         */
        const val DEFAULT_GRANT_VALIDITY_MILLIS = 5 * 60 * 1000L
    }
}
