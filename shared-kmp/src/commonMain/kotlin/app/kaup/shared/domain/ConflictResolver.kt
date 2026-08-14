package app.kaup.shared.domain

import app.kaup.shared.models.MovementDirection
import app.kaup.shared.models.Quantity
import app.kaup.shared.models.StockMovement

/**
 * A movement that left stock below zero.
 *
 * @property movement the movement that was applied.
 * @property stockAfter the stock level once it had been applied, always
 *   negative. This is the depth of the hole, which is the number needed to
 *   reconcile.
 * @property isFirstBreach true for the movement that crossed zero. The rest
 *   followed it down, and a manager needs to tell those apart.
 */
data class StockViolation(
    val movement: StockMovement,
    val stockAfter: Quantity,
    val isFirstBreach: Boolean
)

/**
 * The deterministic interpretation of a set of movements (ADR-020).
 *
 * @property timeline deduplicated and in total order. Every device given the
 *   same input produces this same list, with no coordination.
 * @property discardedDuplicates repeats of an id already in [timeline], the
 *   normal result of a sync that committed and was retried.
 * @property divergentDuplicates duplicates whose content differed from the kept
 *   movement. Retry cannot produce these, so they mean clock, id generation or
 *   storage is broken and a human has to look.
 * @property finalStock the level after replaying [timeline].
 * @property violations every movement that left stock negative.
 */
data class ConflictResolution(
    val timeline: List<StockMovement>,
    val discardedDuplicates: List<StockMovement>,
    val divergentDuplicates: List<StockMovement>,
    val finalStock: Quantity,
    val violations: List<StockViolation>
)

/**
 * Resolves offline conflicts in the stock movement log, per ADR-020.
 *
 * Nothing here edits or discards a fact. The log is append-only and
 * authoritative, so resolution means agreeing on an order, ignoring literal
 * repeats, and reporting what the result implies. There is no last-write-wins.
 */
class ConflictResolver {

    /**
     * Total order over movements: timestamp, then deviceId, then id.
     *
     * Offline device clocks are not trustworthy enough for timestamp alone to
     * be total, and the tie-breakers are values every device already agrees on.
     * This is an order for replay, not a claim about what truly happened first.
     */
    fun sortDeterministically(movements: List<StockMovement>): List<StockMovement> =
        movements.sortedWith(
            compareBy<StockMovement> { it.timestamp }
                .thenBy { it.deviceId }
                .thenBy { it.id }
        )

    /**
     * The whole job in one replay: order, deduplicate, total, and report.
     */
    fun resolve(movements: List<StockMovement>): ConflictResolution {
        val ordered = sortDeterministically(movements)

        val timeline = mutableListOf<StockMovement>()
        val discarded = mutableListOf<StockMovement>()
        val divergent = mutableListOf<StockMovement>()
        val keptById = mutableMapOf<String, StockMovement>()

        for (movement in ordered) {
            val kept = keptById[movement.id]
            if (kept == null) {
                keptById[movement.id] = movement
                timeline.add(movement)
            } else {
                discarded.add(movement)
                // Same id, different body. A retry cannot do this.
                if (kept != movement) divergent.add(movement)
            }
        }

        var stock = Quantity.ZERO
        val violations = mutableListOf<StockViolation>()
        var alreadyNegative = false

        for (movement in timeline) {
            stock = when (movement.direction) {
                MovementDirection.IN -> stock + movement.quantity
                MovementDirection.OUT -> stock - movement.quantity
            }
            if (stock.isNegative) {
                violations.add(
                    StockViolation(
                        movement = movement,
                        stockAfter = stock,
                        isFirstBreach = !alreadyNegative
                    )
                )
                alreadyNegative = true
            } else {
                // Stock recovered, so the next dip is a new breach rather than
                // a continuation of this one.
                alreadyNegative = false
            }
        }

        return ConflictResolution(
            timeline = timeline,
            discardedDuplicates = discarded,
            divergentDuplicates = divergent,
            finalStock = stock,
            violations = violations
        )
    }

    /**
     * Every movement that left stock negative, not just the one that crossed
     * zero.
     *
     * Reporting only the crossing event, which is what this used to do, tells a
     * manager that one sale broke zero and hides the four that followed it
     * down. Both numbers are needed: the first breach explains the oversell and
     * the depth is what has to be reconciled.
     */
    fun detectNegativeStockViolations(movements: List<StockMovement>): List<StockViolation> =
        resolve(movements).violations
}
