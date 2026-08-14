# ADR-015: Payment Gateway Architecture — Capture-Only Default with Pluggable Adapters

- **Date**: 2026-03-22
- **Status**: Accepted
- **Deciders**: Core maintainer

---

## Context

Payment processing is inherently country-specific. Indonesian stores need QRIS.
UK stores use SumUp or Square. US stores use Square or PayPal. No single payment
gateway covers all target markets, and integrating any gateway inside the app
introduces PCI-DSS compliance obligations, legal liability, and a proprietary
dependency that violates the open-source philosophy of the project. Attempting to
ship even one gateway integration in v1 would delay the release significantly and
exclude all stores in countries that gateway does not serve.

At the same time, stores using a separate physical card terminal (which most small
stores already own) simply need the app to record that a card payment occurred —
they do not need the app to process the payment itself.

## Decision

Kaup ships with **capture-only** as the built-in default payment recording
mechanism. Gateway integrations are supported via a pluggable `PaymentGateway`
interface and are explicitly designated as community contribution territory.

**Capture-only behavior:**
The POS register shows a "Card" payment button (and any other non-cash methods
configured by the store owner). When the cashier selects "Card", the app records
the transaction as `payment_method = CARD` in Room. No network call is made. The
physical card terminal processes the actual payment independently. The cashier
confirms the terminal approved the payment, then completes the sale in Kaup. This
works 100% offline and requires zero configuration.

**Pluggable `PaymentGateway` interface:**

```kotlin
interface PaymentGateway {
    val name: String               // display name, e.g. "QRIS", "SumUp"
    val countryCode: String        // ISO 3166-1 alpha-2, e.g. "ID", "GB"
    suspend fun initiate(
        amount     : BigDecimal,
        currency   : String,
        reference  : String
    ): PaymentResult
    suspend fun refund(
        originalReference : String,
        amount            : BigDecimal
    ): RefundResult
    fun isConfigured(): Boolean
}
```

The interface is defined in `:shared-kmp/sync-contracts`. Concrete implementations
are community-contributed adapter modules. The core app never references a concrete
adapter — it only calls the interface. If no gateway is configured,
`CaptureOnlyGateway` (the built-in no-op) is active.

**Supported payment methods (configured in Settings):**
The store owner configures which payment methods appear on the POS screen. Built-in
types require no adapter:

| Method | Built-in | Requires Adapter |
|---|---|---|
| Cash | ✅ | — |
| Card (capture-only) | ✅ | — |
| Split (cash + card) | ✅ | — |
| QRIS | ❌ | Community adapter |
| SumUp | ❌ | Community adapter |
| Square | ❌ | Community adapter |
| GoPay / OVO / Dana | ❌ | Community adapter |
| Custom (store-defined label) | ✅ | — |

**Community adapter contribution path:**
A contributor building a QRIS adapter, for example:
1. Creates a new module `:payment-qris` implementing `PaymentGateway`
2. Adds a Hilt binding in `:android-app` behind a feature flag
3. Adds configuration UI in `:feature-settings` for API keys and credentials
4. Submits a PR — the adapter is reviewed independently of core app changes

All credentials supplied by gateway adapters are stored in Android Keystore.
No API key is ever hardcoded in source — F-Droid compliance requires this.

## Alternatives Considered

| Alternative | Reason Rejected |
|---|---|
| Integrate one gateway (e.g., Stripe) as built-in | Country-specific, excludes most target markets, PCI-DSS liability |
| No payment method recording at all | Loss of payment method breakdown in reports |
| Require gateway for card payments | Blocks stores that use standalone terminals — majority of small stores |

## Consequences

**Positive:**
- Works for every store in every country on day one — no gateway needed
- Community contributors can build country-specific adapters without touching core
- Zero PCI-DSS liability for the core project
- Payment method breakdown in reports works correctly for all built-in types
- F-Droid compliant — no proprietary gateway SDK in the core APK

**Negative:**
- Fully automated card payment (no separate terminal needed) requires a community
  adapter — not available at launch
- Store owners expecting integrated card processing must be clearly informed
  during onboarding that card = capture-only unless a gateway adapter is installed
- Gateway adapters must be maintained by their contributors — a QRIS adapter that
  breaks after an API change needs its contributor to fix it