package app.kaup.shared.models

import kotlin.math.ceil
import kotlin.math.floor

/**
 * The money arithmetic primitives from ADR-019, in one place so there is a
 * single answer to "how does this round" and "what happens on overflow".
 *
 * These are internal on purpose. Everything outside `:shared-kmp` should be
 * going through [Money] and the domain calculators, not doing its own minor
 * unit maths.
 */

// --- Overflow-checked Long arithmetic ---------------------------------------
//
// The Kotlin common standard library has no Math.addExact, so the JDK's checks
// are reproduced here. Each is branch-free on the happy path, which matters
// because they run per line item.

internal fun addExact(a: Long, b: Long): Long {
    val result = a + b
    // Overflow is only possible when the operands share a sign, and it happened
    // when the result does not share that sign.
    if ((a xor result) and (b xor result) < 0L) {
        throw ArithmeticException("Money overflow: $a + $b")
    }
    return result
}

internal fun subtractExact(a: Long, b: Long): Long {
    val result = a - b
    // Overflow is only possible when the operands differ in sign, and it
    // happened when the result's sign differs from the first operand's.
    if ((a xor b) and (a xor result) < 0L) {
        throw ArithmeticException("Money overflow: $a - $b")
    }
    return result
}

internal fun multiplyExact(a: Long, b: Long): Long {
    val result = a * b
    val absA = if (a < 0L) -a else a
    val absB = if (b < 0L) -b else b
    // If neither operand exceeds 31 bits the product cannot exceed 62, so no
    // check is needed. Note -Long.MIN_VALUE stays negative and so fails this
    // test, falling through to the division check that catches it.
    if ((absA or absB) ushr 31 != 0L) {
        if (b != 0L && (result / b != a || (a == Long.MIN_VALUE && b == -1L))) {
            throw ArithmeticException("Money overflow: $a * $b")
        }
    }
    return result
}

// --- Rounding ---------------------------------------------------------------

/** Largest and smallest Double that still converts to a Long without saturating. */
private const val LONG_MAX_AS_DOUBLE = 9.223372036854776E18
private const val LONG_MIN_AS_DOUBLE = -9.223372036854776E18

/**
 * The only place a fractional money value becomes minor units (ADR-019).
 *
 * Ties round away from zero, so 2.5 becomes 3 and -2.5 becomes -3. That
 * symmetry is what makes a refund the exact negation of the sale that produced
 * it. `Double.roundToLong` breaks ties towards positive infinity instead, which
 * is why it is banned in money code, and it saturates silently at the Long
 * bounds where this throws.
 */
internal fun roundHalfAwayFromZero(value: Double): Long {
    require(value.isFinite()) { "Money value must be finite, was $value" }
    val rounded = if (value >= 0.0) floor(value + 0.5) else ceil(value - 0.5)
    if (rounded >= LONG_MAX_AS_DOUBLE || rounded <= LONG_MIN_AS_DOUBLE) {
        throw ArithmeticException("Money overflow: $value does not fit in minor units")
    }
    return rounded.toLong()
}

// --- Allocation -------------------------------------------------------------

/**
 * Splits [total] across [weights] so the shares sum to exactly [total].
 *
 * Integer shares proportional to each weight, then the leftover minor units are
 * handed out one at a time to the largest fractional remainders, ties broken by
 * position. This is the largest-remainder method, and ADR-019 requires it for
 * every cart-level amount spread over lines.
 *
 * Rounding each share independently is the obvious alternative and is exactly
 * the defect this replaces: independently rounded shares do not add up to the
 * amount granted, so the per-line tax bases stop summing to the cart tax base.
 */
internal fun allocateByLargestRemainder(total: Long, weights: List<Long>): List<Long> {
    require(total >= 0L) { "Cannot allocate a negative total: $total" }
    require(weights.all { it >= 0L }) { "Allocation weights must be non-negative: $weights" }
    if (weights.isEmpty()) return emptyList()

    var weightSum = 0L
    for (weight in weights) weightSum = addExact(weightSum, weight)
    if (total == 0L || weightSum == 0L) return List(weights.size) { 0L }

    val shares = LongArray(weights.size)
    val remainders = LongArray(weights.size)
    var allocated = 0L
    for (index in weights.indices) {
        val numerator = multiplyExact(weights[index], total)
        shares[index] = numerator / weightSum
        remainders[index] = numerator % weightSum
        allocated = addExact(allocated, shares[index])
    }

    // Every remainder is below weightSum, so the shortfall is strictly less
    // than the number of weights and one extra unit per line is always enough.
    val shortfall = total - allocated
    val byRemainder = weights.indices.sortedWith(
        compareByDescending<Int> { remainders[it] }.thenBy { it }
    )
    for (position in 0 until shortfall.toInt()) {
        shares[byRemainder[position]] += 1L
    }

    return shares.toList()
}
