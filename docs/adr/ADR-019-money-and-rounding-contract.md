# ADR-019: Money and Rounding Contract

- **Date**: 2026-08-14
- **Status**: Accepted
- **Deciders**: Core maintainer

---

## Context

`Money` is an integer minor-units value class, which is the right foundation,
but nothing above it was specified. `SalesCalculator` made four undocumented
choices, and each one was wrong in a way that only shows up on a real receipt:

1. **Rounding was implicit.** Every step called `roundToLong()`, which breaks
   ties towards positive infinity. A sale of `2.5` minor units rounds to `3`
   and a refund of `-2.5` rounds to `-2`, so refunding a line does not return
   what the line charged.

2. **Per-line tax bases did not sum to the cart total.** The cart discount was
   spread with `(postItemDiscount * cartDiscountRatio).roundToLong()`,
   independently per line. Rounding each share separately means the shares do
   not add up to the discount actually granted, so tax was charged on a base
   that never equalled the amount the customer was charged on.

3. **Multiple inclusive taxes double counted.** Each inclusive tax extracted
   itself from the full line value with `value / (1 + rate)`, so two inclusive
   taxes on one line each claimed to be the only tax in the price.

4. **Nothing was rejected.** Negative quantities, percentage rates above one
   and negative fixed discounts all produced totals rather than errors.

The consequence is that a receipt could not be reconciled: there was no stated
relationship between the subtotal, discount, tax and total lines, and no test
asserted one. This is the kind of defect that surfaces as a cash drawer that is
off by a few units at the end of the day, which is expensive to diagnose and
destroys trust in the till.

## Decision

### 1. Money stays integer minor units

`Money` wraps a `Long` of minor units, with no currency exponent stored on the
value. Currency and its exponent are a store-level setting.

### 2. Arithmetic is overflow-checked

`plus`, `minus`, `times` and `unaryMinus` throw `ArithmeticException` on
overflow rather than wrapping silently. A till that reports a negative total
because a `Long` wrapped is worse than a till that refuses the operation.

### 3. Rounding is half away from zero

One helper, `roundHalfAwayFromZero`, is the only place a fractional money value
becomes an integer. Ties round away from zero, so `2.5` becomes `3` and `-2.5`
becomes `-3`.

This is chosen over banker's rounding because a POS is customer-facing: the
half-up behaviour on positive amounts is what shoppers and cashiers expect, and
symmetry around zero is what makes a refund the exact negation of the sale that
produced it. Banker's rounding wins on long-run aggregate bias, which matters
for accounting ledgers, not for individual receipts that must be explainable at
the counter.

`roundToLong` is banned in money code, because its tie behaviour is asymmetric.

### 4. Subtotal is gross and as-priced

- `subtotal` is the sum of line extensions before any discount, at the price as
  entered. For a line carrying an inclusive tax, that price already contains the
  tax, and the subtotal therefore contains it too.
- `discountTotal` is every discount, item level and cart level.
- `inclusiveTaxTotal` is tax already contained in `subtotal`. It is reported for
  the receipt and is **not** added to the total again.
- `exclusiveTaxTotal` is tax added on top.

### 5. The reconciliation invariant

For every cart:

```
finalTotal == subtotal - discountTotal + exclusiveTaxTotal
```

This holds exactly, in integer minor units, with no tolerance. It is a property
test, not a worked example, and it is the contract every future change to
`SalesCalculator` must preserve.

`inclusiveTaxTotal` is deliberately absent from the invariant. It is a
disclosure of tax already inside `subtotal`, so adding it would charge it twice.

### 6. Cart-level amounts are allocated by largest remainder

Any cart-level amount spread across lines (today the cart discount, tomorrow a
cart-level tax or a rounding adjustment) is allocated with the largest-remainder
method: integer shares proportional to the line's net value, then the leftover
minor units handed out one at a time to the largest fractional remainders,
ties broken by line order.

This guarantees the allocated shares sum to exactly the amount being allocated,
which is what makes the per-line tax bases add up to the cart tax base. The
alternative, rounding each share independently, is what caused the defect.

### 7. Inclusive taxes are extracted once per line

Inclusive taxes on a line are summed into a single rate `R`, and the tax
contained in a line of value `v` is `v - round(v / (1 + R))`. Extracting each
tax separately against the full line value double counts.

### 8. Inputs are validated, and quantity is strictly positive

`calculateTotals` rejects, with `IllegalArgumentException`:

- a negative unit price
- a quantity that is not finite, or is zero or negative
- a percentage discount rate outside `0.0..1.0`
- a negative fixed discount amount
- a tax rate that is negative or not finite

A return is **not** a negative quantity on a sale. ADR-002 models it as its own
movement, and `MovementType` carries `RETURN` and `VOID` for exactly this. A
negative line would silently invert the discount clamps, which assume a
non-negative base.

## Alternatives Considered

| Alternative | Reason Rejected |
|---|---|
| Banker's rounding (half to even) | Better aggregate bias, but asymmetric for refunds and surprising at the counter: `2.5` to `2` and `3.5` to `4` is not explainable to a customer |
| Keep `Double` and round only at the end | Ordering of floating point additions changes the result, and the intermediate line values shown on the receipt would not be the ones summed |
| `BigDecimal` for money | Not in the Kotlin common standard library, so it would pull a multiplatform decimal dependency into `:shared-kmp` for a problem integer minor units already solves |
| Tolerance-based reconciliation (assert within one minor unit) | A tolerance is a place for real defects to hide, and it grows with cart size |
| Allocate the cart discount by rounding each line independently | The behaviour being fixed here |

## Consequences

- `SaleTotals` splits `taxTotal` into `inclusiveTaxTotal` and
  `exclusiveTaxTotal`. `taxTotal` remains as their sum so receipts can show one
  tax line, but the invariant is expressible only with the split.
- Callers must handle `IllegalArgumentException` from a malformed cart. There
  are no production callers yet, so the cost is paid now rather than later.
- Line quantity remains a `Double` in this ADR. Quantity precision is ADR-002's
  concern and is being moved to a scaled integer separately; the rounding rule
  here applies whatever the quantity type, because the line extension is
  rounded through the same helper.
- Per-line tax breakdown is computed internally but not yet returned. The
  receipt feature will expose it; the allocation is already exact, so that is an
  additive change.
