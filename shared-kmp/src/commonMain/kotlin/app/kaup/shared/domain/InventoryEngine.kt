package app.kaup.shared.domain

import app.kaup.shared.models.MovementDirection
import app.kaup.shared.models.Quantity
import app.kaup.shared.models.StockMovement
import kotlinx.datetime.Instant

/**
 * Computes stock by replaying the append-only movement log (ADR-002).
 *
 * The accumulator is an integer [Quantity]. It used to be a `Double`, which
 * meant ten movements of 0.1 did not sum to 1.0 and the error grew with the
 * length of the log, in a figure the shop counts against its own shelves.
 */
class InventoryEngine {

    fun computeStock(movements: List<StockMovement>): Quantity =
        accumulate(movements)

    fun computeStockAsOf(movements: List<StockMovement>, targetTime: Instant): Quantity =
        accumulate(movements.filter { it.timestamp <= targetTime })

    /**
     * Sorting does not change an integer sum, but it is kept so this replays in
     * the same order as [ConflictResolver], which does depend on it.
     */
    private fun accumulate(movements: List<StockMovement>): Quantity =
        movements
            .sortedBy { it.timestamp }
            .fold(Quantity.ZERO) { runningStock, movement ->
                when (movement.direction) {
                    MovementDirection.IN -> runningStock + movement.quantity
                    MovementDirection.OUT -> runningStock - movement.quantity
                }
            }
}
