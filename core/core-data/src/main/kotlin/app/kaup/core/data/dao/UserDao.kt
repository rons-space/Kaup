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

    @Query("UPDATE users SET hotpSecretEncrypted = :secret, hotpCounter = :counter WHERE id = :userId")
    fun updateUserHotp(userId: String, secret: String, counter: Long)

    @Query("UPDATE users SET hotpCounter = :counter WHERE id = :userId")
    fun updateUserHotpCounter(userId: String, counter: Long)

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
