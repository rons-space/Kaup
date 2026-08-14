package app.kaup.core.data.auth

import android.os.SystemClock
import app.kaup.core.data.crypto.PinHasher
import app.kaup.core.data.dao.UserDao
import app.kaup.core.data.entities.UserEntity
import app.kaup.shared.domain.auth.PinLockoutPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a PIN entry attempt. */
sealed interface PinAuthResult {
    data class Success(val user: UserEntity) : PinAuthResult

    /** Wrong PIN. [attemptsBeforeLockout] is zero when the next failure locks. */
    data class Failure(val attemptsBeforeLockout: Int) : PinAuthResult

    /** Entry refused. [remainingMillis] is how long the caller must wait. */
    data class LockedOut(val remainingMillis: Long) : PinAuthResult
}

/**
 * Verifies PINs and owns the brute-force state.
 *
 * The comparison used to live in the lock screen Composable, against a
 * plaintext column. Keeping it here means the hash never leaves `:core-data`,
 * the failure counter cannot be skipped by a caller that forgets it, and the
 * whole thing can be tested without Compose.
 */
@Singleton
class PinAuthenticator @Inject constructor(
    private val userDao: UserDao,
    private val pinHasher: PinHasher
) {

    suspend fun authenticate(userId: String, pin: String): PinAuthResult =
        withContext(Dispatchers.IO) {
            val user = userDao.getUserById(userId)
                ?: return@withContext PinAuthResult.Failure(PinLockoutPolicy.FREE_ATTEMPTS)

            val now = SystemClock.elapsedRealtime()
            val lockoutUntil = PinLockoutPolicy.resolveLockoutAfterReboot(
                nowUptimeMillis = now,
                storedLockoutUntilUptimeMillis = user.pinLockoutUntilUptimeMillis,
                failedAttempts = user.failedPinAttempts
            )
            if (lockoutUntil != user.pinLockoutUntilUptimeMillis) {
                userDao.updatePinAttempts(user.id, user.failedPinAttempts, lockoutUntil)
            }

            val remaining = PinLockoutPolicy.remainingLockoutMillis(now, lockoutUntil)
            if (remaining > 0L) return@withContext PinAuthResult.LockedOut(remaining)

            val matches = pinHasher.verify(
                pin = pin,
                expectedHashHex = user.pinHash,
                saltHex = user.pinSalt,
                iterations = user.pinIterations
            )

            if (matches) {
                userDao.updatePinAttempts(user.id, failedAttempts = 0, lockoutUntil = 0L)
                PinAuthResult.Success(user.copy(failedPinAttempts = 0, pinLockoutUntilUptimeMillis = 0L))
            } else {
                val attempts = user.failedPinAttempts + 1
                val nextLockout = PinLockoutPolicy.lockoutUntil(now, attempts)
                userDao.updatePinAttempts(user.id, attempts, nextLockout)
                val nextRemaining = PinLockoutPolicy.remainingLockoutMillis(now, nextLockout)
                if (nextRemaining > 0L) {
                    PinAuthResult.LockedOut(nextRemaining)
                } else {
                    PinAuthResult.Failure(
                        attemptsBeforeLockout = (PinLockoutPolicy.FREE_ATTEMPTS - attempts).coerceAtLeast(0)
                    )
                }
            }
        }

    /** Hashes [pin] for a new or changed credential. Never stores the PIN itself. */
    fun newCredential(pin: String): PinCredential {
        val salt = pinHasher.newSalt()
        return PinCredential(
            hash = pinHasher.hash(pin, salt, PinHasher.DEFAULT_ITERATIONS),
            salt = salt,
            iterations = PinHasher.DEFAULT_ITERATIONS
        )
    }
}

data class PinCredential(
    val hash: String,
    val salt: String,
    val iterations: Int
)
