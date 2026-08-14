# ADR-020: Offline Conflict Resolution Policy

- **Date**: 2026-08-14
- **Status**: Accepted
- **Deciders**: Core maintainer

---

## Context

ADR-001 lets every device keep selling while offline and ADR-002 makes the
stock movement log append-only, which together guarantee that two devices will
sometimes describe the same shelf differently. Nothing said what to do about it.

`ConflictResolver` was named for the job but did not do it. It sorted movements
and flagged the single event that first drove stock below zero. It did not
deduplicate, did not report how deep the oversell went, and returned no
resolved timeline, so a caller had no way to act on the result.

The gap that matters most in practice is duplicates. A sync that fails after the
server commits but before the client records the acknowledgement is a normal
occurrence on a till on café wifi, and the client retries. Replaying the same
movement twice moves stock twice.

There is also a modelling question the log did not answer: nothing prevented a
negative `quantity`, so `OUT -3` and `IN 3` were two encodings of one fact, and
every consumer would have had to normalise before summing.

## Decision

### 1. The log is authoritative and append-only, so nothing is overwritten

There is no last-write-wins anywhere in this design. Conflict resolution
produces a **deterministic interpretation** of the events, never an edit to
them. Every device given the same set of movements derives the same timeline,
the same stock and the same violations, without talking to any other device.

### 2. Ordering is total and device-independent

Movements order by `timestamp`, then `deviceId`, then `id`. Clocks between
offline devices are not reliable enough for timestamp alone to be a total order,
and the two tie-breakers are values every device already agrees on.

This is an ordering for replay, not a claim about what really happened first.
The log is commutative for stock purposes, so the order only has to be
*agreed*, not *true*.

### 3. Duplicates are dropped by id, and content divergence is surfaced

The first occurrence in deterministic order is kept. A dropped duplicate that
differs in content from the kept one is reported separately: identical ids with
different bodies cannot happen through normal retry, so it means clock, id
generation or storage is broken, and a human needs to know rather than have one
version silently win.

### 4. Quantity is always positive, and direction carries the sign

`OUT -3` is rejected as a way of expressing `IN 3`. One fact gets one encoding.

### 5. Negative stock is reported, never rejected

ADR-002 is explicit that the customer in front of the cashier wins, so an
oversell is not blocked. Resolution reports **every** movement that leaves stock
negative, with the resulting level, and marks the first breach.

Reporting only the crossing event, which is what the code did, tells a manager
that one sale broke zero and hides that four more followed it. The depth of the
hole is the number needed to reconcile, and the first breach is the one needed
to explain it.

### 6. Stock is a scaled integer, refining ADR-002

ADR-002 wrote `quantity: BigDecimal`. The Kotlin common standard library has no
`BigDecimal`, and pulling in a multiplatform decimal library for this is a large
dependency for a small need. `Quantity` is therefore a `Long` of thousandths of
a unit, the same shape as `Money`.

Three decimal places is one gram on a kilogram scale and one millilitre on a
litre, which is the finest granularity retail scales report. Conversion from a
typed or scale-reported decimal rounds by the ADR-019 rule, so returning a
quantity is the exact negation of selling it.

`Double` accumulation is the specific thing being removed: ten movements of
`0.1` do not sum to `1.0` in binary floating point, and stock is a sum over a
log that only grows.

## Alternatives Considered

| Alternative | Reason Rejected |
|---|---|
| Last-write-wins on a mutable stock level | Rejected by ADR-002 already: one device's write silently destroys another's sale |
| Block the sale when stock would go negative | Rejected by ADR-001: the till must keep working, and the customer is already holding the item |
| Vector clocks or CRDT counters | The log is already a commutative event set; ordering by `(timestamp, deviceId, id)` gives determinism without per-device state to carry and sync |
| Deduplicate by content hash rather than id | Two genuinely separate sales of one unit, one second apart on the same device, hash identically. Dropping one loses a real sale |
| A multiplatform BigDecimal library | A dependency in `:shared-kmp` for precision that a scaled integer already provides exactly |
| Let quantity carry the sign | Two encodings for one fact, and every consumer has to normalise before summing |

## Consequences

- `ConflictResolver.resolve` returns a `ConflictResolution`: the deduplicated
  ordered timeline, the discarded duplicates, any divergent duplicates, the
  final stock and the violations. Callers get everything from one replay.
- `detectNegativeStockViolations` returns `StockViolation`, carrying the
  movement, the stock level after it and whether it was the first breach.
  Existing callers reading a bare `List<StockMovement>` must adapt; there are
  none yet.
- Divergent duplicates need somewhere to go. Until the notification work lands
  they are only returned; ADR-011's manager notification is the intended home.
- The `stock_movements.quantity` column changes from `REAL` to `INTEGER`. That
  is a schema change, and ADR-018 requires it to land before `v0.2-alpha` closes
  the destructive migration window.
- `LineItem.quantity` on the sale side stays a `Double` for now, as ADR-019
  noted. It is the last one, and moving it lets the line extension be computed
  in exact integer arithmetic.
