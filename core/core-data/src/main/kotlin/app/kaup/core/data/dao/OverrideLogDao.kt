package app.kaup.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.kaup.core.data.entities.OverrideLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Reads and appends the manager override audit trail.
 *
 * There is no update and no delete. The table is append-only: an audit record
 * that can be edited after the fact is not evidence of anything. Housekeeping
 * that eventually trims old rows will be an explicit, permission-gated
 * operation rather than a DAO method sitting here waiting to be called by
 * accident.
 *
 * [insert] uses ABORT rather than the REPLACE used elsewhere. Two rows sharing
 * an id means a bug in id generation, and overwriting the first one would
 * destroy a record of a real grant to hide it.
 */
@Dao
interface OverrideLogDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(entry: OverrideLogEntity)

    @Query("SELECT * FROM override_log ORDER BY grantedAtEpochMillis DESC")
    fun observeAll(): Flow<List<OverrideLogEntity>>

    @Query(
        "SELECT * FROM override_log WHERE approvedByUserId = :userId " +
            "ORDER BY grantedAtEpochMillis DESC"
    )
    fun observeApprovedBy(userId: String): Flow<List<OverrideLogEntity>>

    @Query(
        "SELECT * FROM override_log WHERE transactionId = :transactionId " +
            "ORDER BY grantedAtEpochMillis DESC"
    )
    fun getForTransaction(transactionId: String): List<OverrideLogEntity>

    @Query("SELECT * FROM override_log WHERE syncStatus = 'PENDING' ORDER BY grantedAtEpochMillis ASC")
    fun getPendingSync(): List<OverrideLogEntity>

    @Query("UPDATE override_log SET syncStatus = :status WHERE id = :id")
    fun updateSyncStatus(id: String, status: String)

    /**
     * Counts grants recorded against a counter value for a manager.
     *
     * A successful override can only ever produce one row per counter, because
     * the counter advances in the same transaction. Anything above one is a
     * replay or a bug, and this is what a future integrity check will call.
     */
    @Query(
        "SELECT COUNT(*) FROM override_log WHERE approvedByUserId = :userId " +
            "AND counterUsed = :counter"
    )
    fun countGrantsAtCounter(userId: String, counter: Long): Int
}
