package app.kaup.shared.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the ADR-020 stock quantity type: exact accumulation, symmetric
 * conversion, and overflow that throws rather than wraps.
 */
class QuantityTest {

    @Test
    fun `ZERO has no thousandths`() {
        assertEquals(0L, Quantity.ZERO.thousandths)
        assertTrue(Quantity.ZERO.isZero)
        assertFalse(Quantity.ZERO.isNegative)
    }

    @Test
    fun `ofUnits scales whole units`() {
        assertEquals(Quantity(3000L), Quantity.ofUnits(3L))
        assertEquals(Quantity.ZERO, Quantity.ofUnits(0L))
    }

    @Test
    fun `of converts a decimal to thousandths`() {
        assertEquals(Quantity(1500L), Quantity.of(1.5))
        assertEquals(Quantity(1L), Quantity.of(0.001))
        assertEquals(Quantity(-2500L), Quantity.of(-2.5))
    }

    @Test
    fun `of rounds a value finer than a thousandth away from zero`() {
        assertEquals(Quantity(2L), Quantity.of(0.0015))
        assertEquals(Quantity(-2L), Quantity.of(-0.0015))
        assertEquals(Quantity(1L), Quantity.of(0.0014))
    }

    @Test
    fun `returning a quantity is the exact negation of selling it`() {
        for (units in listOf(0.001, 0.5, 1.5, 2.5, 99.999, 1234.567)) {
            assertEquals(-Quantity.of(units), Quantity.of(-units), "asymmetry at $units")
        }
    }

    @Test
    fun `summing tenths is exact`() {
        // The defect this type removes: as Double this sum is
        // 0.9999999999999999, and the error grows with the length of the log.
        var total = Quantity.ZERO
        repeat(10) { total += Quantity.of(0.1) }
        assertEquals(Quantity.ofUnits(1L), total)
    }

    @Test
    fun `summing thirds of a kilo stays exact at gram precision`() {
        var total = Quantity.ZERO
        repeat(3) { total += Quantity.of(0.333) }
        assertEquals(Quantity(999L), total)
    }

    @Test
    fun `plus minus and times behave`() {
        assertEquals(Quantity(1500L), Quantity(1000L) + Quantity(500L))
        assertEquals(Quantity(500L), Quantity(1000L) - Quantity(500L))
        assertEquals(Quantity(-500L), Quantity(500L) - Quantity(1000L))
        assertEquals(Quantity(3000L), Quantity(1000L) * 3L)
    }

    @Test
    fun `stock can go negative, because an oversell is reported not rejected`() {
        val oversold = Quantity.ofUnits(1L) - Quantity.ofUnits(4L)
        assertTrue(oversold.isNegative)
        assertEquals(Quantity.ofUnits(-3L), oversold)
    }

    @Test
    fun `compareTo orders by thousandths`() {
        assertTrue(Quantity.of(0.5) < Quantity.of(1.0))
        assertTrue(Quantity.of(1.0) > Quantity.of(0.5))
        assertEquals(0, Quantity.of(1.0).compareTo(Quantity(1000L)))
    }

    @Test
    fun `toDouble round trips a representable quantity`() {
        assertEquals(1.5, Quantity.of(1.5).toDouble())
        assertEquals(-0.25, Quantity.of(-0.25).toDouble())
    }

    @Test
    fun `arithmetic throws rather than wrapping`() {
        assertFailsWith<ArithmeticException> { Quantity(Long.MAX_VALUE) + Quantity(1L) }
        assertFailsWith<ArithmeticException> { Quantity(Long.MIN_VALUE) - Quantity(1L) }
        assertFailsWith<ArithmeticException> { Quantity(Long.MAX_VALUE) * 2L }
        assertFailsWith<ArithmeticException> { -Quantity(Long.MIN_VALUE) }
        assertFailsWith<ArithmeticException> { Quantity.ofUnits(Long.MAX_VALUE) }
    }

    @Test
    fun `of rejects a value that is not finite`() {
        assertFailsWith<IllegalArgumentException> { Quantity.of(Double.NaN) }
        assertFailsWith<IllegalArgumentException> { Quantity.of(Double.POSITIVE_INFINITY) }
    }
}
