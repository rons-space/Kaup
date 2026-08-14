package app.kaup.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.kaup.core.data.entities.LocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(location: LocationEntity): Long

    @Query("SELECT * FROM locations WHERE isDefault = 1 LIMIT 1")
    fun getDefaultLocation(): Flow<LocationEntity?>

    /**
     * One-shot read for callers that need the location id to write a row, such
     * as onboarding creating the first user. The seeding callback guarantees a
     * row exists, so a null here means the database was tampered with.
     */
    @Query("SELECT * FROM locations WHERE isDefault = 1 LIMIT 1")
    fun getDefaultLocationOnce(): LocationEntity?

    @Query("SELECT COUNT(*) FROM locations")
    fun count(): Int
}
