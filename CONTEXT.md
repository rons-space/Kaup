# Kaup — AI Agent Context File

Paste this file at the start of every coding session in Antigravity, Cursor,
or any other AI coding agent. It gives the agent the minimum context needed
to produce correct, architecture-aligned code without re-reading every ADR.

---

## What Kaup Is

Kaup is a free, open-source, offline-first Android POS and business management
system for small and medium stores. GPL v3. No subscriptions. No proprietary
cloud. Targets retail, F&B, and market stalls in any country.

**Package name**: `app.kaup.android`
**Language**: Kotlin (Kotlin Multiplatform for shared domain logic)
**Min SDK**: API 26 | **Target SDK**: latest stable
**UI**: Jetpack Compose + Material 3 Expressive
**DI**: Hilt
**Database**: Room (local; at-rest encryption is planned, not yet implemented)
**Sync**: WorkManager queue → pluggable `SyncBackend` interface
**Server**: Optional Ktor (self-hosted, Docker Compose)
**Build flavors**: `github`, `fdroid`, `playstore`

---

## Module Structure

```
:shared-kmp          → Kotlin Multiplatform — domain models, business logic,
                       interfaces. NO android.* or androidx.* imports. Ever.

:core-data           → Room entities, DAOs, database, repositories.
                       Depends on :shared-kmp only.

:core-ui             → Compose theme, typography, shapes, shared components.
                       Depends on :shared-kmp only.

:core-network        → WorkManager, sync queue, SyncBackend wiring,
                       notification backends.
                       Depends on :shared-kmp only.

:feature-auth        → Lock screen, PIN, biometric, RBAC, HOTP, onboarding.
:feature-pos         → Sale register, cart, payments, receipts, shifts.
:feature-inventory   → Items, categories, stock movements, labels.
:feature-customers   → Customer profiles, loyalty, gift cards.
:feature-suppliers   → Supplier management, purchase orders.
:feature-expenses    → Expense entry, categories, receipt capture.
:feature-sales       → Quotations, sales orders, delivery notes.
:feature-reports     → Sales, inventory, payments, expenses, margin reports.
:feature-settings    → Sync backend, processing mode, backup, restore,
                       printer, language, update checker.
:feature-restaurant  → Tables, table orders, split bill (Could Have / v1.x).

:android-app         → Navigation host, Hilt setup, flavor wiring.
                       The ONLY module that may depend on all feature-* modules.

ktor-server          → Standalone Kotlin/JVM server. Separate Gradle project.
```

---

## The Three Non-Negotiables

Every line of code written for Kaup must respect these three rules.
If a proposed solution violates any of them, reject it and find another way.

### 1. Offline-First — Always
Every feature must work completely with no internet connection.
Network calls are background sync, never blocking foreground operations.
A sale must complete, a receipt must print, stock must decrement, and a
manager override must work — all with airplane mode enabled.
- Use Room for all state
- Use WorkManager for all sync
- Never gate a UI action on a network response

### 2. Module Boundaries — Never Cross Them
```
feature-*  →  may import :core-* and :shared-kmp ONLY
feature-*  →  MUST NOT import another feature-* module
core-*     →  may import :shared-kmp ONLY
shared-kmp →  MUST NOT import android.* or androidx.*
```
Cross-feature data flows through repository interfaces in `:core-data`.
Cross-feature navigation is handled in `:android-app`.
If you find yourself adding a `feature-*` dependency to another `feature-*`
module, stop — expose the data through `:core-data` instead.

### 3. F-Droid Clean — No Proprietary Dependencies
The `fdroid` and `github` build flavors must contain zero proprietary SDKs.
- No Firebase (FCM is `playstore` flavor only, behind `FcmBackend`)
- No Google Play Services in shared code
- No proprietary analytics or crash reporting unless explicitly opted in
- No API keys hardcoded in source
- All new libraries must be MIT, Apache 2.0, LGPL, or GPL-compatible

---

## Key Domain Classes (in :shared-kmp)

| Class | Responsibility |
|---|---|
| `SalesCalculator` | Line items → totals with inclusive/exclusive tax, discounts, rounding |
| `TaxResolver` | Applies per-item tax rates to a cart |
| `InventoryEngine` | Computes current stock by replaying movement event log |
| `ConflictResolver` | Resolves simultaneous writes from multiple offline devices |
| `HOTPGenerator` | Generates and validates HOTP codes (RFC 4226) for offline manager approval |

---

## Pluggable Interfaces (all in :shared-kmp)

There is no `sync-contracts` source set. The interfaces live under
`domain/sync`, `domain/notification` and `domain/update`, and their models
under `models/`. Several ADRs name `sync-contracts`; treat the packages below
as the real locations.

| Interface | Built-in Default | Purpose |
|---|---|---|
| `SyncBackend` | `NoSyncBackend` | Push/pull records to optional server |
| `NotificationBackend` | `LocalNotificationBackend` | Fire notifications locally or via ntfy |
| `UpdateChecker` | `NoOpUpdateChecker` (`github` → `GitHubUpdateChecker`) | Check GitHub Releases for updates |
| `PaymentGateway` | `CaptureOnlyGateway` | Process card payments (community adapters) |
| `ReceiptEmailSender` | `IntentEmailSender` | Send receipt by email |
| `PrinterService` | — | Bluetooth ESC/POS thermal printing |

---

## RBAC — How Permissions Work

Every restricted action requires a `Permission` enum check:

```kotlin
if (session.hasPermission(Permission.POS_VOID_TRANSACTION)) {
    // show void button
}
```

Actions that exceed the current user's role require `ManagerApprovalOverlay`,
which uses HOTP for offline verification — no server call, no internet needed.

Roles and their default permission sets live in `Role.getDefaultPermissions()`
in `:shared-kmp/domain/models/auth`. The four built-in roles are `OWNER`,
`MANAGER`, `CASHIER` and `CREW`.

`CREW` is the name ADR-009 settled on, and it is what the code has always
enforced. This file, the README and the roadmap said `WAITER`, which read as
restaurant-only for a role that also covers a shop floor assistant.

`MANAGER` holds every permission except the `USERS_*` family: a manager runs
the store day to day but does not create or delete staff accounts.

---

## Sync Status Lifecycle

Every syncable Room entity carries a `syncStatus` column:

```
PENDING → SYNCING → SYNCED
                 → FAILED  → (retry with backoff) → SYNCED or CONFLICT
CONFLICT → (ConflictResolver) → SYNCED
```

WorkManager detects `PENDING` records and calls `SyncBackend.pushRecords()`.
On Tier 0 (no sync), records stay `PENDING` forever — this is correct behavior.

---

## Build Flavors

| Flavor | Update | FCM | Sideload install |
|---|---|---|---|
| `github` | `GitHubUpdateChecker` | ❌ | ✅ |
| `fdroid` | F-Droid client | ❌ | ✅ |
| `playstore` | Play Store API | ✅ optional | ❌ |

Never add the `kmp-app-updater` or FCM dependency to the `fdroid` or
`playstore` / `fdroid` cross-flavor respectively. Use Hilt flavor-specific
modules in `:android-app/src/<flavorName>/`.

---

## Database Notes

- **Schema version**: increment `DATABASE_VERSION` in `KaupDatabase` on
  every entity change
- **Alpha phase**: `fallbackToDestructiveMigration()` is active — data wipe
  on schema change is acceptable until `v0.2-alpha`
- **Beta onward**: formal `Migration` objects required for every version bump
- **Multi-location**: every location-aware entity carries a non-null
  `locationId` FK → `locations.id`. Single default location is seeded on
  first launch. Never omit `locationId` from a new location-aware entity.
- **Schema export**: enabled via KSP arg `room.schemaLocation` — JSON files
  committed to `/app/schemas/`

---

## Naming Conventions

| Thing | Convention | Example |
|---|---|---|
| Room entity | `PascalCase` + `@Entity` | `StockMovement`, `TransactionLineItem` |
| DAO | `PascalCase` + `Dao` suffix | `StockMovementDao`, `ItemDao` |
| ViewModel | `PascalCase` + `ViewModel` suffix | `PosRegisterViewModel` |
| Composable | `PascalCase` | `CartLineItemRow`, `LockScreen` |
| Repository | `PascalCase` + `Repository` suffix | `InventoryRepository` |
| Use case | `PascalCase` + `UseCase` suffix | `CompleteSaleUseCase` |
| Hilt module | `PascalCase` + `Module` suffix | `SyncBackendModule` |
| Flavor DI module | placed in `src/<flavor>/` source set | `src/github/GitHubUpdateModule.kt` |

---

## Commit Message Format

```
type(scope): short description

Closes #ISSUE_NUMBER
```

Types: `feat` `fix` `docs` `test` `refactor` `chore` `perf`
Scope: module name without colon — `pos`, `inventory`, `auth`, `shared-kmp`,
`ktor-server`, `docs`, `ci`

Example:
```
feat(inventory): add reorder level threshold per item

Closes #42
```

---

## What Not to Do

- Do not suggest Firebase, Google Analytics, Crashlytics, or any Google
  Play Services API in shared or F-Droid code
- Do not add a `feature-*` dependency to another `feature-*` module
- Do not write android.* imports in `:shared-kmp`
- Do not block the POS sale flow on any network call
- Do not hardcode API keys, URLs, or secrets in source
- Do not use `nullable locationId` on location-aware entities — always
  non-null with a default from the seeded location
- Do not skip incrementing `DATABASE_VERSION` when changing an entity class
- Do not write user-facing strings as hardcoded literals — use `strings.xml`
