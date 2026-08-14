package app.kaup.core.data.auth

import androidx.room.withTransaction
import app.kaup.core.data.KaupDatabase
import app.kaup.core.data.crypto.HotpSecretUnrecoverableException
import app.kaup.core.data.crypto.SecretSealer
import app.kaup.core.data.dao.UserDao
import app.kaup.shared.domain.HOTPGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of asking for a fresh override code. */
sealed interface HotpCodeResult {
    /** [counter] is the value the code was generated from, already consumed. */
    data class Issued(val code: String, val counter: Long) : HotpCodeResult

    /** This account has no HOTP secret yet; it must complete provisioning. */
    data object NotProvisioned : HotpCodeResult

    /** The secret exists but cannot be read, or the counter could not be taken. */
    data class Unavailable(val reason: String) : HotpCodeResult
}

/**
 * Issues manager override codes, and owns the counter while doing it.
 *
 * This exists because the ViewModel that used to do the job read the counter
 * from `SessionManager.currentUser`, which is a snapshot taken at login and
 * never refreshed. The row was updated, the snapshot was not, so every code
 * generated in a session came from the same counter and was therefore the
 * **same code**, which quietly removes the "one time" from one-time password.
 *
 * The counter is now read and advanced in one transaction, guarded by its
 * previous value, so two callers racing cannot be handed the same code.
 */
@Singleton
class HotpCodeIssuer @Inject constructor(
    private val database: KaupDatabase,
    private val userDao: UserDao,
    private val secretSealer: SecretSealer
) {

    suspend fun issue(userId: String): HotpCodeResult = withContext(Dispatchers.IO) {
        val user = userDao.getUserById(userId)
            ?: return@withContext HotpCodeResult.Unavailable("No such user")
        val encrypted = user.hotpSecretEncrypted
            ?: return@withContext HotpCodeResult.NotProvisioned

        val secret = try {
            secretSealer.decrypt(encrypted)
        } catch (e: HotpSecretUnrecoverableException) {
            return@withContext HotpCodeResult.Unavailable(
                e.message ?: "The stored HOTP secret cannot be read"
            )
        }

        try {
            val counter = takeNextCounter(userId)
                ?: return@withContext HotpCodeResult.Unavailable(
                    "The counter is being advanced by something else; try again"
                )
            HotpCodeResult.Issued(HOTPGenerator.generateCode(secret, counter), counter)
        } finally {
            // The plaintext secret must not outlive this call, whatever
            // happened above. A ByteArray is the only shape that lets us
            // guarantee that; a String would be interned and immutable.
            secret.fill(0)
        }
    }

    /**
     * Claims the current counter and moves the row past it, returning the value
     * claimed.
     *
     * The update is conditional on the counter still holding the value that was
     * read, so a concurrent claim loses rather than duplicating. Losing is
     * retried a couple of times because the winner's write is quick and the
     * caller has no better recovery than trying again.
     */
    private suspend fun takeNextCounter(userId: String): Long? = database.withTransaction<Long?> {
        repeat(CLAIM_ATTEMPTS) {
            val current = userDao.getHotpCounter(userId) ?: return@withTransaction null
            if (userDao.advanceHotpCounter(userId, current, current + 1) == 1) {
                return@withTransaction current
            }
        }
        null
    }

    private companion object {
        const val CLAIM_ATTEMPTS = 3
    }
}
