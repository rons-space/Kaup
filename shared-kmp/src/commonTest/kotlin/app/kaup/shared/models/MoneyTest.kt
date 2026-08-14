package app.kaup.shared.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the [Money] value class, the integer-minor-units type that all POS
 * money math is built on. These pin the arithmetic and ordering behaviour,
 * including the ADR-019 rule that overflow throws instead of wrapping.
 */
class MoneyTest {

    @Test
    fun `ZERO has no minor units`() {
        assertEquals(0L, Money.ZERO.minorUnits)
    }

    @Test
    fun `plus adds minor units`() {
        assertEquals(Money(1500L), Money(1000L) + Money(500L))
    }

    @Test
    fun `minus subtracts minor units and can go negative`() {
        assertEquals(Money(500L), Money(1000L) - Money(500L))
        assertEquals(Money(-500L), Money(500L) - Money(1000L))
    }

    @Test
    fun `times by an Int scales the amount`() {
        assertEquals(Money(3000L), Money(1000L) * 3)
    }

    @Test
    fun `times by a Long scales the amount`() {
        assertEquals(Money(3000L), Money(1000L) * 3L)
    }

    @Test
    fun `times by zero yields ZERO`() {
        assertEquals(Money.ZERO, Money(1234L) * 0)
    }

    @Test
    fun `adding ZERO is an identity`() {
        assertEquals(Money(1234L), Money(1234L) + Money.ZERO)
    }

    @Test
    fun `compareTo orders by minor units`() {
        assertTrue(Money(500L) < Money(1000L))
        assertTrue(Money(1000L) > Money(500L))
        assertEquals(0, Money(1000L).compareTo(Money(1000L)))
        assertTrue(Money(-1L) < Money.ZERO)
    }

    @Test
    fun `equality is by minor units`() {
        assertEquals(Money(1000L), Money(1000L))
        assertFalse(Money(1000L) == Money(1001L))
    }

    @Test
    fun `plus throws rather than wrapping past the maximum`() {
        assertFailsWith<ArithmeticException> { Money(Long.MAX_VALUE) + Money(1L) }
    }

    @Test
    fun `minus throws rather than wrapping past the minimum`() {
        assertFailsWith<ArithmeticException> { Money(Long.MIN_VALUE) - Money(1L) }
    }

    @Test
    fun `times throws rather than wrapping`() {
        assertFailsWith<ArithmeticException> { Money(Long.MAX_VALUE) * 2 }
        assertFailsWith<ArithmeticException> { Money(Long.MIN_VALUE) * -1L }
    }

    @Test
    fun `unary minus negates and refuses the one value with no counterpart`() {
        assertEquals(Money(-1000L), -Money(1000L))
        assertEquals(Money(1000L), -Money(-1000L))
        assertFailsWith<ArithmeticException> { -Money(Long.MIN_VALUE) }
    }

    @Test
    fun `arithmetic near the bounds still works when it fits`() {
        assertEquals(Money(Long.MAX_VALUE), Money(Long.MAX_VALUE - 1L) + Money(1L))
        assertEquals(Money(Long.MIN_VALUE), Money(Long.MIN_VALUE + 1L) - Money(1L))
    }
}
