# Product Requirements Document (PRD)

- **Version**: 1.0
- **Date**: 2026-03-14
- **Status**: Active
- **Maintainer**: Core maintainer

---

## Table of Contents

- [Problem Statement](#problem-statement)
- [Target Users](#target-users)
- [Non-Goals](#non-goals)
- [Success Metrics for v1.0](#success-metrics-for-v10)
- [Functional Requirements](#functional-requirements)
- [Non-Functional Requirements](#non-functional-requirements)
- [Constraints](#constraints)
- [Dependencies](#dependencies)

---

## Problem Statement

Small and medium businesses need a POS and ERP system that:

- Works **100% offline** — a lost connection must never interrupt a sale
- Runs **natively on Android** — the device every store owner already has
- Is **genuinely open source** — no vendor lock-in, no forced subscriptions,
  no cloud dependency
- Is **globally usable** — no country-specific defaults; every tax rate,
  currency, and compliance requirement is user-configured
- **Scales with the business** — from a single device with no infrastructure
  to a multi-device store with a self-hosted or managed cloud server

Existing open-source POS solutions (OSPOS, UniCenta) are web-first or
desktop-first and do not provide a native Android experience. Existing Android
POS apps are proprietary, subscription-based, or cloud-dependent. No solution
in the market satisfies all five requirements simultaneously.

---

## Target Users

### Persona 1 — The Solo Store Owner
A small retail shop, market stall, or street food vendor. Non-technical.
Runs everything themselves. Has one Android device. Needs to be selling
within minutes of installing the app. Has no interest in servers or
infrastructure. Cares about: speed of checkout, receipt printing, knowing
their stock levels, and end-of-day sales summary.

### Persona 2 — The Store with Staff
A small store with 2–5 employees. One or two Android devices. A manager and
one or more cashiers. Manager needs to control what cashiers can and cannot do.
Needs to see reports without handing the device to staff. Cares about:
role-based access, manager approval for sensitive actions, shift management,
and staff accountability.

### Persona 3 — The F&B Operator
A café, small restaurant, or food stall. Needs table management and order
routing in addition to POS. May have a kitchen that needs to see orders.
Cares about: table assignment, order status, split bills, and quick item lookup
during a rush.

### Persona 4 — The IT-Savvy Operator
A technically confident store owner or a developer setting up the app for a
client. Wants full data ownership via a self-hosted server. Wants to
contribute improvements back to the project. Cares about: self-hosting
documentation, open API contracts, GPL v3 compliance, and F-Droid availability.

---

## Non-Goals

The following are explicitly out of scope for v1.0 and will not be designed
for, prototyped, or partially implemented:

- Full double-entry accounting (General Ledger, AP/AR, bank reconciliation)
- Financial statements (P&L, Balance Sheet, Cash Flow)
- HR and payroll management
- Manufacturing (BOM, MRP, work orders)
- Quality control, asset management, project management
- CRM pipeline and e-commerce integration
- Web dashboard (Compose for Web — post Year 1)
- iOS app
- Kitchen Display System (KDS)
- Country-specific tax defaults or payment integration defaults
- Hardware peripherals beyond Bluetooth ESC/POS printers (cash drawers,
  weight scales, customer-facing displays — post Year 1)

---

## Success Metrics for v1.0

| Metric | Target |
|---|---|
| Time from install to first completed sale | ≤ 10 minutes for a non-technical user |
| Offline operation | 100% of features functional with no internet |
| Backup and restore | Completes in ≤ 30 seconds for 12 months of transaction data |
| Base APK size | ≤ 30 MB |
| Minimum Android version | Android 8.0 (API 26) as specified; the build currently ships `minSdk = 24` (see Compatibility) |
| POS screen load time from Lock Screen | ≤ 300 ms |
| F-Droid compliance | Zero proprietary trackers, zero hard FCM dependency |
| Crash-free session rate (alpha target) | ≥ 95% |

---

## Functional Requirements

All functional requirements are documented and prioritized in the MoSCoW:

→ See [`ROADMAP.md`](../../ROADMAP.md)

Module-level requirements are documented per module:

→ See [`/docs/modules.md`](../modules.md)

User stories are tracked as GitHub Issues labeled `user-story` and grouped
under GitHub Milestones (Epics):

→ See [GitHub Projects board](../../projects)

---

## Non-Functional Requirements

### Offline Operation
All user-facing operations — completing a sale, adding a product, issuing a
refund, generating a receipt, viewing reports — must function with zero network
activity. Network connectivity must never be in the critical path of any
operation. See ADR-001.

### Security
- All manager authorization uses cryptographically signed HOTP codes (ADR-005)
- All device sessions are PIN-protected with optional biometric enrollment
- All backup files are AES-encrypted before writing to storage
- All secrets (HOTP keys, API tokens) are stored in Android Keystore
- The RBAC permission system enforces access control locally without server
  validation (ADR-009)
- Restricted UI elements are hidden, not merely disabled

### Performance
- POS screen must be responsive with a cart of up to 100 line items
- Report queries must complete in ≤ 2 seconds for up to 24 months of data
- Barcode scan to item added to cart must complete in ≤ 500 ms
- App cold start to Lock Screen must complete in ≤ 2 seconds

### Storage
- Base APK: ≤ 30 MB
- Media files (receipts, refund photos) are stored on the local filesystem;
  Room stores paths only — media never bloats the Room database
- Housekeeping menu allows user to safely delete synced media files with a
  clear size summary before deletion

### Compatibility
- Minimum: Android 8.0 (API 26)

  > **Unresolved (#187).** Every module in the build declares `minSdk = 24`,
  > which is Android 7.0, two API levels below this requirement. The developer
  > documentation has been corrected to describe what the build actually does,
  > but this row is a product decision and is left as written until someone
  > rules on it. Either the build rises to 26, dropping Android 7.0 devices, or
  > this requirement drops to 24 and the extra reach becomes intentional. It
  > should not stay ambiguous: the gap decides whether a whole class of cheap
  > retail hardware is supported.

- Target: latest stable Android SDK
- Must function on low-spec devices (2 GB RAM, budget chipsets) commonly
  used in SME retail environments

### Accessibility
- Full TalkBack support across all screens
- All text respects system dynamic font scaling
- Contrast ratios must meet WCAG AA minimum, targeting legibility in direct
  sunlight

### Privacy
- Zero telemetry by default
- Analytics and crash reporting are opt-in only; user is shown explicit
  pros/cons before enabling
- No data is ever sent to any third-party service without explicit user
  consent and configuration
- The app functions fully without any Google account or Google Services

### Internationalisation
- All user-facing strings are externalized in `strings.xml` with clear
  naming conventions to facilitate community translation
- No country-specific defaults for tax rates, currencies, or payment methods
- Currency is a required field during onboarding with no pre-selected default
- Date, time, and number formatting respects the device locale

### Distribution
- F-Droid and IzzyOnDroid compliant from v0.2-alpha (ADR-013)
- Google Play Store from v1.0
- Direct APK download from GitHub Releases from v0.1-alpha

---

## Constraints

| Constraint | Source |
|---|---|
| GPL v3 — all dependencies must be GPL-compatible | ADR-006 |
| No proprietary trackers | F-Droid compliance (ADR-013) |
| No hard FCM dependency | F-Droid compliance (ADR-013) |
| No hardcoded API keys in source | F-Droid compliance (ADR-013) |
| All business logic in `:shared-kmp` | ADR-003 |
| Feature modules must not depend on other feature modules | ADR-008 |
| All writes go to Room first — never directly to server | ADR-001 |

---

## Dependencies

| Dependency | Purpose | License |
|---|---|---|
| Jetpack Compose | Android UI framework | Apache 2.0 |
| Room | Local SQLite database | Apache 2.0 |
| WorkManager | Background sync scheduling | Apache 2.0 |
| Kotlin Multiplatform | Shared domain logic | Apache 2.0 |
| Ktor (client + server) | HTTP client and self-hosted server | Apache 2.0 |
| Kotlin Coroutines | Async programming | Apache 2.0 |
| Kotlin Serialization | JSON serialization | Apache 2.0 |
| DataStore | Feature flags and settings persistence | Apache 2.0 |
| CameraX | Barcode scanning | Apache 2.0 |
| ESCPOS-ThermalPrinter-Android | Bluetooth receipt printing | MIT |
| totp-kt | HOTP/TOTP code generation | MIT |
| Android-Room-Database-Backup | Encrypted backup and restore | MIT |
| Hilt | Dependency injection | Apache 2.0 |
| Coil | Image loading and thumbnails | Apache 2.0 |
| MockK | Unit test mocking | Apache 2.0 |
| Kotest | Unit test assertions | Apache 2.0 |
