package app.kaup.shared.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pins the ADR-019 primitives: rounding is symmetric around zero, and
 * allocation is exact.
 */
class MoneyMathTest {

    @Test
    fun `rounds halves away from zero in both directions`() {
        assertEquals(3L, roundHalfAwayFromZero(2.5))
        assertEquals(-3L, roundHalfAwayFromZero(-2.5))
        assertEquals(4L, roundHalfAwayFromZero(3.5))
        assertEquals(-4L, roundHalfAwayFromZero(-3.5))
    }

    @Test
    fun `a refund is the exact negation of the sale that produced it`() {
        // The reason half-away-from-zero was chosen over roundToLong, whose
        // ties go towards positive infinity and so break this symmetry.
        for (cents in listOf(0.5, 1.5, 2.5, 10.5, 1234.5, 0.4, 0.6, 99.99)) {
            assertEquals(
                -roundHalfAwayFromZero(cents),
                roundHalfAwayFromZero(-cents),
                "asymmetric rounding at $cents"
            )
        }
    }

    @Test
    fun `rounds normally away from ties`() {
        assertEquals(2L, roundHalfAwayFromZero(2.4))
        assertEquals(3L, roundHalfAwayFromZero(2.6))
        assertEquals(0L, roundHalfAwayFromZero(0.0))
        assertEquals(0L, roundHalfAwayFromZero(-0.0))
    }

    @Test
    fun `rejects values that are not finite or do not fit`() {
        assertFailsWith<IllegalArgumentException> { roundHalfAwayFromZero(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { roundHalfAwayFromZero(Double.POSITIVE_INFINITY) }
        assertFailsWith<ArithmeticException> { roundHalfAwayFromZero(1.0E19) }
        assertFailsWith<ArithmeticException> { roundHalfAwayFromZero(-1.0E19) }
    }

    @Test
    fun `allocation always sums to the total`() {
        // 100 over three equal lines is the classic case: 33 each leaves one
        // unit that has to land somewhere rather than vanish.
        val shares = allocateByLargestRemainder(100L, listOf(1000L, 1000L, 1000L))
        assertEquals(100L, shares.sum())
        assertEquals(listOf(34L, 33L, 33L), shares)
    }

    @Test
    fun `allocation is proportional to the weights`() {
        val shares = allocateByLargestRemainder(100L, listOf(3000L, 1000L))
        assertEquals(listOf(75L, 25L), shares)
        assertEquals(100L, shares.sum())
    }

    @Test
    fun `allocation gives leftovers to the largest remainders first`() {
        // Shares are 16.67, 16.67 and 16.67 of a 50 total across equal lines.
        val shares = allocateByLargestRemainder(50L, listOf(1L, 1L, 1L))
        assertEquals(50L, shares.sum())
        assertEquals(listOf(17L, 17L, 16L), shares)
    }

    @Test
    fun `allocation handles zero total, zero weights and empty lines`() {
        assertEquals(listOf(0L, 0L), allocateByLargestRemainder(0L, listOf(5L, 5L)))
        assertEquals(listOf(0L, 0L), allocateByLargestRemainder(10L, listOf(0L, 0L)))
        assertEquals(emptyList<Long>(), allocateByLargestRemainder(10L, emptyList()))
    }

    @Test
    fun `allocation never hands a line more than the total`() {
        val shares = allocateByLargestRemainder(7L, listOf(1L, 2L, 3L, 4L, 5L))
        assertEquals(7L, shares.sum())
        assertTrue(shares.all { it >= 0L })
    }

    @Test
    fun `allocation sums exactly across many awkward splits`() {
        for (total in 0L..40L) {
            for (lines in 1..7) {
                val weights = List(lines) { (it + 1).toLong() * 7L }
                assertEquals(
                    total,
                    allocateByLargestRemainder(total, weights).sum(),
                    "total $total over $lines lines"
                )
            }
        }
    }

    @Test
    fun `allocation rejects negative input`() {
        assertFailsWith<IllegalArgumentException> { allocateByLargestRemainder(-1L, listOf(1L)) }
        assertFailsWith<IllegalArgumentException> { allocateByLargestRemainder(1L, listOf(-1L)) }
    }
}
