# ADR-002: Event-Sourced Inventory

- **Date**: 2026-03-14
- **Status**: Accepted
- **Deciders**: Core maintainer

---

## Context

In a multi-device offline-first environment, two devices can both sell the same item
while disconnected. If inventory is stored as a mutable stock level (e.g., `stock = 3`),
two devices decrementing simultaneously produce an unresolvable conflict — it is
impossible to determine whose write is correct after the fact.

Additionally, blocking a sale when stock reads zero would violate the uninterrupted
operation requirement, since the displayed stock level may be stale due to an unsynced
receiving record on another device.

## Decision

Inventory is tracked as an **immutable log of stock movement events**, not as a mutable
stock level. Every stock-affecting action (sale, receiving, transfer, manual adjustment)
writes a `StockMovement` record:

```kotlin
StockMovement(
    itemId        : String,
    type          : SALE | RECEIVING | TRANSFER | ADJUSTMENT,
    quantity      : BigDecimal,   // refined to a scaled integer by ADR-020
    direction     : IN | OUT,
    transactionId : String,
    deviceId      : String,
    timestamp     : Instant,
    syncStatus    : PENDING | SYNCED | FAILED
)
```

The current stock level for display is computed by aggregating movements:
`currentStock = Σ(IN quantities) - Σ(OUT quantities)`

ADR-020 refines `quantity` to `Quantity`, a scaled integer of thousandths of a
unit: the Kotlin common standard library has no `BigDecimal`, and this sum runs
over a log that only grows, so binary floating point would drift. It also
settles what happens when two devices disagree, which this ADR leaves open.

A denormalized `currentStock` column is maintained on each write for display
performance. The movement log remains the authoritative source of truth.

When movements sync to the server, the server replays all events to compute the
authoritative stock level. If the result is negative, the server **flags it but does
not reject the transactions**. Negative stock surfaces as a manager notification and
can be treated as a pre-order (`fulfillment_status = PENDING_STOCK`).

## Alternatives Considered

| Alternative | Reason Rejected |
|---|---|
| Mutable stock level with last-write-wins | Silent data loss — one device's write silently overwrites another |
| Block sales when stock is zero offline | Violates the uninterrupted operation requirement |
| Server-side stock validation before completing a sale | Requires connectivity — incompatible with offline-first |

## Consequences

**Positive:**
- Conflict resolution is deterministic — server replays all events in timestamp order
- Full audit trail of every stock change, who made it, on which device, and when
- Negative stock is surfaced to the manager, not silently corrupted
- Pre-order workflow emerges naturally from the `fulfillment_status` field
- Supports future accounting integrations — COGS maps directly to OUT movements

**Negative:**
- Movement log grows indefinitely — an archival policy is needed for old synced records
- Denormalized `currentStock` must be kept in sync with the movement log on every write
