package app.kaup.shared.models

import kotlin.jvm.JvmInline

/**
 * An amount of money in integer minor units (cents, sen, fils).
 *
 * Currency and its exponent are a store-level setting and are deliberately not
 * carried on the value: a single till transacts in one currency, and pairing
 * every amount with a currency tag would invite mixed-currency arithmetic that
 * the domain has no meaning for.
 *
 * Arithmetic is overflow-checked. A till that silently reports a negative total
 * because a Long wrapped around is worse than one that refuses the operation,
 * so every operator throws [ArithmeticException] rather than returning a wrong
 * answer. See ADR-019.
 */
@JvmInline
value class Money(val minorUnits: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(addExact(minorUnits, other.minorUnits))

    operator fun minus(other: Money): Money = Money(subtractExact(minorUnits, other.minorUnits))

    operator fun times(multiplier: Int): Money = times(multiplier.toLong())

    operator fun times(multiplier: Long): Money = Money(multiplyExact(minorUnits, multiplier))

    /**
     * Negation, used to turn a sale line into its refund. Exact by the same
     * rule as the other operators: [Long.MIN_VALUE] has no positive
     * counterpart, so negating it throws rather than returning itself.
     */
    operator fun unaryMinus(): Money =
        if (minorUnits == Long.MIN_VALUE) {
            throw ArithmeticException("Money overflow: -($minorUnits)")
        } else {
            Money(-minorUnits)
        }

    override operator fun compareTo(other: Money): Int = minorUnits.compareTo(other.minorUnits)

    companion object {
        val ZERO = Money(0L)
    }
}
