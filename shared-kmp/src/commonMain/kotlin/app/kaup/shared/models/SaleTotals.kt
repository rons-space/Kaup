package app.kaup.shared.models

/**
 * The totals for one cart, in integer minor units, satisfying the ADR-019
 * reconciliation invariant:
 *
 * ```
 * finalTotal == subtotal - discountTotal + exclusiveTaxTotal
 * ```
 *
 * exactly, with no tolerance.
 *
 * @property subtotal every line extension before discount, at the price as
 *   entered. A line priced with an inclusive tax carries that tax here.
 * @property discountTotal every discount, item level and cart level.
 * @property inclusiveTaxTotal tax already contained in [subtotal]. Disclosed
 *   for the receipt and absent from the invariant, because adding it to the
 *   total would charge it a second time.
 * @property exclusiveTaxTotal tax added on top of the discounted subtotal.
 * @property finalTotal what the customer pays.
 */
data class SaleTotals(
    val subtotal: Money,
    val discountTotal: Money,
    val inclusiveTaxTotal: Money,
    val exclusiveTaxTotal: Money,
    val finalTotal: Money
) {
    /** Both kinds of tax, for receipts that show a single tax line. */
    val taxTotal: Money get() = inclusiveTaxTotal + exclusiveTaxTotal
}
