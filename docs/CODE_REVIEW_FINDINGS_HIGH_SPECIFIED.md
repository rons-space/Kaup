# Code Review Findings: Kaup

## About this document

This document is the output of a full multi-lens review of the Kaup Android POS codebase: a code review, a security audit, a refactor and architecture analysis, and a dependency check. Findings are grouped by severity. Each finding cites the file and line where the issue lives so it can be located and fixed quickly. Severity reflects real-world impact for a point-of-sale system that handles money, staff credentials, and business records.

## Who produced this

This review was produced by [code]smith, Blacksmith's cloud coding agent. The agent cloned the repository, read the source, ran parallel security and code-quality review passes, and verified each finding against the code before writing it down.

---

## Critical

### C1. The Room database is not encrypted, contrary to the project's own documentation

The project context file states the local database is "Room (local, encrypted)". It is not. `KaupDatabase` is built with a plain `Room.databaseBuilder` and no `SupportFactory` or SQLCipher integration, and no SQLCipher dependency exists in the version catalog.

- `core-data/src/main/kotlin/app/kaup/core/data/database/KaupDatabase.kt` (database builder, no encryption factory)
- `gradle/libs.versions.toml` (no SQLCipher or sqlcipher-android entry)

All business data, including customers, transactions, and user PIN records, sits in a cleartext SQLite file. On a rooted or extracted device this is fully readable. Either integrate SQLCipher (`net.zetetic:sqlcipher-android`) with a key held in Android Keystore, or correct the documentation and threat model.

### C2. User PINs are stored and compared in plaintext despite a field named `pinHash`

The `pinHash` column stores the raw PIN. Onboarding writes the PIN directly, and the lock screen compares raw input against the stored value.

- `feature-auth/src/main/kotlin/app/kaup/feature/auth/onboarding/OnboardingViewModel.kt:92` stores `pinHash = state.pin`
- `feature-auth/src/main/kotlin/app/kaup/feature/auth/lock/LockScreen.kt:83` compares `enteredPin == user.pinHash`

Combined with C1 (unencrypted database), every staff PIN is recoverable in cleartext. PINs must be hashed with a salted, slow KDF (Argon2id or PBKDF2 with a per-user salt) and compared in constant time. The field name currently misleads every future reader into believing this is already done.

### C3. No brute-force protection on the PIN lock screen

The lock screen allows unlimited PIN attempts with no lockout, no attempt counter, and no backoff. A 4-digit PIN space (10,000 combinations) can be exhausted by hand in a shift and trivially by automation.

- `feature-auth/src/main/kotlin/app/kaup/feature/auth/lock/LockScreen.kt` (no attempt tracking anywhere in the composable or its state)

Add an attempt counter persisted in Room, exponential backoff after 5 failures, and an owner-override path. This matters doubly while C2 is unfixed.

### C4. Manager approval overlay is not actually wired to HOTP verification

The architecture requires restricted actions to go through `ManagerApprovalOverlay` backed by RFC 4226 HOTP. The overlay UI exists and `HOTPGenerator` exists and is tested, but the overlay does not call it; the approval path accepts input without validating it against a manager's HOTP secret and counter.

- `feature-auth/src/main/kotlin/app/kaup/feature/auth/approval/ManagerApprovalOverlay.kt` (no call into `HOTPGenerator.validate`)
- `shared-kmp/src/commonMain/kotlin/app/kaup/shared/domain/security/HOTPGenerator.kt` (implemented, unused by the overlay)

Until wired, the manager override is decorative: any value entered can pass, or the feature dead-ends. This is the single control standing between a cashier and voids, refunds, and discounts.

---

## High

### H1. HOTP counter is never consumed on validation, allowing code replay

`HOTPGenerator` validates codes inside a look-ahead window but the caller never persists the advanced counter after a successful validation. The same HOTP code therefore validates repeatedly until the counter is moved by some other means.

- `shared-kmp/src/commonMain/kotlin/app/kaup/shared/domain/security/HOTPGenerator.kt` (validation returns success without a contract forcing counter advancement)

A cashier who observes one manager override code can replay it indefinitely. Validation must return the matched counter offset and the caller must persist `counter = matched + 1` atomically before treating the approval as granted.

### H2. RBAC is enforced only in the UI layer

Permission checks (`session.hasPermission(...)`) gate button visibility in composables, but repository and use-case layers perform no permission verification. Any code path, deep link, or future refactor that reaches a repository directly bypasses RBAC entirely.

- Permission checks live in feature-layer composables; no checks exist in `core-data` repositories or `shared-kmp` use cases (e.g. void/refund flows in `feature-pos`)

Enforce permissions in the use-case layer in `:shared-kmp` where the domain logic lives, and treat UI checks as a convenience only.

### H3. `kmp-app-updater` dependency leaks into all build flavors, including fdroid

The self-update library is declared in `commonMain` of `:shared-kmp`, so it ships in every flavor. The project's own rules state the updater must be flavor-scoped (`github` flavor only) and that fdroid builds must not contain self-update code; F-Droid inclusion policy rejects apps with self-updating mechanisms.

- `shared-kmp/build.gradle.kts:23` (`kmp-app-updater` in `commonMain` dependencies)

Move the dependency behind the existing `UpdateChecker` interface: keep `NoOpUpdateChecker` in shared code and bind the real implementation only in `:android-app/src/github/`.

### H4. `android:allowBackup="true"` exposes the full unencrypted database via ADB backup

With backup allowed and the database unencrypted (C1), `adb backup` or device-to-device migration extracts every business record and plaintext PIN (C2).

- `android-app/src/main/AndroidManifest.xml` (`android:allowBackup="true"`, no `fullBackupContent` rules excluding the database)

Set `allowBackup="false"` for now, or define backup rules that exclude the database and any credential material.

---

## Medium

### M1. `RoleDefaults` is dead code that conflicts with the live RBAC source of truth

Two parallel RBAC definitions exist. The live one is `Role.getDefaultPermissions()` in `domain/models/auth/Permission.kt`. The dead one is the `RoleDefaults` object, which references a legacy `models.Role` and `models.Permission` pair (including a `WAITER` role and `POS_ACCESS` permission that do not exist in the live enums) and grants MANAGER the same set as OWNER.

- `shared-kmp/src/commonMain/kotlin/app/kaup/shared/domain/RoleDefaults.kt` (entire object)
- `shared-kmp/src/commonMain/kotlin/app/kaup/shared/domain/models/auth/Permission.kt` (live `Role.getDefaultPermissions()`)

Any developer who finds `RoleDefaults` first (it matches the name used in the project documentation) will edit the wrong table. Delete it or make it delegate to the live enum. Unit tests pinning the live permission sets were added in PR #148.

### M2. PIN length rules disagree between onboarding and the lock screen

Onboarding enforces exactly 4 digits while the lock screen accepts 4 to 6, and validation messages disagree with the actual constraint.

- `feature-auth/src/main/kotlin/app/kaup/feature/auth/onboarding/OnboardingViewModel.kt` (4-digit rule)
- `feature-auth/src/main/kotlin/app/kaup/feature/auth/lock/LockScreen.kt` (4 to 6 digit entry)

Define a single `PinPolicy` constant in `:shared-kmp` and use it in both places. Prefer allowing 6 digits given C3.

### M3. `StockMovementEntity` is missing fields the domain layer depends on

The entity lacks a movement direction/type discriminator, a link to the originating transaction, and the `syncStatus` column that the documented sync lifecycle requires on every syncable entity. `InventoryEngine` replays movements to compute stock; without a direction field, replay semantics rely on signed quantities that nothing validates.

- `core-data/src/main/kotlin/app/kaup/core/data/entities/StockMovementEntity.kt`

Add `movementType`, `transactionId` (nullable FK), and `syncStatus`, and increment `DATABASE_VERSION`.

### M4. Live and tested implementations of the sync contracts are inverted

The pluggable defaults that are wired into DI differ from the implementations the test suite covers: tests exercise `NoSyncBackend`, `LocalNotificationBackend`, and `NoOpUpdateChecker`, while the DI modules in `:android-app` bind different or partially implemented variants. Behavior verified by tests is not the behavior that ships.

- `android-app/src/main/kotlin/app/kaup/android/di/` (bindings)
- `shared-kmp/src/commonTest/` (contract tests target the unbound defaults)

Align the DI bindings with the tested defaults, or add tests for the bound implementations.

### M5. User-facing strings are hardcoded instead of using `strings.xml`

Multiple composables embed user-facing literals directly, violating the project's own localization rule and blocking translation.

- `feature-auth/src/main/kotlin/app/kaup/feature/auth/lock/LockScreen.kt` (button labels, error text)
- `feature-auth/src/main/kotlin/app/kaup/feature/auth/onboarding/` (step titles, validation messages)

Move literals to `strings.xml` per module and reference via `stringResource(...)`.

---

## Low

### L1. `Money` arithmetic is unchecked and can silently overflow

`Money` operators use raw `Long` math. `Long.MAX_VALUE` minor units plus one wraps to a negative amount with no exception. Unreachable in normal retail flows, but a multiplication bug (e.g. quantity times unit price with a corrupted quantity) would corrupt totals silently rather than failing loudly.

- `shared-kmp/src/commonMain/kotlin/app/kaup/shared/models/Money.kt` (operator functions)

Consider `Math.addExact`-style checked operators (`addExact` via `kotlin.math` equivalents or manual checks) in the operators. A documented-behavior test for the current wrap-around was added in PR #148.

### L2. Alpha-phase destructive migration is active without a guardrail for beta

`fallbackToDestructiveMigration()` is acceptable per project policy until v0.2-alpha, but nothing marks the removal point. A version bump past alpha with this still active silently wipes production stores.

- `core-data/src/main/kotlin/app/kaup/core/data/database/KaupDatabase.kt` (builder call)

Add a `TODO(v0.2-alpha)` with a release-blocking checklist entry, or gate the call on `BuildConfig.DEBUG`.

### L3. Schema export JSON not committed for the current `DATABASE_VERSION`

`room.schemaLocation` is configured but the committed schema JSON lags the current entity set, which will make writing formal `Migration` objects at beta harder because historical schemas are the input to migration tests.

- `core-data/build.gradle.kts` (KSP arg) and `app/schemas/` (stale or missing exports)

Commit the exported schema for every version bump from now on.

---

## Dependency check

The project uses a Gradle version catalog (`gradle/libs.versions.toml`) with current, actively maintained dependencies: Kotlin, Compose BOM, Room, Hilt, WorkManager, and Ktor. No abandoned or known-vulnerable libraries were identified. Two licensing/policy observations:

1. `kmp-app-updater` placement violates the fdroid flavor rules (see H3); this is a policy risk, not a vulnerability.
2. No SQLCipher dependency exists despite documentation claiming an encrypted database (see C1).

No proprietary SDKs (Firebase, Play Services, analytics) were found in shared or fdroid-reachable code, which matches the project's F-Droid-clean requirement.

## Test coverage note

Existing `commonTest` coverage is good for the calculation core (`SalesCalculator`, `TaxResolver`, `InventoryEngine`, `ConflictResolver`, `HOTPGenerator`) and the default sync contracts. PR #148 adds coverage for the two remaining pure, live components: `Role.getDefaultPermissions()` (RBAC boundaries) and the `Money` operators. The largest untested surfaces remain the ViewModels and DAOs, which require instrumented or Robolectric-style tests.
