package app.kaup.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.kaup.core.data.converters.MovementDirectionConverter
import app.kaup.core.data.converters.MovementTypeConverter
import app.kaup.core.data.converters.PermissionSetConverter
import app.kaup.core.data.converters.RoleConverter
import app.kaup.core.data.converters.SyncStatusConverter
import app.kaup.core.data.dao.ItemDao
import app.kaup.core.data.dao.LocationDao
import app.kaup.core.data.dao.StockMovementDao
import app.kaup.core.data.dao.UserDao
import app.kaup.core.data.entities.ItemEntity
import app.kaup.core.data.entities.LocationEntity
import app.kaup.core.data.entities.StockMovementEntity
import app.kaup.core.data.entities.UserEntity

@TypeConverters(
    RoleConverter::class,
    PermissionSetConverter::class,
    SyncStatusConverter::class,
    MovementTypeConverter::class,
    MovementDirectionConverter::class
)
@Database(
    entities = [
        LocationEntity::class,
        ItemEntity::class,
        StockMovementEntity::class,
        UserEntity::class
    ],
    version = KaupDatabase.DATABASE_VERSION,
    exportSchema = true
)
abstract class KaupDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun itemDao(): ItemDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun userDao(): UserDao

    companion object {
        /**
         * Bump this in the same commit as any entity change. It was an inline
         * literal, which is how version 3 ended up identical to version 2.
         *
         * Version history:
         *   4  last version before the schema audit
         *   5  syncStatus on every entity, locationId on users, hashed PIN
         *      columns and lockout state, stock movements reconciled with the
         *      domain model (typed movement, direction, transactionId)
         *   6  stock_movements.quantity moves from REAL to INTEGER
         *      thousandths (ADR-020), so replaying the log stops
         *      accumulating float drift
         */
        const val DATABASE_VERSION: Int = 6

        /**
         * ADR-018 Phase 1: alpha builds recreate the database instead of
         * migrating, and every alpha release note carries the data loss
         * warning.
         *
         * TODO(v0.2-alpha, #203): flip this to false, write the baseline
         * Migration, and add the MigrationTestHelper suite. The cutover is a
         * release blocker for v0.2-alpha, not a follow-up.
         */
        const val ALPHA_DESTRUCTIVE_MIGRATION: Boolean = true
    }
}
