package app.kaup.shared.models

import kotlinx.datetime.Instant

enum class MovementType {
    SALE,
    RECEIVING,
    TRANSFER,
    ADJUSTMENT,
    WASTE,

    // A voided sale and a refund are separate events, not deletions: the log is
    // append-only, so reversing a sale means writing the opposite movement and
    // keeping the original. The two are distinguished because a void happens
    // before the customer leaves and a return brings stock back later, and
    // reports treat them differently.
    VOID,
    RETURN
}

enum class MovementDirection {
    IN, OUT
}

enum class SyncStatus {
    PENDING, SYNCING, SYNCED, FAILED, CONFLICT
}

data class StockMovement(
    val id: String,
    val itemId: String,
    val type: MovementType,
    val direction: MovementDirection,
    val quantity: Double,
    val transactionId: String?,
    val deviceId: String,
    val timestamp: Instant,
    val syncStatus: SyncStatus = SyncStatus.PENDING
)
