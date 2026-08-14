package app.kaup.core.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.kaup.shared.models.MovementDirection
import app.kaup.shared.models.MovementType
import app.kaup.shared.models.SyncStatus

/**
 * The persisted form of `app.kaup.shared.models.StockMovement`.
 *
 * The two used to disagree: the entity stored the type as a free String whose
 * comment listed values the domain enum did not contain, and it carried no
 * direction, no transaction reference and no sync status. Stock is computed by
 * replaying this table, so a row that cannot be mapped back to the domain model
 * is a wrong stock figure, not a display bug.
 */
@Entity(
    tableName = "stock_movements",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("locationId"),
        Index("itemId"),
        Index("transactionId"),
        Index("syncStatus")
    ]
)
data class StockMovementEntity(
    @PrimaryKey val id: String,
    val locationId: String,
    val itemId: String,
    // Thousandths of a unit, matching Quantity.SCALE in the domain model. Stock
    // is a sum over this table, so the column is INTEGER: a REAL would
    // reintroduce exactly the float drift ADR-020 removes.
    //
    // The unit is in the name because the column is a bare Long, and a Long
    // called quantity is the exact ambiguity ADR-020 is about: SELECT
    // SUM(quantity) would silently return thousandths.
    //
    // It is a Long rather than a Quantity because Room 2.6.1 unwraps a value
    // class with getDeclaredFields().single(). Quantity's companion object
    // compiles SCALE and ZERO to static fields on the class, so it declares
    // three fields and that call throws "List has more than one element". The
    // getter-only isNegative and isZero are not involved: they have no backing
    // field. Any value class with a companion hits this, Money included, so
    // persist the primitive and convert at the edge.
    //
    // That also matches timestamp, an epoch Long rather than an Instant: this
    // entity is the persistence shape, not the domain one.
    val quantityThousandths: Long,
    val movementType: MovementType,
    val direction: MovementDirection,
    // Set when the movement was produced by a sale, void or refund. There is no
    // foreign key yet because the transactions table does not exist; add the
    // constraint in the same change that introduces it.
    val transactionId: String? = null,
    val timestamp: Long,
    val deviceId: String,
    val syncStatus: SyncStatus = SyncStatus.PENDING
)
