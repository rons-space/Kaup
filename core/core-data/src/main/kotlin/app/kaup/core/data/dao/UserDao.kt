package app.kaup.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.kaup.core.data.entities.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY name ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    fun getUserById(userId: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertUser(user: UserEntity)

    /** Managers who can actually approve something, for the approval overlay. */
    @Query("SELECT * FROM users WHERE hotpSecretEncrypted IS NOT NULL ORDER BY name ASC")
    fun getUsersWithHotpSecret(): List<UserEntity>

    @Query("UPDATE users SET hotpSecretEncrypted = :secret, hotpCounter = :counter WHERE id = :userId")
    fun updateUserHotp(userId: String, secret: String, counter: Long)

    /**
     * Reads the counter straight from the row.
     *
     * Callers used to take it from the cached session user, which is a snapshot
     * taken at login and never refreshed, so every code generated in a session
     * came from the same counter and was therefore the same code. Nothing but
     * this table is the source of truth for it.
     */
    @Query("SELECT hotpCounter FROM users WHERE id = :userId LIMIT 1")
    fun getHotpCounter(userId: String): Long?

    @Query("UPDATE users SET hotpCounter = :counter WHERE id = :userId")
    fun updateUserHotpCounter(userId: String, counter: Long)

    /**
     * Advances the counter only if it still holds the value the caller read.
     *
     * The guard is what makes "read, decide, write" safe without holding a lock
     * across the HMAC work: if anything else moved the counter in between, zero
     * rows are updated and the caller retries or refuses rather than reusing a
     * counter. Callers wrap this in a transaction with the audit insert, so a
     * grant and its record cannot come apart.
     */
    @Query("UPDATE users SET hotpCounter = :newCounter WHERE id = :userId AND hotpCounter = :expectedCounter")
    fun advanceHotpCounter(userId: String, expectedCounter: Long, newCounter: Long): Int

    /**
     * Override attempt state, recorded against the manager being asked to
     * approve. Written on every attempt for the same reason PIN attempts are.
     */
    @Query(
        "UPDATE users SET failedOverrideAttempts = :failedAttempts, " +
            "overrideLockoutUntilUptimeMillis = :lockoutUntil WHERE id = :userId"
    )
    fun updateOverrideAttempts(userId: String, failedAttempts: Int, lockoutUntil: Long)

    /**
     * Brute-force state is written on every attempt, successful or not, so the
     * counter survives the process being killed between attempts.
     */
    @Query(
        "UPDATE users SET failedPinAttempts = :failedAttempts, " +
            "pinLockoutUntilUptimeMillis = :lockoutUntil WHERE id = :userId"
    )
    fun updatePinAttempts(userId: String, failedAttempts: Int, lockoutUntil: Long)

    @Query(
        "UPDATE users SET pinHash = :hash, pinSalt = :salt, pinIterations = :iterations, " +
            "failedPinAttempts = 0, pinLockoutUntilUptimeMillis = 0 WHERE id = :userId"
    )
    fun updatePinCredential(userId: String, hash: String, salt: String, iterations: Int)
}
