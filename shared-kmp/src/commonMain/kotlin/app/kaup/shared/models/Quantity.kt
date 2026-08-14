package app.kaup.shared.models

import kotlin.jvm.JvmInline

/**
 * A stock quantity in thousandths of a unit.
 *
 * ADR-002 wrote this as a `BigDecimal`, which the Kotlin common standard library
 * does not have. ADR-020 refines that to a scaled integer for the same reason
 * [Money] is one: stock is computed by replaying and summing an append-only log,
 * and binary floating point does not survive that. Ten movements of `0.1` summed
 * as `Double` do not equal `1.0`, so a shop selling by weight would drift away
 * from its own shelf.
 *
 * Three decimal places covers one gram on a kilogram scale and one millilitre on
 * a litre, which is the finest granularity a retail scale reports.
 *
 * Arithmetic is overflow-checked for the same reason [Money]'s is: a wrapped
 * stock figure is worse than a refused operation.
 */
@JvmInline
value class Quantity(val thousandths: Long) : Comparable<Quantity> {

    operator fun plus(other: Quantity): Quantity =
        Quantity(addExact(thousandths, other.thousandths))

    operator fun minus(other: Quantity): Quantity =
        Quantity(subtractExact(thousandths, other.thousandths))

    operator fun times(multiplier: Long): Quantity =
        Quantity(multiplyExact(thousandths, multiplier))

    operator fun unaryMinus(): Quantity =
        if (thousandths == Long.MIN_VALUE) {
            throw ArithmeticException("Quantity overflow: -($thousandths)")
        } else {
            Quantity(-thousandths)
        }

    val isNegative: Boolean get() = thousandths < 0L
    val isZero: Boolean get() = thousandths == 0L

    override operator fun compareTo(other: Quantity): Int =
        thousandths.compareTo(other.thousandths)

    /**
     * For display and for the arithmetic that genuinely needs a real number,
     * such as multiplying a weight by a unit price. Never round-trip through
     * this to store a quantity: use [of] so the rounding is explicit.
     */
    fun toDouble(): Double = thousandths.toDouble() / SCALE

    override fun toString(): String = "Quantity(${toDouble()})"

    companion object {
        /** Thousandths per whole unit. */
        const val SCALE: Long = 1000L

        val ZERO = Quantity(0L)

        /** A whole number of units, for example 3 items off the shelf. */
        fun ofUnits(units: Long): Quantity = Quantity(multiplyExact(units, SCALE))

        /**
         * Converts a decimal quantity, as typed by a cashier or reported by a
         * scale, into thousandths. Rounds by the ADR-019 rule, so a return of a
         * quantity is the exact negation of the sale of it.
         */
        fun of(units: Double): Quantity = Quantity(roundHalfAwayFromZero(units * SCALE))
    }
}
