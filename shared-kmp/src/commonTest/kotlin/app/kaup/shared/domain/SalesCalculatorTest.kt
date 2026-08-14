package app.kaup.shared.domain

import app.kaup.shared.models.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SalesCalculatorTest {

    private val calculator = SalesCalculator()

    /**
     * The ADR-019 contract. Every cart in this file goes through it, because a
     * worked example only proves the case it encodes and this is the property
     * that has to hold for all of them.
     */
    private fun assertReconciles(totals: SaleTotals, label: String = "") {
        assertEquals(
            totals.finalTotal,
            totals.subtotal - totals.discountTotal + totals.exclusiveTaxTotal,
            "finalTotal must equal subtotal - discountTotal + exclusiveTaxTotal $label"
        )
    }

    @Test
    fun `empty cart should return zero totals`() {
        val totals = calculator.calculateTotals(emptyList())
        assertEquals(Money.ZERO, totals.subtotal)
        assertEquals(Money.ZERO, totals.discountTotal)
        assertEquals(Money.ZERO, totals.taxTotal)
        assertEquals(Money.ZERO, totals.finalTotal)
        assertReconciles(totals)
    }

    @Test
    fun `basic line items without discounts or taxes`() {
        val items = listOf(
            LineItem("item1", Money(1000L), 2.0), // $20.00
            LineItem("item2", Money(500L), 1.0)   // $5.00
        )
        val totals = calculator.calculateTotals(items)
        assertEquals(Money(2500L), totals.subtotal)
        assertEquals(Money(0L), totals.discountTotal)
        assertEquals(Money(0L), totals.taxTotal)
        assertEquals(Money(2500L), totals.finalTotal)
        assertReconciles(totals)
    }

    @Test
    fun `fractional quantities calculate correctly`() {
        val items = listOf(
            LineItem("flour", Money(1000L), 1.5) // $10.00/kg * 1.5kg = $15.00
        )
        val totals = calculator.calculateTotals(items)
        assertEquals(Money(1500L), totals.subtotal)
        assertEquals(Money(1500L), totals.finalTotal)
        assertReconciles(totals)
    }

    @Test
    fun `item level percentage discount`() {
        val items = listOf(
            LineItem(
                "item", 
                Money(1000L), 
                1.0, 
                discounts = listOf(Discount.Percentage(0.10)) // 10% off
            )
        )
        val totals = calculator.calculateTotals(items)
        assertEquals(Money(1000L), totals.subtotal)
        assertEquals(Money(100L), totals.discountTotal) // 10% of 1000
        assertEquals(Money(900L), totals.finalTotal)
        assertReconciles(totals)
    }

    @Test
    fun `cart level fixed discount`() {
        val items = listOf(
            LineItem("item1", Money(1000L), 1.0),
            LineItem("item2", Money(1000L), 1.0)
        )
        val totals = calculator.calculateTotals(items, listOf(Discount.FixedAmount(Money(500L))))
        assertEquals(Money(2000L), totals.subtotal)
        assertEquals(Money(500L), totals.discountTotal)
        assertEquals(Money(1500L), totals.finalTotal)
        assertReconciles(totals)
    }

    @Test
    fun `exclusive tax increases final total`() {
        val items = listOf(
            LineItem(
                "item", 
                Money(1000L), 
                1.0, 
                taxes = listOf(TaxRate("vat", 0.10, isInclusive = false)) // 10% exclusive
            )
        )
        val totals = calculator.calculateTotals(items)
        assertEquals(Money(1000L), totals.subtotal)
        assertEquals(Money(100L), totals.taxTotal) // 10% of 1000
        assertEquals(Money(1100L), totals.finalTotal) // 1000 + 100
        assertReconciles(totals)
    }

    @Test
    fun `inclusive tax does not increase final total`() {
        val items = listOf(
            LineItem(
                "item", 
                Money(1100L), // Price including tax
                1.0, 
                taxes = listOf(TaxRate("vat", 0.10, isInclusive = true)) // 10% inclusive
            )
        )
        val totals = calculator.calculateTotals(items)
        assertEquals(Money(1100L), totals.subtotal)
        // Inclusive tax amount = 1100 - (1100 / 1.1) = 1100 - 1000 = 100
        assertEquals(Money(100L), totals.taxTotal) 
        assertEquals(Money(1100L), totals.finalTotal) // Customer still pays 1100
        assertEquals(Money.ZERO, totals.exclusiveTaxTotal)
        assertReconciles(totals)
    }

    // --- ADR-019 reconciliation -------------------------------------------

    @Test
    fun `cart discount that does not divide evenly still reconciles`() {
        // 10 units over three equal lines is one unit short of an even split,
        // and that unit has to land on a line rather than disappear. The old
        // implementation rounded each line's share independently, so the tax
        // base stopped matching the amount charged.
        val items = listOf(
            LineItem("a", Money(1000L), 1.0, taxes = listOf(TaxRate("vat", 0.07, false))),
            LineItem("b", Money(1000L), 1.0, taxes = listOf(TaxRate("vat", 0.07, false))),
            LineItem("c", Money(1000L), 1.0, taxes = listOf(TaxRate("vat", 0.07, false)))
        )
        val totals = calculator.calculateTotals(items, listOf(Discount.FixedAmount(Money(10L))))
        assertEquals(Money(3000L), totals.subtotal)
        assertEquals(Money(10L), totals.discountTotal)
        assertReconciles(totals)
    }

    @Test
    fun `the taxed base is exactly the discounted subtotal`() {
        // The per-line tax bases are internal, so this probes them with a 100%
        // exclusive tax: at that rate the tax total is the sum of the bases.
        // That sum has to equal subtotal - discountTotal, or the customer is
        // taxed on money they were never charged.
        //
        // This is the test the reconciliation invariant cannot do on its own.
        // The old implementation satisfied the top-level invariant while
        // reporting 2991 here against a charged base of 2990, because it
        // rounded each line's share of the cart discount independently.
        val items = List(3) { index ->
            LineItem(
                "line$index", Money(1000L), 1.0,
                taxes = listOf(TaxRate("probe", 1.0, isInclusive = false))
            )
        }
        val totals = calculator.calculateTotals(items, listOf(Discount.FixedAmount(Money(10L))))
        assertEquals(Money(2990L), totals.subtotal - totals.discountTotal)
        assertEquals(totals.subtotal - totals.discountTotal, totals.exclusiveTaxTotal)
        assertReconciles(totals)
    }

    @Test
    fun `reconciles across a spread of awkward carts`() {
        val prices = listOf(1L, 7L, 99L, 333L, 1050L)
        val quantities = listOf(1.0, 2.0, 0.333, 1.5)
        val rates = listOf(0.0, 0.06, 0.075, 0.2)

        for (price in prices) {
            for (quantity in quantities) {
                for (rate in rates) {
                    val items = listOf(
                        LineItem(
                            "a", Money(price), quantity,
                            discounts = listOf(Discount.Percentage(0.15)),
                            taxes = listOf(TaxRate("ex", rate, isInclusive = false))
                        ),
                        LineItem(
                            "b", Money(price * 3L), quantity,
                            taxes = listOf(TaxRate("in", rate, isInclusive = true))
                        ),
                        LineItem("c", Money(price + 1L), quantity)
                    )
                    val totals = calculator.calculateTotals(
                        items,
                        listOf(Discount.Percentage(0.13))
                    )
                    assertReconciles(totals, "price=$price qty=$quantity rate=$rate")
                }
            }
        }
    }

    @Test
    fun `two inclusive taxes on one line are extracted once, not twice`() {
        // 1200 at a combined 20% inclusive is 1000 net, so 200 of tax. Taken
        // separately each tax would extract against the full 1200 and the pair
        // would report roughly 214, more tax than the price contains.
        val items = listOf(
            LineItem(
                "item",
                Money(1200L),
                1.0,
                taxes = listOf(
                    TaxRate("vat", 0.10, isInclusive = true),
                    TaxRate("service", 0.10, isInclusive = true)
                )
            )
        )
        val totals = calculator.calculateTotals(items)
        assertEquals(Money(200L), totals.inclusiveTaxTotal)
        assertEquals(Money(1200L), totals.finalTotal)
        assertReconciles(totals)
    }

    @Test
    fun `inclusive tax is disclosed but never added to the total`() {
        val items = listOf(
            LineItem(
                "item", Money(1100L), 1.0,
                taxes = listOf(TaxRate("vat", 0.10, isInclusive = true))
            )
        )
        val totals = calculator.calculateTotals(items)
        assertEquals(Money(100L), totals.inclusiveTaxTotal)
        assertEquals(Money.ZERO, totals.exclusiveTaxTotal)
        assertEquals(totals.subtotal, totals.finalTotal)
        assertReconciles(totals)
    }

    @Test
    fun `discounts can never drive a line or a cart below zero`() {
        val items = listOf(
            LineItem(
                "item", Money(1000L), 1.0,
                discounts = listOf(Discount.FixedAmount(Money(5000L)))
            )
        )
        val totals = calculator.calculateTotals(items, listOf(Discount.FixedAmount(Money(9000L))))
        assertEquals(Money(1000L), totals.subtotal)
        assertEquals(Money(1000L), totals.discountTotal)
        assertEquals(Money.ZERO, totals.finalTotal)
        assertReconciles(totals)
    }

    // --- ADR-019 input validation ------------------------------------------

    @Test
    fun `rejects a non-positive or non-finite quantity`() {
        // A return is its own transaction under ADR-002, not a negative line.
        assertFailsWith<IllegalArgumentException> {
            calculator.calculateTotals(listOf(LineItem("a", Money(100L), -1.0)))
        }
        assertFailsWith<IllegalArgumentException> {
            calculator.calculateTotals(listOf(LineItem("a", Money(100L), 0.0)))
        }
        assertFailsWith<IllegalArgumentException> {
            calculator.calculateTotals(listOf(LineItem("a", Money(100L), Double.NaN)))
        }
    }

    @Test
    fun `rejects a percentage discount outside zero to one`() {
        assertFailsWith<IllegalArgumentException> {
            calculator.calculateTotals(
                listOf(LineItem("a", Money(100L), 1.0, discounts = listOf(Discount.Percentage(1.5))))
            )
        }
        assertFailsWith<IllegalArgumentException> {
            calculator.calculateTotals(
                listOf(LineItem("a", Money(100L), 1.0)),
                listOf(Discount.Percentage(-0.1))
            )
        }
    }

    @Test
    fun `rejects a negative unit price`() {
        assertFailsWith<IllegalArgumentException> {
            calculator.calculateTotals(listOf(LineItem("a", Money(-100L), 1.0)))
        }
    }

    @Test
    fun `rejects a negative fixed discount and a negative tax rate`() {
        assertFailsWith<IllegalArgumentException> {
            calculator.calculateTotals(
                listOf(LineItem("a", Money(100L), 1.0, discounts = listOf(Discount.FixedAmount(Money(-1L)))))
            )
        }
        assertFailsWith<IllegalArgumentException> {
            calculator.calculateTotals(
                listOf(LineItem("a", Money(100L), 1.0, taxes = listOf(TaxRate("bad", -0.1, false))))
            )
        }
    }
}
