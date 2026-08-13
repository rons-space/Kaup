# Code Review Findings: Kaup

**Date:** 2026-08-13
**Reviewer:** [code]smith, a cloud coding agent from Blacksmith (https://www.blacksmith.sh). This review was produced autonomously by reading every layer of the repository: the `:shared-kmp` domain logic and its tests, the auth/crypto stack, the `:core-data` persistence layer, the `:core-network` sync engine, the `:android-app` and `:feature-auth` UI/DI, and the Gradle build, CI, and docs. Every finding cites the file and line range where it was verified; nothing is speculative. The build was not executed (this is a research-only review), so toolchain claims are flagged as unverified where relevant.

**Scope:** Full-repo deep dive covering cryptography/auth, money and inventory domain correctness, architecture-rule compliance, and build/CI/docs/hygiene.

**Severity legend:** CRITICAL = security hole, data-loss, or a rule violation that breaks a core project promise. HIGH = serious defect. MEDIUM = quality/robustness issue that will bite as features land. LOW = hygiene.

---

## Executive summary

Kaup is an early-stage (14 commits), unusually well-documented offline-first Kotlin Multiplatform + Android POS app. The parts that exist are the auth/RBAC/HOTP foundation, the `:core-*` scaffolding, and the `:shared-kmp` money/inventory/conflict domain logic (with 32 unit tests, the HOTP suite validated against RFC 4226 vectors). The module boundaries (non-negotiable #2) are genuinely clean, `:shared-kmp` commonMain has zero `android.*` imports, and the ADRs are real, dated architecture decisions.

The problems are of two kinds. First, the codebase makes security and feature claims it does not implement: the docs and code advertise an "encrypted Room database", but the database is plaintext, and user PINs are stored and compared in plaintext with a field misleadingly named `pinHash` and no brute-force protection. Second, the project's own three non-negotiables are partially violated (the `kmp-app-updater` dependency ships to the F-Droid flavor; the sync engine is inert; no default location is seeded, so the first item insert would fail its FK), and the `:shared-kmp` layer contains a full set of duplicated, divergent model/interface hierarchies where the app runs on one copy and the domain engines and documentation describe the other (they already disagree on whether the fourth role is `CREW` or `WAITER` and on manager permissions). None of the money/inventory domain logic is wired into any feature yet, which is the right moment to fix its correctness bugs (negative-quantity handling, multi-inclusive-tax math, rounding) before it ships. Given the alpha stage nothing here is exploitable in production yet, but the plaintext-PIN/plaintext-DB gap and the duplicated type hierarchy should be closed before feature modules build on top of them.

---

## 1. Security: auth, crypto, at-rest protection

### 1.1 PINs stored and verified in plaintext (CRITICAL)

`OnboardingViewModel.kt:92` stores the owner PIN raw with a self-admitted TODO: `pinHash = state.ownerPin, // Note: In production, hash this properly`. `UserEntity.kt:14` names the column `pinHash` but it holds the raw PIN. Verification happens **inside the Composable** by plain string compare: `LockScreen.kt:83` `if (pin == user.pinHash)`. There is no hashing, salt, or KDF anywhere in the repo.

### 1.2 The Room database is not encrypted (CRITICAL)

`DatabaseModule.kt:26-33` builds the DB with a plain `Room.databaseBuilder(...).fallbackToDestructiveMigration().build()`, no `openHelperFactory`. There is zero SQLCipher/`net.zetetic`/`SupportFactory` anywhere in source or the version catalog. So the "Database: Room (local, encrypted)" claim in CONTEXT.md (and "encrypted Room database" in README/SECURITY.md) is false: users, roles, `permissionsOverride`, plaintext PINs, and HOTP counters all live in a plaintext SQLite file. Combined with 1.1, every credential is recoverable by reading one file, and `android:allowBackup="true"` (`AndroidManifest.xml:7`, no `dataExtractionRules`) means Android Auto Backup can upload it off-device.

### 1.3 No brute-force protection on the lock screen (CRITICAL)

`LockScreen.kt:81-92` auto-submits at exactly 4 digits with only a 500ms cosmetic delay on failure, no attempt counter, no lockout, no backoff, and no persisted failed-attempt state. A 4-digit space (10,000) is trivially brute-forceable on-device. (`OnboardingViewModel.kt:56` also allows 4-6 digit PINs while the lock screen hard-caps at 4, so a 5-6 digit PIN can never authenticate: a lockout-by-bug.)

### 1.4 The manager-override / HOTP enforcement half does not exist (HIGH)

The HOTP *generation* code is RFC 4226-correct (big-endian counter, dynamic truncation with `& 0x0F` / `0x7F`, verified against all RFC Appendix D vectors in `HOTPGeneratorTest.kt`), and the secret is genuinely encrypted via Android Keystore AES-GCM (`KeystoreManager.kt:36-60`, fresh random IV per call, no reuse). But:

- `HOTPGenerator.validateCode` has **no production caller** (only tests). `ManagerApprovalOverlay.kt:64-65` hands any non-empty typed string to `onApprove` with no HOTP check, no counter consumption, and no throttling (RFC 4226 §7.3 requires throttling), and the overlay itself has no call sites. The entire enforcement side of the offline-override feature is a stub.
- **Counter reuse (HIGH):** `OverrideCodeGenerationViewModel.kt:41,53-60` reads `hotpCounter` from the `SessionManager` snapshot taken at login (`SessionManager.kt:20-23`), which is never refreshed after the DAO writes the incremented counter. Every "Generate New Code" tap within a session re-reads the stale counter and reissues the *identical* code, breaking single-use semantics.

### 1.5 RBAC is enforced only in the UI (HIGH)

`PermissionHelpers.kt:27-31` (`RequirePermission`) merely skips rendering. No DAO, repository, or use case ever consults `SessionManager.hasPermission`. `SessionManager.login()` also trusts `UserEntity.permissionsOverride` read from the plaintext DB (`SessionManager.kt:22`), so with 1.2 a local tamperer can grant themselves the full permission set. Acceptable for scaffolding, but the enforcement layer must land in the domain/data layer before restricted features do.

### 1.6 Lower-severity security notes

- **No `FLAG_SECURE` (MEDIUM):** the HOTP secret is shown as a QR plus plaintext Base32 during provisioning (`HotpProvisioningScreen.kt:57-74`) and the override code screen has no screenshot/screen-record protection anywhere in the repo; the raw secret in `rawSecret: ByteArray` is never zeroed.
- **Keystore hardening (MEDIUM):** `KeystoreManager.kt:22-29` sets no StrongBox, no `setUserAuthenticationRequired`, no `setUnlockedDeviceRequired`, and does not handle `KeyPermanentlyInvalidatedException`; any code running as the app UID can silently decrypt the HOTP secret.
- **Non-constant-time comparison (MEDIUM):** both `HOTPGenerator.kt:42` (`generated == inputCode`) and the PIN compare use `==`.
- **Positive:** the HOTP secret uses `SecureRandom` with a correct 160-bit size; no `java.util.Random`/`Math.random`; no hardcoded keys or salts anywhere.

---

## 2. Domain logic correctness (`:shared-kmp`)

Money is correctly modeled as integer minor units (`Money.kt:6` `value class Money(val minorUnits: Long)`), but **all arithmetic routes through `Double` intermediates** re-quantized with `roundToLong()` (`SalesCalculator.kt:13,19,38,51-52,62,68,72`; `LineItem.quantity` and tax/discount rates are `Double`). Drift is bounded to about ±1 minor unit per rounding site, a defensible design, but not exact, and `roundToLong` is half-up toward +infinity (not banker's rounding) and asymmetric for negatives, so a sale and its equal-and-opposite refund can differ by a minor unit. None of this domain logic is referenced by any module outside `:shared-kmp` tests yet, so it is the ideal time to fix the following before features wire in:

- **Negative-quantity (refund/return) lines are broken (HIGH).** `SalesCalculator.kt:25` clamps `if (itemDiscount > rawSubtotal) itemDiscount = rawSubtotal`; for a return line (qty -1, price 100) `rawSubtotal = -100`, a 10% discount is -10, and `-10 > -100` fires the clamp, forcing the discount to -100 and zeroing the line. Same sign bug for negative carts at line 43, and line 51's `> 0` guard skips the discount ratio for negative carts so tax and total use different bases. No test covers negative quantity.
- **Multiple inclusive taxes overstate tax (HIGH).** `SalesCalculator.kt:65-75` extracts each inclusive tax as `value - round(value/(1+rate))` on the full line value; with two 10% inclusive taxes on 1100 it reports 200 instead of the correct ~183. The reported `taxTotal` (shown to the customer / tax authority) is wrong whenever an item has 2+ inclusive rates.
- **Cart-discount proration loses/creates minor units (MEDIUM).** `SalesCalculator.kt:62` rounds each line's share of the global discount independently with no largest-remainder distribution, so per-line values do not sum to the cart discount, and tax (per-line) can be inconsistent with the total the customer pays.
- **`ConflictResolver` does not do what it is documented to do (HIGH).** CONTEXT and the sync lifecycle describe resolving simultaneous offline writes, but `ConflictResolver.kt:6-45` only deterministically sorts `StockMovement`s and flags the first zero-stock breach: no LWW, no merge, no `PendingRecord`/`RemoteUpdate` handling. What exists is deterministic and lossless but relies on device wall-clock timestamps with no hybrid-logical-clock/skew mitigation.
- **`InventoryEngine` uses a `Double` stock accumulator (MEDIUM)** (`InventoryEngine.kt:12`) which `ConflictResolver.kt:38` then compares exactly against `0.0`, so FP residue can be flagged as a negative-stock violation. The movement enum (`StockMovement.kt:5-7`) has no VOID/RETURN type and the engine ignores `type` entirely, so a voided sale can only be modeled as a compensating movement that corrupts receiving reports.

Test quality: the HOTP suite is strong (RFC vectors); the others are happy-path (SalesCalculator numbers all divide evenly, no rounding-tie, negative-quantity, multi-tax, or discount+tax interaction cases; the entire proportional-allocation path is never exercised with a nonzero ratio). `AnalyticsAggregator.kt` is an empty `// TODO` stub.

---

## 3. Architecture-rule compliance (the three non-negotiables)

### 3.1 Offline-first: not violated, but the sync stack is inert (HIGH)

No UI action gates on the network, and the default backend is a no-op, so the rule holds today. But `SyncManager.schedulePeriodicSync()`/`triggerImmediateSync()` have zero callers; the Hilt worker factory is configured in `KaupApplication.kt:10-18` yet the manifest never removes `androidx.work.WorkManagerInitializer`, so the default factory runs and `@HiltWorker SyncWorker` cannot be instantiated; and no entity except `locations` carries `syncStatus`, so "WorkManager detects PENDING records" is currently unimplementable. The live `LocalNotificationBackend` also posts to a channel nobody creates and the manifest lacks `POST_NOTIFICATIONS`, so notifications are a silent no-op.

### 3.2 Module boundaries: clean (the strongest part of the codebase)

Verified across all build files and imports: `:feature-auth` depends only on `:shared-kmp` + `:core-*`; `:core-data`/`:core-ui` on `:shared-kmp` only; `:android-app` is the sole aggregator; `:shared-kmp` commonMain has zero `android.*`/`androidx.*` imports. One nit: `:core-network` declares a dependency on `:core-data` (allowed by CONTEXT's module table but contradicting the stricter "core-* → :shared-kmp ONLY" rule) and never actually imports anything from it.

### 3.3 F-Droid clean: violated (CRITICAL for the project's promise)

`shared-kmp/build.gradle.kts:23` adds `implementation(libs.kmp.app.updater.core)` in `commonMain`, and every module (all flavors, including `fdroid`) depends on `:shared-kmp`, so the self-updater ships in the F-Droid build, exactly what CONTEXT forbids. The mitigating fact is that it is currently unused (`GitHubUpdateChecker.kt` is a stub returning `UpToDate`). Compounding this, the flavor architecture exists only on paper: `github`/`fdroid`/`playstore` are declared but every module has only `src/main` (no `src/github/`, `src/fdroid/`, `src/playstore/` source sets and no flavor Hilt modules), so the flavor-gating mechanism CONTEXT mandates does not exist, and `GitHubUpdateChecker` is compiled into all flavors.

### 3.4 Data layer: no default location is seeded (CRITICAL)

`items` and `stock_movements` both declare `NOT NULL locationId` FKs with `ON DELETE CASCADE` referencing `locations`, but `OnboardingViewModel.completeOnboarding()` never inserts a location and nothing else calls `LocationEntity(...)` or `getDefaultLocation`. So the `locations` table is always empty and the first item insert fails its FK constraint. The per-entity rule (non-null `locationId` with `@ForeignKey` + `@Index`) is followed; the seed-on-first-launch half is missing. Also, `syncStatus` is missing from `ItemEntity`/`StockMovementEntity`/`UserEntity` (only `LocationEntity` has it, as a raw String), and there is no `sync_queue` table despite `SyncWorker` referencing one.

---

## 4. Duplicated, divergent type hierarchies (HIGH)

`:shared-kmp` carries two parallel, incompatible copies of core contracts. The app runs on one, the domain engines and CONTEXT describe the other:

| Concern | Copy the app uses | Copy that is dead outside `:shared-kmp` |
|---|---|---|
| `Permission` | `shared.domain.models.auth.Permission` (31-33 values) | `shared.models.Permission` (10 values) |
| `Role` | `shared.domain.models.auth.Role` = OWNER/MANAGER/CASHIER/**CREW** | `shared.models.Role` = OWNER/MANAGER/CASHIER/**WAITER** |
| Role defaults | `Role.getDefaultPermissions()` (MANAGER minus USERS_*) | `RoleDefaults` object (MANAGER = full set) — zero callers |
| `SyncBackend` | `shared.sync.SyncBackend` (no-arg, no payloads) | `shared.domain.sync.SyncBackend` (rich `PendingRecord`/`SyncResult`) |
| `NotificationBackend` | `shared.sync.NotificationBackend` | `shared.domain.notification.NotificationBackend` |

Live consequences today: CONTEXT and the dead enum say the fourth role is `WAITER`; the running code uses `CREW`. `RoleDefaults` (named by CONTEXT as the source of truth) grants MANAGER every permission while the live `getDefaultPermissions()` strips USERS_*, a conflicting RBAC policy in the same module. The domain engines (`SalesCalculator`, `InventoryEngine`, `ConflictResolver`) are built on the dead `shared.models.*` types, so wiring them into feature-pos/inventory later will require bridging two type systems. `NoSyncBackend` and `LocalNotificationBackend` each exist in triplicate across `:shared-kmp` and `:core-network`. The `:shared-kmp/sync-contracts` package CONTEXT references does not exist. The `StockMovementEntity` in `:core-data` also drifts from the shared `StockMovement` model (no `direction`, no `transactionId`, no `syncStatus`, `type` stored as a free String whose comment `"RECEIPT"` mismatches the enum `RECEIVING`), so it cannot be mapped to the domain model without inferring `direction`.

---

## 5. Build, CI, docs, hygiene

- **No LICENSE file (HIGH).** `LICENSE` does not exist, yet README links to it, an ADR documents the GPL v3 decision, and SECURITY.md asserts GPL compatibility. Until the license text ships the project is not actually GPL-licensed, and F-Droid inclusion requires a recognized license file.
- **No CI, no static analysis (HIGH).** `.github/` has only issue/PR templates; there is no `.github/workflows/`, so nothing builds, tests, or enforces the module-boundary and F-Droid rules, even though README shows a `ci.yml` build badge and CONTRIBUTING requires "CI passes". No detekt/ktlint/Android lint/coverage tooling anywhere.
- **Suspicious / likely-unbuildable toolchain (HIGH, unverified).** `libs.versions.toml` pins `agp = "9.2.0"`, `hilt = "2.59.2"`, and the wrapper uses `gradle-9.5.0`, none of which could be confirmed as real stable releases here; paired with Kotlin 2.1.0 and compose-bom 2024.09.02 this is a badly skewed combination. No Android module applies the Kotlin plugin (relying on AGP built-in Kotlin, hinted by `android.disallowKotlinSourceSets=false`), and `android-app` enables minification pointing at a `proguard-rules.pro` that does not exist. Treat the build as unproven until it is actually run.
- **Documented scope vastly exceeds built scope (HIGH).** 9 of 10 feature modules and the entire `ktor-server` do not exist (`settings.gradle.kts` includes only `:shared-kmp`, `:core:*`, `:feature:feature-auth`, `:android-app`). README presents POS/inventory/loyalty/reports in present tense; CONTRIBUTING tells contributors to run `:feature-pos:test`, which cannot exist.
- **SECURITY.md over-claims (HIGH).** It states an encrypted database, AES-encrypted backups, an audit log, biometric auth, and a Ktor server with HTTPS/JWT; none of these are implemented (only the HOTP-secret Keystore encryption checks out).
- **Config inconsistencies (MEDIUM).** All modules set `minSdk = 24` while docs mandate 26; `compileSdk = 36` vs `targetSdk = 34`; `android-app` has no `res/` directory at all and every user-facing string is a hardcoded literal (violating the project's own strings.xml rule).
- **Hygiene (LOW).** 15 `.idea/` files committed; `ADR-015-...md.md` double extension; dead `ktor` catalog entry; `.gitignore` lacks keystore/signing patterns; two near-duplicate commits.

**Verified positives:** module boundaries and `:shared-kmp` purity hold; the HOTP implementation is genuine and RFC-tested; Room schema export is real and the committed JSONs match `@Database(version = 4)`; 32 domain unit tests exist on the highest-value logic; the ADRs are real, dated, well-argued decisions and the PR/issue templates operationalize the project's rules; the Gradle wrapper is complete and the version catalog is used consistently; no secrets or hardcoded service URLs anywhere.

---

## 6. Recommended remediation order

1. **Fix the credential/at-rest story (blocking before beta).** Hash+salt PINs with a real KDF (Argon2/scrypt) and a constant-time compare moved out of the Composable into a repository; wire SQLCipher with a Keystore-wrapped DB key so the "encrypted database" claim becomes true; add PIN attempt lockout/backoff; set `allowBackup=false` or scoped `dataExtractionRules`.
2. **Collapse the duplicated type hierarchy.** Pick one `Permission`/`Role`/`SyncBackend`/`NotificationBackend`/role-defaults set (the richer `domain.*` contracts), delete the `shared.models.*`/`shared.sync.*` copies and the triplicate backends, and reconcile the CREW-vs-WAITER and manager-permission divergence with the docs. Do this before feature modules build on either half.
3. **Make the data layer usable.** Seed the default location on first launch, add `syncStatus` to the syncable entities (and the `sync_queue` table), and reconcile `StockMovementEntity` with the domain `StockMovement`.
4. **Honor the F-Droid non-negotiable.** Move `kmp-app-updater` and `GitHubUpdateChecker` into a `github`-flavor-only source set, create the `github`/`fdroid`/`playstore` source sets and flavor Hilt modules CONTEXT promises.
5. **Fix the domain-logic bugs while nothing depends on them:** negative-quantity handling, multi-inclusive-tax extraction, cart-discount largest-remainder allocation, a documented rounding mode, and integer/fixed-point stock; then either implement `ConflictResolver` against the real `CONFLICT` state or rename it.
6. **Wire and de-inert the sync stack:** remove the default WorkManager initializer so the Hilt factory is used, schedule the worker, and create the notification channel + permission.
7. **Ship-safety and honesty:** add the GPL v3 LICENSE file; add a CI workflow (build all flavors + `:shared-kmp` tests + lint) or drop the badge; pin verifiable toolchain versions and add the missing `proguard-rules.pro`; rewrite README/SECURITY.md to describe the actual v0.1-alpha state; extract user-facing strings into `strings.xml`.
