package app.kaup.core.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.kaup.shared.models.SyncStatus

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("locationId"), Index("syncStatus")]
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val locationId: String,
    val name: String,
    val price: Long,
    val type: String,
    val syncStatus: SyncStatus = SyncStatus.PENDING
)
