package app.kaup.shared.domain

import app.kaup.shared.models.Discount
import app.kaup.shared.models.LineItem
import app.kaup.shared.models.Money
import app.kaup.shared.models.SaleTotals
import app.kaup.shared.models.TaxRate
import app.kaup.shared.models.addExact
import app.kaup.shared.models.allocateByLargestRemainder
import app.kaup.shared.models.roundHalfAwayFromZero
import app.kaup.shared.models.subtractExact

/**
 * Turns a cart into [SaleTotals] under the ADR-019 money contract.
 *
 * The contract, in one line:
 *
 * ```
 * finalTotal == subtotal - discountTotal + exclusiveTaxTotal
 * ```
 *
 * Holding that exactly is the whole job. It is easy to lose by rounding the
 * same quantity twice in different places, which is what the previous
 * implementation did when it spread the cart discount over lines with an
 * independently rounded ratio per line.
 */
class SalesCalculator {

    fun calculateTotals(
        items: List<LineItem>,
        cartDiscounts: List<Discount> = emptyList()
    ): SaleTotals {
        items.forEach { validate(it) }
        cartDiscounts.forEach { validateDiscount(it) }

        // 1. Line extensions at the price as entered, before any discount.
        val lineGross = items.map { item ->
            roundHalfAwayFromZero(item.unitPrice.minorUnits.toDouble() * item.quantity)
        }

        // 2. Item discounts, each clamped so a line can never go negative.
        val itemDiscounts = items.mapIndexed { index, item ->
            var sum = 0L
            for (discount in item.discounts) {
                sum = addExact(sum, discountAmount(discount, lineGross[index]))
            }
            sum.coerceIn(0L, lineGross[index])
        }

        val lineNet = lineGross.mapIndexed { index, gross ->
            subtractExact(gross, itemDiscounts[index])
        }
        var cartNet = 0L
        for (net in lineNet) cartNet = addExact(cartNet, net)

        // 3. Cart discounts, clamped against the cart rather than any one line.
        var cartDiscount = 0L
        for (discount in cartDiscounts) {
            cartDiscount = addExact(cartDiscount, discountAmount(discount, cartNet))
        }
        cartDiscount = cartDiscount.coerceIn(0L, cartNet)

        // 4. Spread the cart discount so the shares sum to exactly what was
        //    granted. This is what keeps the per-line tax bases adding up to
        //    the cart tax base.
        val allocatedCartDiscount = allocateByLargestRemainder(cartDiscount, lineNet)

        // 5. Tax, charged on what the customer is actually charged for.
        var inclusiveTax = 0L
        var exclusiveTax = 0L
        items.forEachIndexed { index, item ->
            val taxable = subtractExact(lineNet[index], allocatedCartDiscount[index])

            // Inclusive taxes are extracted once, against their combined rate.
            // Extracting each separately against the full line value would have
            // every tax claim to be the only one inside the price.
            val inclusiveRate = item.taxes.filter { it.isInclusive }.sumOf { it.rate }
            if (inclusiveRate > 0.0) {
                val net = roundHalfAwayFromZero(taxable / (1.0 + inclusiveRate))
                inclusiveTax = addExact(inclusiveTax, subtractExact(taxable, net))
            }

            for (tax in item.taxes) {
                if (!tax.isInclusive) {
                    exclusiveTax = addExact(
                        exclusiveTax,
                        roundHalfAwayFromZero(taxable.toDouble() * tax.rate)
                    )
                }
            }
        }

        var subtotal = 0L
        for (gross in lineGross) subtotal = addExact(subtotal, gross)
        var discountTotal = cartDiscount
        for (discount in itemDiscounts) discountTotal = addExact(discountTotal, discount)

        return SaleTotals(
            subtotal = Money(subtotal),
            discountTotal = Money(discountTotal),
            inclusiveTaxTotal = Money(inclusiveTax),
            exclusiveTaxTotal = Money(exclusiveTax),
            // Equal to subtotal - discountTotal + exclusiveTax by construction:
            // cartNet is subtotal minus the item discounts, and cartDiscount is
            // the rest of discountTotal.
            finalTotal = Money(addExact(subtractExact(cartNet, cartDiscount), exclusiveTax))
        )
    }

    private fun discountAmount(discount: Discount, base: Long): Long = when (discount) {
        is Discount.Percentage -> roundHalfAwayFromZero(base.toDouble() * discount.rate)
        is Discount.FixedAmount -> discount.amount.minorUnits
    }

    private fun validate(item: LineItem) {
        // Without this the discount clamp below would coerce into an empty
        // range and throw something unreadable about coercion instead of
        // naming the bad line.
        require(item.unitPrice.minorUnits >= 0L) {
            "Line price must not be negative, was ${item.unitPrice.minorUnits} for ${item.productId}"
        }
        require(item.quantity.isFinite()) {
            "Line quantity must be finite, was ${item.quantity} for ${item.productId}"
        }
        // A return is its own transaction under ADR-002, not a negative sale
        // line. A negative line would invert the discount clamps below, which
        // assume a non-negative base.
        require(item.quantity > 0.0) {
            "Line quantity must be positive, was ${item.quantity} for ${item.productId}"
        }
        item.discounts.forEach { validateDiscount(it) }
        item.taxes.forEach { validateTax(it) }
    }

    private fun validateDiscount(discount: Discount) {
        when (discount) {
            is Discount.Percentage -> require(discount.rate.isFinite() && discount.rate in 0.0..1.0) {
                "Percentage discount rate must be between 0 and 1, was ${discount.rate}"
            }
            is Discount.FixedAmount -> require(discount.amount.minorUnits >= 0L) {
                "Fixed discount must not be negative, was ${discount.amount.minorUnits}"
            }
        }
    }

    private fun validateTax(tax: TaxRate) {
        require(tax.rate.isFinite() && tax.rate >= 0.0) {
            "Tax rate must be finite and non-negative, was ${tax.rate} for ${tax.id}"
        }
    }
}
