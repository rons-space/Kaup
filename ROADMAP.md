# Kaup — Product Roadmap & MoSCoW Prioritization

- **Version**: 1.1
- **Date**: 2026-08-14
- **Status**: Active
- **Methodology**: Kanban — see [ADR-012](docs/adr/ADR-012-kanban-development-methodology.md)

> **How this file relates to the issue tracker.** Issues #1-#146 were generated
> from the checkboxes below, one per box. Items added after that generation run
> are filed by hand and carry the `roadmap-gap` label, so re-running any
> generator over this file would create duplicates. Remediation work from
> [`docs/CODE_REVIEW_MERGED.md`](docs/CODE_REVIEW_MERGED.md) is tracked under the
> `code-review` label and is not listed here. MoSCoW priority is mirrored by the
> `must-have`, `should-have` and `could-have` labels, and by milestones.

---

## Table of Contents

- [Milestone Overview](#milestone-overview)
- [Must Have — v0.1-alpha](#must-have--v01-alpha)
- [Should Have — v0.2-alpha](#should-have--v02-alpha)
- [v1.0 Release Requirements](#v10-release-requirements)
- [Could Have — v1.x](#could-have--v1x)
- [Won't Have — Post Year 1](#wont-have--post-year-1)
- [Testing Strategy](#testing-strategy)
- [Resolved Decisions](#resolved-decisions)
- [Open Questions](#open-questions)

---

## Milestone Overview

| Milestone | Scope | Distribution |
|---|---|---|
| `v0.1-alpha` | Must Have core — POS, Inventory, Auth, Sync Engine | GitHub Releases only |
| `v0.2-alpha` | Should Have complete — first public release | GitHub Releases + IzzyOnDroid |
| `v1.0` | All Must Have + Should Have stable and documented | GitHub + IzzyOnDroid + F-Droid + Play Store |
| `v1.x` | Could Have — community contributions, incremental releases | All channels |
| `v2.0` | Post Year 1 — Web Dashboard, deep ERP layer | All channels |

Releases ship when the milestone is genuinely complete — not on a calendar date.
See [ADR-012](docs/adr/ADR-012-kanban-development-methodology.md).

---

## Must Have — v0.1-alpha

These items are non-negotiable for the app to be minimally functional.
Nothing in v0.2-alpha is started until every Must Have is stable.

### Foundation (all modules depend on this — built first)

- [ ] `:shared-kmp` module scaffold — domain models, DTOs, enums
- [ ] `SalesCalculator` — line items, discounts, tax (inclusive/exclusive), totals
- [ ] `TaxResolver` — per-item tax rate application
- [ ] `InventoryEngine` — current stock computed from movement event log
- [ ] `ConflictResolver` — event replay for multi-device conflict resolution
- [ ] `HOTPGenerator` — HOTP code generation and validation (RFC 4226)
- [ ] `SyncBackend` interface + `NoSyncBackend` implementation
- [ ] `NotificationBackend` interface + `LocalNotificationBackend` implementation
- [ ] `UpdateChecker` interface + `NoOpUpdateChecker` + `GitHubUpdateChecker`
- [ ] `PaymentGateway` interface + `CaptureOnlyGateway` built-in default
      ([ADR-015](docs/adr/ADR-015-payment-gateway-architecture.md))
- [ ] `Permission` catalogue constants + `RoleDefaults`
      ([ADR-009](docs/adr/ADR-009-rbac-permission-system.md))
- [ ] `:core-data` — Room database setup, all entity definitions, all DAOs
- [ ] `:core-data`: `Location` entity, `location_id` on every location-aware
      table, default location seeded in a Room `onCreate` callback
      ([ADR-016](docs/adr/ADR-016-multi-location-schema.md))
- [ ] `:core-data`: `PrinterService` abstraction (`printReceipt()`,
      `printLabel()`), driver bound in `:android-app`
- [ ] `:core-data`: backup and restore file I/O (AES, SAF) and media path
      helpers
- [ ] `:core-data`: schema JSON export, `DATABASE_VERSION` discipline, and the
      destructive-migration guardrail
      ([ADR-018](docs/adr/ADR-018-room-migration-strategy.md) Phase 1)
- [ ] Device identity: `deviceId` written on every stock movement, device
      associated with a location
      ([ADR-002](docs/adr/ADR-002-event-sourced-inventory.md))
- [ ] DataStore settings persistence + `FeatureFlags` object, conditional
      navigation graph registration, flag-gated DAO and WorkManager init
      ([ADR-007](docs/adr/ADR-007-feature-flag-module-system.md))
- [ ] `:core-ui` — Material 3 Expressive theme, typography, shape system,
      base Compose components, `ManagerApprovalOverlay` shell
- [ ] `:core-network` — WorkManager scaffold, sync queue management,
      `NoSyncBackend` wired via Hilt
- [ ] `:android-app` shell — navigation host, Hilt setup, three build flavors
      (`github`, `fdroid`, `playstore`), `NavigationSuiteScaffold`

### feature-auth

- [ ] Lock Screen UI — staff profile card grid, adaptive layout
- [ ] PIN entry and validation
- [ ] Session management — `SessionManager` singleton, `Set<Permission>` loading
- [ ] Auto-lock idle timeout
- [ ] Role defaults — Owner, Manager, Cashier, Crew
- [ ] RBAC permission check helpers — `session.hasPermission()`
- [ ] Basic onboarding wizard — store name, currency, first Owner account
      (Steps 1–3 of 7; remaining steps unlocked in Should Have)
- [ ] HOTP key generation and QR provisioning UI (manager side)
- [ ] Override code generation UI (manager side)
- [ ] Override code entry and validation UI (staff side)
- [ ] `ManagerApprovalOverlay` — full implementation, HOTP Path A
- [ ] Override code scope selection: single action versus time-boxed elevation
      token, with the warning UI and the Settings toggle that disables elevation
      tokens ([ADR-005](docs/adr/ADR-005-hotp-offline-authorization.md))
- [ ] Override code single-use consumption + `OverrideLog` write, synced when
      connectivity returns
- [ ] Audit log write on every authorization event
- [ ] Printed backup codes for HOTP fallback

### feature-pos

- [ ] Sale register UI — cart, line items, quantity, per-item discount
      (adaptive: compact and expanded layouts)
- [ ] Item search and barcode scan to cart (CameraX)
- [ ] Payment method selection — cash, split payment
- [ ] Change calculation display
- [ ] Transaction write to Room — `sync_status = PENDING`
- [ ] Stock movement write — `OUT` per line item on sale completion
- [ ] Receipt composition — itemised, tax breakdown, store name, date
- [ ] Bluetooth ESC/POS thermal printer integration (`PrinterService`)
- [ ] Shift open and close UI — cash-up, variance summary
- [ ] Shift record write to Room
- [ ] Void transaction — requires `POS_VOID_TRANSACTION` + `ManagerApprovalOverlay`
- [ ] Refund flow — requires `POS_ISSUE_REFUND` + `ManagerApprovalOverlay`
- [ ] Fulfillment status display for negative-stock pre-orders
- [ ] `fulfillment_status = PENDING_STOCK` write + manager alert when a sale
      drives stock negative ([ADR-002](docs/adr/ADR-002-event-sourced-inventory.md))
- [ ] Denormalized `currentStock` maintained on every movement write

### feature-inventory

- [ ] Item list UI — search, filter by category, adaptive layout
- [ ] Item detail and edit UI
- [ ] Category management UI
- [ ] Item variant and attribute management
- [ ] Barcode scan for item lookup (CameraX)
- [ ] Stock receiving UI — log incoming stock, write `IN` movement to Room
- [ ] Manual stock adjustment UI — with reason field
- [ ] Reorder level configuration per item
- [ ] Low stock local notification — WorkManager periodic check
- [ ] Barcode label printing trigger (`PrinterService`)

### feature-settings (Must Have subset)

- [ ] Backend tier selection UI — Tier 0 through Tier 3
- [ ] Processing mode selection UI — Standalone / Assisted / Server-First
  (with explicit warning for Server-First)
- [ ] Full database backup — AES-encrypted, SAF destination picker
- [ ] Restore from backup — file picker, validation summary
- [ ] Backup schedule configuration
- [ ] Manual "Sync Now" trigger
- [ ] In-app update notification for `github` flavor
  (Play Store and F-Droid builds excluded — updates handled by their
  respective stores)
- [ ] In-app update UI and install path: 24 hour WorkManager check off the POS
      critical path, banner with What's new / Update / Dismiss, dismiss
      persisted until the next version, download to cache, `PackageInstaller`
      ([ADR-014](docs/adr/ADR-014-in-app-update-mechanism.md))
- [ ] Backend credentials stored in Android Keystore
- [ ] Runtime rebinding of `SyncBackend` when the tier selection changes
- [ ] Language selection

### Sync Engine (Tier 0 complete, Tier 1 scaffold)

- [ ] WorkManager sync job — detects PENDING records, calls `SyncBackend`
- [ ] Exponential backoff retry on sync failure
- [ ] Sync failure local notification when retry budget exhausted
- [ ] `sync_status` lifecycle — PENDING → SYNCING → SYNCED / FAILED / CONFLICT
- [ ] Sync triggers: connectivity restored, app foregrounded with PENDING
      records, 15 minute periodic check, manual Sync Now
- [ ] Batched push reading from `sync_queue` rather than per record
- [ ] Tier 0 correctness: perpetual PENDING is not an error and must not fire
      the retry-exhausted notification
- [ ] Processing mode behaviour: Standalone and Assisted write paths, server
      reachability detection, Standalone as the Tier 0 default
      ([ADR-010](docs/adr/ADR-010-processing-modes.md))
- [ ] Ktor server project scaffold — routes, JWT auth, HTTPS, Docker Compose
- [ ] Ktor server: `/auth/login`, `/auth/logout`, JWT issuance and validation
- [ ] Ktor server: `/sync/push`, `/sync/pull`, `/sync/conflict`, file upload
- [ ] Ktor server: HTTPS enforced on LAN, rate limiting, input validation
- [ ] Ktor server: Exposed schema mirroring Room, Flyway migrations
- [ ] `KtorBackend` Android adapter — `pushRecords()`, `pullUpdates()`,
      `uploadFile()`
- [ ] Basic conflict resolution on server via `ConflictResolver`

### Release engineering (v0.1-alpha ships from GitHub Releases)

- [ ] Release signing: keystore generation, CI secrets, `signingConfigs`
      ([ADR-013](docs/adr/ADR-013-fdroid-izzydroid-distribution.md))
- [ ] Release workflow: tag, build three flavors, sign, attach to the GitHub
      Release
- [ ] `foss` base flavor, and `kmp-app-updater` excluded from the `playstore`
      source set ([ADR-014](docs/adr/ADR-014-in-app-update-mechanism.md))
- [ ] `RELEASING.md`: release checklist, tagging, and the mandatory alpha
      data-loss warning from
      [ADR-018](docs/adr/ADR-018-room-migration-strategy.md)
- [ ] Kanban board: five columns, WIP limit of 2, board automation, and the
      written definition of done
      ([ADR-012](docs/adr/ADR-012-kanban-development-methodology.md))

---

## Should Have — v0.2-alpha

These items make Kaup production-ready for real store use.
Work begins after all Must Have items are stable.

### feature-auth (Should Have additions)

- [ ] Full user management UI — create, edit, delete staff accounts
- [ ] Role assignment UI
- [ ] Per-user permission override UI — grant up or restrict down
- [ ] Biometric enrollment — Android BiometricPrompt, Keystore-backed
- [ ] Biometric-gated HOTP code generation (Option E)
- [ ] Server-provisioned HOTP key distribution (Option B, requires Tier 1+)
- [ ] Onboarding wizard — Steps 4–7 (backend selection, feature flags,
      printer pairing, HOTP provisioning for first staff account)

### feature-pos (Should Have additions)

- [ ] Card payment method — field capture only (no payment gateway in v1)
- [ ] Receipt email trigger
- [ ] `ReceiptEmailSender` + `IntentEmailSender` default, PDF receipt
      composition, recipient capture at checkout
      ([ADR-017](docs/adr/ADR-017-receipt-email.md))
- [ ] `SmtpEmailSender`: Keystore credentials, test-email button, WorkManager
      retry, failed-send notification
- [ ] Payment method configuration UI, store-defined custom methods, and the
      onboarding disclosure that card is capture-only
      ([ADR-015](docs/adr/ADR-015-payment-gateway-architecture.md))
- [ ] Per-item note field on cart line items
- [ ] Price override — requires `POS_OVERRIDE_PRICE` + `ManagerApprovalOverlay`
- [ ] Discount above threshold — requires `POS_DISCOUNT_ABOVE_X` +
      `ManagerApprovalOverlay`
- [ ] Refund evidence — optional photo/video capture, compressed,
      stored in app-private filesystem

### feature-inventory (Should Have additions)

- [ ] Item kit / bundle configuration
- [ ] Item thumbnail image — capture or gallery pick, stored locally
- [ ] Stock valuation display — FIFO cost per item

### feature-customers

- [ ] Customer list and detail UI — adaptive layout
- [ ] Customer search UI
- [ ] Customer assignment to a sale in `:feature-pos`
- [ ] Customer purchase history view
- [ ] Loyalty program — points accrual and redemption
- [ ] Gift card issuance and redemption

### feature-suppliers

- [ ] Supplier list and detail UI
- [ ] Purchase order creation and status tracking
- [ ] Link stock receiving in `:feature-inventory` to a purchase order

### feature-expenses

- [ ] Expense entry UI — amount, category, date, notes, optional receipt photo
- [ ] Expense category management UI
- [ ] Expense list and filter UI

### feature-sales

- [ ] Quotation creation and management
- [ ] Sales order creation and status tracking
- [ ] Delivery note creation and fulfillment tracking
- [ ] Sales order → POS handoff when customer pays

### feature-reports

- [ ] Sales report — by date range, by cashier, by item (adaptive layout)
- [ ] Inventory / stock report — current levels, movement history
- [ ] Payments summary — breakdown by payment method per shift
- [ ] Expense report
- [ ] Gross margin / cost price report
- [ ] CSV export for all report types
- [ ] PDF export for all report types

### feature-settings (Should Have additions)

- [ ] Housekeeping menu — synced file list with size summary, safe delete
- [ ] Local file retention policy configuration
- [ ] Video compression and duration settings
- [ ] Analytics and crash reporting opt-in — explicit pros/cons warning
- [ ] `SupabaseBackend` Android adapter (Tier 2–3)
- [ ] `AppwriteBackend` Android adapter (Tier 2–3)
- [ ] ntfy remote notification setup UI (`NtfyBackend`)
- [ ] Feature flag settings screen, "Coming soon" badges, and flag state
      included in backup and restore
      ([ADR-007](docs/adr/ADR-007-feature-flag-module-system.md))
- [ ] Dark mode toggle (Settings → Display, default follow-system)
- [ ] Reference schema files for the Supabase and Appwrite backends
      (`supabase/schema.sql`, `appwrite/schema.sql`)

### Design system and accessibility

- [ ] Design system implementation: Success and Warning colour roles, the
      three-font system with Roboto Mono for numerics, the type scale, shape
      and spacing tokens, and the 48/56/64dp touch targets
      ([design system](docs/design/design-system.md))
- [ ] Adaptive layouts: five window size classes, navigation rail on expanded,
      `ListDetailPaneScaffold` for inventory, customers and reports, POS item
      grid
- [ ] Accessibility conformance: WCAG AA contrast in both themes, TalkBack
      content descriptions, 200% font scaling without clipping, focus order,
      no colour-only state indicators

### Platform and quality

- [ ] `AnalyticsAggregator` in `:shared-kmp/domain` (report aggregation stays
      out of `:feature-reports`)
- [ ] Notification backends: `NtfyBackend` implementation, server-side ntfy
      sender, FCM to ntfy to local fallback chain, TrustedTime with a
      `System.currentTimeMillis()` fallback, `notification_log` table
      ([ADR-011](docs/adr/ADR-011-notification-system.md))
- [ ] Bulk sync progress indicator when a Tier 0 store upgrades
- [ ] Database record archival policy for old synced rows
- [ ] Module boundary enforcement in the build, not only in review
      ([ADR-008](docs/adr/ADR-008-multi-module-android-architecture.md))
- [ ] CI checks for licence compatibility, trackers and hardcoded secrets
      ([ADR-013](docs/adr/ADR-013-fdroid-izzydroid-distribution.md))
- [ ] Adapter authoring guide for `SyncBackend` and `PaymentGateway`
      contributors

### Notifications (Should Have)

- [ ] Shift open reminder — AlarmManager, user-configured time
- [ ] Backup reminder — fires after user-configured days without backup
- [ ] Manager override request notification — via ntfy for Tier 1–3

### IzzyOnDroid Submission

- [ ] `fastlane/` metadata — store listing, screenshots, changelogs
- [ ] `github` flavor APK signed and attached to GitHub Release tag
- [ ] IzzyOnDroid metadata file committed to repo
- [ ] Submission PR opened to IzzyOnDroid repo

---

## v1.0 Release Requirements

The milestone table promises F-Droid and Play Store availability at v1.0.
Both are submission processes with their own lead time, and neither was
previously listed as work.

### F-Droid submission

- [ ] `fdroiddata` build recipe (`.yml`) prepared and tested
- [ ] Build verified reproducible from source: no binary blobs, no proprietary
      libraries, no hardcoded keys
- [ ] Merge request opened against the F-Droid data repository
- [ ] Channel-switch guidance documented: the F-Droid signature differs, so
      users must uninstall and reinstall

### Google Play Store submission

- [ ] `playstore` flavor built and signed as an AAB
- [ ] Store listing, content rating, and the data safety form, consistent with
      the zero-telemetry-by-default commitment in the PRD

---

## Could Have — v1.x

These items are genuine improvements but do not block any store from
operating fully without them. Ordered loosely by community value.

### feature-pos
- [ ] Loyalty points redemption at checkout
- [ ] Multi-currency display (display only — store currency is fixed)
- [ ] Customer-facing display output (second screen or cast)

### feature-inventory
- [ ] Stock transfer between locations
- [ ] Stock valuation methods — FIFO, weighted average, selectable
- [ ] Supplier quotation comparison

### feature-customers
- [ ] Mailchimp export integration

### feature-sales
- [ ] Commission tracking per staff member

### feature-reports
- [ ] Advanced analytics dashboard with interactive charts
- [ ] Scheduled report export via email or ntfy

### feature-settings
- [ ] Receipt template and footer editor
- [ ] FCM push notification support (`FcmBackend`, `playstore` flavor only)
- [ ] `AppwriteBackend` self-hosted setup wizard

### feature-auth
- [ ] NFC tap-to-approve manager authorization (Option C)
- [ ] BLE proximity approval (Option D — with explicit security caveat in UI)

### feature-restaurant
- [ ] Table grid UI — visual layout, status indicators
- [ ] Table creation, renaming, and merge
- [ ] Order assignment to table
- [ ] Split bill UI
- [ ] Crew role with restricted navigation

### Ktor Server
- [ ] Server-side RBAC enforcement (post-v1 security hardening)
- [ ] Multi-location stock transfer API
- [ ] Admin web UI for server management

---

## Won't Have — Post Year 1

These are explicitly out of scope until v2.0 or later.
Do not design for, prototype, or partially implement these in v1.

- Full double-entry accounting (General Ledger, AP/AR, bank reconciliation)
- Financial statements (P&L, Balance Sheet, Cash Flow)
- HR and payroll management
- Manufacturing (BOM, MRP, work orders)
- Quality control, asset management, project management
- CRM pipeline and e-commerce integration
- Web dashboard (Compose for Web)
- iOS app
- Kitchen Display System (KDS)
- Country-specific tax or payment defaults
- Hardware peripherals beyond Bluetooth ESC/POS printers
  (cash drawers, weight scales, customer-facing displays)

---

## Testing Strategy

### Unit Tests (`:shared-kmp/commonTest`)
Run on all targets — Android, JVM (server), and future Wasm.
Every class in `:shared-kmp/domain` must have unit test coverage before
the corresponding feature module is considered done.

Priority test cases per domain class:

| Class | Priority Cases |
|---|---|
| `SalesCalculator` | Inclusive tax, exclusive tax, mixed rates, zero-rate items, discount before and after tax, rounding at £0.005 |
| `TaxResolver` | Multiple rates in one cart, tax-exempt items, zero-rate correctly distinguished from no-tax |
| `InventoryEngine` | Normal IN/OUT, negative stock result, simultaneous writes from two devices, look-ahead window edge case |
| `ConflictResolver` | Two devices sell the last unit offline, receiving + sale overlap, timestamp tie |
| `HOTPGenerator` | Valid code, consumed code rejected, counter drift within window, drift outside window, printed backup code path |

These gates are tracked as issues in their own right, because "the feature is
done" and "the gate that says the feature is done" are different pieces of work:

- [ ] Unit coverage for every `:shared-kmp/domain` class, including the
      priority cases above
- [ ] Integration test harness and per-feature suites (see below)
- [ ] The four end-to-end UI flows (see below)

### Integration Tests (`:feature-*`)
Each feature module runs integration tests against an in-memory Room database.
No emulator required. Focus on repository layer — DAO writes, sync status
transitions, permission gate enforcement.

Processing modes are tested independently: Standalone, Assisted, and the
Server-First fallback to a local write with an unconfirmed indicator
([ADR-010](docs/adr/ADR-010-processing-modes.md)).

### UI Tests (`:android-app`)
End-to-end flows tested on emulator using Espresso + Compose Test:
- Complete a sale → receipt generated → stock decremented
- Cashier attempts void → ManagerApprovalOverlay → HOTP validates → void completes
- Sync failure → retry → SYNCED status confirmed
- Backup → wipe app data → restore → data intact

### Manual Test Checklist (before every release tag)
- [ ] Cold start to Lock Screen in ≤ 2 seconds on a 2 GB RAM device
- [ ] Complete sale with airplane mode enabled — full offline confirmation
- [ ] Barcode scan to cart in ≤ 500 ms
- [ ] Receipt prints on Bluetooth ESC/POS thermal printer
- [ ] Backup and restore roundtrip — data verified intact
- [ ] All three build flavors compile and launch cleanly

---

## Resolved Decisions

The four questions this section used to list were all answered on 2026-03-22.
The resulting work is folded into the lists above.

| Question | Answer | ADR |
|---|---|---|
| Card payment: capture-only or a gateway? | Capture-only default, pluggable `PaymentGateway`, adapters are community territory | [ADR-015](docs/adr/ADR-015-payment-gateway-architecture.md) |
| Multi-location: retrofit later or design for it now? | `location_id` in the schema from day one, single seeded location, UI hidden until v1.x | [ADR-016](docs/adr/ADR-016-multi-location-schema.md) |
| Receipt email: intent or SMTP? | Both, behind `ReceiptEmailSender`; intent is the zero-config default | [ADR-017](docs/adr/ADR-017-receipt-email.md) |
| Room migration strategy? | Destructive during alpha, formal migrations from v0.2-alpha, schema JSON committed throughout | [ADR-018](docs/adr/ADR-018-room-migration-strategy.md) |

## Open Questions

These are unresolved decisions that will need a new ADR before implementation:

| Question | Blocks |
|---|---|
| Money and tax contract: rounding mode, whether subtotal is presented net or gross, and the negative-quantity policy | `SalesCalculator`, every receipt and report |
| Conflict resolution policy: stock movements merge commutatively, but mutable records need last-write-wins with a vector clock or per-field merge, and wall-clock skew needs handling | `ConflictResolver`, Tier 1+ sync |
| Tier 2 and Tier 3 definitions differ between `docs/architecture.md` and `README.md` | Backend tier selection UI |
| Does the `playstore` flavor use the Play in-app update API, as ADR-014 says, or no in-app update at all, as this file said? | feature-settings, release engineering |
| Google Sans is specified by the design system but is not freely redistributable, which collides with the F-Droid clean rule | Design system, F-Droid submission |