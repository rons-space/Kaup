package app.kaup.core.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.kaup.shared.models.SyncStatus
import java.util.UUID

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String?,
    val isDefault: Boolean = true,
    val syncStatus: SyncStatus = SyncStatus.PENDING
) {
    companion object {
        /**
         * The id of the location seeded when the database is created.
         *
         * ADR-016 requires exactly one location to exist before any other row
         * is written, because every location-aware table carries a non-null
         * `locationId` foreign key. A fixed id rather than a random one means
         * callers can reference the default location without a lookup, and it
         * stays stable across the destructive recreations of the alpha phase.
         */
        const val DEFAULT_ID: String = "00000000-0000-0000-0000-000000000001"
        const val DEFAULT_NAME: String = "Main"
    }
}
