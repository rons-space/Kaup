# Kaup Code Review: Merged Findings

Merged from the three independent code reviews in `docs/`.
Merge date: 2026-08-13
Repository HEAD at merge time: `ef89f62` (`Merge pull request #151 from rons-space/dev`)

---

## About this document

Three separate reviews of the Kaup codebase were produced independently. They
overlap heavily, they disagree in a few places, and each one contains findings
the other two missed. This document merges them into a single list so there is
one place to work from.

### Source documents

| Label | File | Size | Structure |
|---|---|---|---|
| **A** (master) | `CODE_REVIEW_NORMAL_UNSPECIFIED.md` | 1,632 lines, 36 findings | Severity-graded, IDs `C1-C6`, `H1-H11`, `M1-M13`, `L1-L6`, with reproduction appendix |
| **B** | `CODE_REVIEW_FINDINGS_HIGH_UNSPECIFIED.md` | 126 lines | Thematic sections (security, domain, architecture, duplication, build/CI/docs) with inline severities |
| **C** | `CODE_REVIEW_FINDINGS_HIGH_SPECIFIED.md` | 175 lines | Severity-graded, IDs `C1-C4`, `H1-H4`, `M1-M5`, `L1-L3` |

**A is the master.** It is by far the largest, it is the only one with a
reproduction appendix, its IDs are the most granular, and its severity scale is
explicitly defined. Every merged finding below therefore keeps A's ID and A's
severity unless a documented reason to change is given.

### How B and C were merged into A

For each finding in A, B and C were checked for a matching finding. The result
is recorded on a `Sources` line:

- **Sources: A, B, C** means all three reviews independently found it. Treat
  these as the highest-confidence findings in the document.
- **Sources: A, B** or **Sources: A, C** means two reviews found it.
- **Sources: A only** means the master found it alone. These are not weaker,
  they are mostly findings that required a measurement B and C did not make.
- **Adds** lines carry detail that B or C contributed which A did not have.
- **Divergence** lines record where the reviews disagree on severity or facts.

Findings that exist only in B or only in C were appended to A's ID space as
`H12-H15`, `M14-M16`, and `L7-L9`.

### Verification performed during the merge

A reviewed commit `b7f756e`. HEAD is now `ef89f62`. Where the three documents
contradicted each other, the claim was re-checked against the current tree:

| Claim under dispute | Result at `ef89f62` |
|---|---|
| Test count and location | 51 `@Test` across 10 files, still **all** in `:shared-kmp`. A's "32 in 8 files" is stale; C's claim that PR #148 landed `MoneyTest` and `RolePermissionsTest` is **correct** |
| Room schema export current? | `1.json` through `4.json` all present, `4.json` matches `version = 4`. B is **correct**, C's L3 ("schema JSON lags") is **not reproducible** |
| Schema versions 2 and 3 identical | Confirmed, both `d0aaa1cc13fbac7563053865aa1b0a5d`. A is correct |
| `kotlin-android` applied anywhere | 2 hits only: root `apply false` and the catalog entry. No module applies it. A is correct |
| `gradlew` mode | `100644`. A is correct |
| `LICENSE` file | Absent. A, B correct |
| CI workflows | `.github/` has templates only, no `workflows/`. A, B correct |
| `syncStatus` on entities | 2 matches, only `LocationEntity` plus the shared domain model. A is correct |
| `allowBackup` | `true`, no `dataExtractionRules`, no `fullBackupContent`. A, B, C correct |
| Any KDF or hashing primitive | Zero matches. A, B, C correct |
| `SyncManager` call sites | Zero outside its own declaration. B is correct |
| `sync_queue` table | Referenced only in a comment in `SyncWorker.kt:22`. B is correct |
| HOTP counter re-read per generation | `OverrideCodeGenerationViewModel` reads `sessionManager.currentUser.value.hotpCounter` and writes the DAO without refreshing the session. B is correct |

Nothing was built or run: there is still no JDK or Android SDK available, so
every build-related finding remains a prediction from configuration, exactly as
A stated.

---

## Overlap summary

| Category | Count |
|---|---|
| Findings in the merged list | **46** |
| Found by all three reviews | 12 |
| Found by exactly two reviews | 19 |
| Found by one review only | 15 |
| Contributed by A alone | 6 |
| Contributed by B alone | 7 |
| Contributed by C alone | 2 |
| Direct factual conflicts between reviews | 6 (resolved below) |

Severity totals after merge: **6 Critical, 15 High, 16 Medium, 9 Low.**

The twelve findings all three reviews independently identified are the ones to
fix first, listed by severity:

1. **C1** plaintext PIN in a column named `pinHash`
2. **C2** database not encrypted despite the documentation claim
3. **C5** a 5 or 6 digit PIN permanently locks the owner out of the app
4. **C6** `allowBackup="true"` completing the extraction chain
5. **H3** RBAC, manager approval, and HOTP validation not wired to anything
6. **H4** no brute-force protection on PIN entry
7. **H5** duplicate parallel domain hierarchies, with the tested side dead
8. **H9** `syncStatus` exists on one of four Room entities
9. **H11** zero tests outside `:shared-kmp`
10. **M3** `kmp-app-updater` shipping into the F-Droid flavor
11. **M8** HOTP validation is timing-unsafe, replayable, wrong window
12. **M11** no `res/` directory and no `strings.xml`, so localisation is blocked

---

## Conflicts between the three reviews, and how they were resolved

### K1. Test coverage: 32 tests on dead code, or 51 including new live coverage?

A measured 32 `@Test` in 8 files at `b7f756e` and noted that part of that
coverage exercises dead code. C stated that PR #148 added tests for
`Role.getDefaultPermissions()` and the `Money` operators. B described the suite
as 32 tests with strong HOTP coverage and happy-path everything else.

**Resolved:** C is right, and A is stale. At `ef89f62` there are 51 `@Test`
across 10 files, including `models/auth/RolePermissionsTest.kt` and
`models/MoneyTest.kt`, which are the two PR #148 additions. A's underlying point
survives unchanged: all 51 tests are still in `:shared-kmp`, and
`NoSyncBackendTest` still targets the dead `shared.domain.sync.NoSyncBackend`.
See **H11**.

### K2. Room schema export: current, or lagging?

C's L3 claims the committed schema JSON lags the current `DATABASE_VERSION`. B
states the export is real and the committed JSON matches `@Database(version = 4)`.
A does not dispute the export exists but flags that `2.json` and `3.json` are
identical.

**Resolved:** B and A are correct, C's L3 is **dropped as not reproducible**.
All four schema files exist and `4.json` matches the declared version. A's
observation about the duplicate identity hash stands. See **M10**.

### K3. Is the missing Kotlin Android plugin a build blocker?

A files it as Critical **C4** and predicts a configuration-time failure. B
mentions the same fact but reads it charitably, noting that the modules may be
relying on AGP's built-in Kotlin support, hinted at by
`android.disallowKotlinSourceSets=false` in `gradle.properties`. C does not
mention it at all.

**Resolved:** kept at Critical, with B's caveat recorded in the finding. Neither
review ran Gradle, so this is the single most important thing for a maintainer
to check first. If the build succeeds, downgrade it.

### K4. Severity of the unseeded default location

B rates it CRITICAL. A rates it **H1**, reasoning that it is latent because
nothing inserts items yet.

**Resolved:** kept at High per the master, with B's dissent recorded. A's
reasoning is sound and its evidence is stronger (it verified that `ItemDao` and
`StockMovementDao` have zero consumers).

### K5. Is RBAC enforced in the UI, or nowhere?

B and C both describe RBAC as "enforced only in the UI layer", implying
composables do gate on permissions. A measured `hasPermission` and found **zero
call sites**, including in composables, with `PermissionHelpers.hasPermission`
referenced only from line 28 of its own file.

**Resolved:** A's stricter reading is correct and is what **H3** now says. B's
and C's framing understates the problem: there is no enforcement anywhere, not
even cosmetic.

### K6. File path discrepancies in C

C cites `core-data/.../database/KaupDatabase.kt` as the location of the
`Room.databaseBuilder` call and `feature-auth/.../auth/lock/LockScreen.kt` for
the PIN comparison. A and B both locate the builder in
`android-app/.../di/DatabaseModule.kt:26-33` and the lock screen at
`feature/feature-auth/.../auth/ui/LockScreen.kt`.

**Resolved:** A and B have the correct paths. C's paths appear to be
approximations rather than verified locations, so where C is the only source for
a path, that path is marked unverified below.

---

## Critical findings

### C1. The user PIN is stored and compared in plaintext, in a column named `pinHash`

**Sources: A (C1), B (1.1), C (C2).** All three reviews lead with this.

`OnboardingViewModel.kt:92` writes the owner's PIN straight into the field named
`pinHash`, with the author's own comment `// Note: In production, hash this
properly`. `LockScreen.kt:83` then compares the typed PIN to that column with
`if (pin == user.pinHash)`. A search for `MessageDigest`, `bcrypt`, `scrypt`,
`argon`, `PBKDF2`, `sha256`, and `hashOf` across all Kotlin, Gradle, and TOML
files returns zero matches, re-confirmed at `ef89f62`.

The column name is not aspirational, it is actively misleading: any contributor
reading `UserEntity.pinHash` will assume a digest and write code on that
assumption. This composes with **C2** and **C6** into a single extraction chain
rather than three separate issues. Fixing it later requires a credential
migration, because existing installs hold plaintext values indistinguishable
from digests without a format marker.

**Adds (B):** the comparison happens **inside the Composable**, not in a
repository, so there is no layer where a hardening fix could be applied centrally.
**Adds (C):** `SECURITY.md` makes no mention of the plaintext storage, so a user
reading the project's own security documentation would not learn about it.

**Fix.** PBKDF2 via `javax.crypto.SecretKeyFactory` in `:core-data` (no new
dependency, available on all supported API levels) with a per-user random salt
column and a stored iteration count, or a KMP Argon2/bcrypt binding subject to
the licence rule in `CONTEXT.md`. Store salt and algorithm parameters, compare in
constant time, move the comparison out of the Composable into a repository,
rename the column as part of the format migration, and add the one-line test
that asserts the stored value is not equal to the input PIN.

### C2. The database is not encrypted, contradicting two explicit documentation claims

**Sources: A (C2), B (1.2), C (C1).**

`CONTEXT.md:20` states `**Database**: Room (local, encrypted)` and `README.md:23`
repeats the promise to users. `android-app/.../di/DatabaseModule.kt:26-33` builds
the database with no `openHelperFactory` and no passphrase. A search for
`sqlcipher`, `zetetic`, `SupportFactory`, `openHelperFactory`, and `passphrase`
across all Kotlin, Gradle, and TOML files returns zero matches, and
`gradle/libs.versions.toml` has no encryption dependency.

Critical for two distinct reasons. The technical exposure is that
`kaup_database` is a plaintext SQLite file holding user records with plaintext
PINs. The second reason is that this is a **false security claim made to end
users in the README of a privacy-positioning product**, which is a more serious
category of defect than an unimplemented feature.

**Adds (B):** the exposure includes `permissionsOverride` and HOTP counters, not
just PINs, and `SECURITY.md` repeats the encrypted-database claim as well, so
three documents are wrong rather than two.
**Note:** the repository already contains most of the fix. `KeystoreManager` is a
correct AES-256-GCM implementation backed by the Android keystore, so passphrase
management is largely solved and simply not wired to the database.
**Path note:** C cites `core-data/.../KaupDatabase.kt` for the builder; the
verified location is `android-app/.../di/DatabaseModule.kt` (see **K6**).

**Fix.** Add SQLCipher, generate a random database passphrase, seal it with the
existing `KeystoreManager`, and pass it via `openHelperFactory`. If that is
deferred past alpha, change `CONTEXT.md:20`, `README.md:23`, and `SECURITY.md`
**today** to say "planned", because those lines are currently untrue in
user-facing documents. Withdrawing a false security claim is urgent even when
implementing it is not.

### C3. A GPL v3 project ships no LICENSE file

**Sources: A (C3), B (§5).** C does not mention it.

There is no `LICENSE` file. A case-insensitive search for any path containing
`licen` returns exactly one result, `docs/adr/ADR-006-gpl-v3-license.md`, which
is a decision record and not a licence. Meanwhile the repository asserts GPL v3
in five places and links the non-existent file three times (`README.md:9`, `:25`,
`:181`, `:184`, `:187`). `README.md:187` states the full licence text is in a
file that does not exist, and all three links render as 404s on GitHub.

This is a legal defect, not a cosmetic one. Without a licence file the default
position is exclusive copyright, so the code is technically not open source
whatever the badge says. The repository is already accepting contributions
through `CONTRIBUTING.md` and issue and PR templates, and every contribution
accepted before a licence lands is made under unclear terms, which requires
contacting every contributor to unwind. F-Droid, which the project explicitly
targets with a dedicated flavor, requires a machine-readable licence to package
an application, so the `fdroid` flavor cannot ship as things stand.

**Fix.** Commit the verbatim GPL v3 text as `LICENSE` in the repository root.
Five minutes, and the cheapest Critical in this document.

### C4. No module applies the Kotlin Android plugin

**Sources: A (C4), B (§5, as part of the toolchain finding).** C does not mention it.

Every Android module contains Kotlin sources under `src/main/kotlin/` and none of
them applies the Kotlin Android plugin. The plugin is declared at the root with
`apply false` (`build.gradle.kts:5`) and then never applied. Re-verified at
`ef89f62`: searching all Gradle and TOML files for `kotlin.android`,
`kotlin("android")`, and `org.jetbrains.kotlin.android` yields exactly two hits,
the `apply false` line and the catalog definition at `libs.versions.toml:46`.

| Module | Plugins applied | `kotlin-android`? |
|---|---|---|
| `:android-app` | `android.application`, `ksp`, `hilt`, `compose.compiler` | no |
| `:core:core-data` | `android.library`, `ksp` | no |
| `:core:core-ui` | `android.library`, `compose.compiler` | no |
| `:core:core-network` | `android.library`, `ksp`, `hilt` | no |
| `:feature:feature-auth` | `android.library`, `ksp`, `hilt`, `compose.compiler` | no |

`:shared-kmp` is unaffected because it applies `kotlin.multiplatform`.

Predicted consequences, in descending confidence: `compose.compiler` is a Kotlin
compiler plugin and applying it without a Kotlin plugin is a configuration-time
failure; without `kotlin-android` no Kotlin compilation task is registered, so
`src/main/kotlin` is not compiled; KSP and Hilt then have no sources, so Room
DAOs and the Hilt graph are not generated.

**Divergence (B):** B reads the same fact more charitably, suggesting the modules
may rely on AGP's built-in Kotlin support, which `android.disallowKotlinSourceSets=false`
in `gradle.properties` hints at. See **K3**.
**Honest caveat:** no review ran Gradle. This is derived from configuration
alone. It is filed Critical rather than as a question because **H10** establishes
there is no CI, so nothing has ever verified that the committed tree builds.

**Fix.** Verify first with `./gradlew assembleGithubDebug` after fixing **H2**.
If it fails, add `alias(libs.plugins.kotlin.android)` to all five Android
modules. Either way, add the CI workflow from **H10** so the class of error
cannot recur.

### C5. A 5 or 6 digit PIN permanently locks the owner out of the app

**Sources: A (C5), B (1.3, as a parenthetical), C (M2, as Medium).**

Onboarding gates continue on a minimum of four digits (`OnboardingViewModel.kt:30`)
and accepts up to six (`OnboardingViewModel.kt:56`). The lock screen evaluates
the PIN only when the length is exactly four (`LockScreen.kt:82`). An owner who
follows the wizard and chooses a five or six digit PIN, which the wizard
explicitly permits, can never unlock the app again: on the fifth digit the
comparison branch is simply never entered. There is no submit button, no error,
no PIN reset flow, no recovery code, and no alternative authentication anywhere
in the repository. The only escape is clearing app data, which destroys the
sales and stock history.

**Divergence:** A rates this Critical because it is reachable through the
documented happy path and causes total loss of access and data. C rates the same
underlying mismatch Medium and frames it as a consistency problem
("define a single `PinPolicy` constant") without identifying the lockout. B
identifies the lockout ("a lockout-by-bug") but files it under the brute-force
finding. **A's severity is kept**: C's framing misses the consequence.

**Fix.** Make the two bounds agree and validate in one place, ideally an explicit
confirm action rather than a length trigger. Add C's suggested single `PinPolicy`
constant in `:shared-kmp` so the two screens cannot drift again, and prefer 6
digits given **H4**. Add a test that onboards a 6 digit PIN and unlocks with it.

### C6. `allowBackup="true"` makes the plaintext database and PIN extractable

**Sources: A (C6), B (1.2), C (H4).**

`android-app/src/main/AndroidManifest.xml:7` enables backup with no
`android:fullBackupContent` and no `android:dataExtractionRules`, re-verified at
`ef89f62`, so the default applies and the app's databases directory is included.

On its own this is routine hardening. Here it completes the chain from **C1** and
**C2**: the backup contains `kaup_database`, that database is unencrypted, and
the user table holds the PIN in plaintext. The credential is extractable from a
device backup without root, and it is copied to whatever cloud backup destination
the device is configured to use, which directly contradicts the "your data, your
device" promise.

**Adds (B):** names the vector precisely as Android Auto Backup uploading the
database off-device.

**Fix.** Set `android:allowBackup="false"`. If backup is wanted later as a
user-facing feature, and `ROADMAP.md` suggests it is, implement it as an explicit
encrypted export rather than relying on platform backup, and exclude the database
via `dataExtractionRules` in the meantime.

---

## High findings

### H1. The default location is never seeded, while `locationId` is a non-null foreign key

**Sources: A (H1), B (3.4).** C does not mention it.

`CONTEXT.md:184-186` states that a single default location is seeded on first
launch and instructs contributors never to omit `locationId`. The entities follow
the instruction; the seeding does not happen. `ItemEntity.kt:11-22` declares a
non-null `locationId` with a cascading foreign key to `locations`. No
`RoomDatabase.Callback`, no `addCallback`, no `createFromAsset`, and no
prepopulation exists anywhere. `LocationDao` is referenced in exactly three
places, its own declaration, the abstract accessor on `KaupDatabase`, and the
Hilt `@Provides` method, and is never injected into any consumer. Room enables
foreign key enforcement by default, so the first item insert fails with a
constraint violation.

**Divergence:** B rates this CRITICAL. A rates it High because it is latent:
`ItemDao` and `StockMovementDao` have zero consumers outside DI, and the POS,
inventory, reports, and settings destinations are all `DummyScreen` placeholders
(`KaupAppShell.kt:130-133`). **A's severity is kept**, see **K4**. It becomes a
hard blocker the moment the first inventory or POS write lands, and it will
present as a confusing constraint-violation crash in a feature that looks correct.

**Fix.** Add a `RoomDatabase.Callback` in `DatabaseModule` that inserts the
default location in `onCreate`, and add an instrumented test that opens a fresh
database and inserts one item. Seed in the Room callback rather than in
onboarding, because the ADR-018 destructive-migration policy recreates the
database and the seed must survive that.

### H2. `gradlew` is committed non-executable, so every documented build command fails

**Sources: A only.**

`git ls-files --stage gradlew` returns `100644`, not `100755`, re-verified at
`ef89f62`. On a fresh clone on Linux or macOS every `./gradlew` invocation fails
with a permission error, and both `README.md` and `CONTRIBUTING.md` instruct
contributors to run `./gradlew` commands.

Filed High rather than Low because it lands on first contact, affects every
contributor on the two most common development platforms, and combines badly with
**C4**: a new contributor hits a permission error, fixes it, and then hits a
build failure.

**Fix.** `git update-index --chmod=+x gradlew` and commit. The mode is a property
of the git index, so fixing the local filesystem permission does not fix the clone.

### H3. RBAC, manager approval, and HOTP validation are entirely dead code

**Sources: A (H3), B (1.4, 1.5), C (C4, H2).** All three found this, with the
widest framing gap of any finding in the merge.

`CONTEXT.md:120-134` describes RBAC as the mechanism gating every restricted
action, with `ManagerApprovalOverlay` and offline HOTP verification. Four commits
are dedicated to building it. None of it is connected to anything.

| Component | Definition | Production call sites |
|---|---|---|
| `ManagerApprovalOverlay` | `core-ui/.../components/ManagerApprovalOverlay.kt:25` | **0** |
| `PermissionHelpers.hasPermission` | `core-ui/.../auth/PermissionHelpers.kt:10` | **0** (used only by line 28 of its own file) |
| `SessionManager.hasPermission` | `core-data/.../auth/SessionManager.kt:30` | **0** |
| `HOTPGenerator.validateCode` | `shared-kmp/.../HOTPGenerator.kt:32` | **0** (4 references, all in `HOTPGeneratorTest`) |

Every action in the app is currently unguarded, and there is no path by which a
manager override can be requested or verified at runtime. The HOTP provisioning
and override-code-generation screens exist, so a manager can generate a code, but
nothing anywhere can validate one. `KaupAppShell.kt:67` says the quiet part out
loud: `// Simulate entering PIN and successful unlock`.

**Divergence:** B (1.5) and C (H2) both describe RBAC as "enforced only in the UI
layer", which implies composables do gate on permissions. A's measurement shows
zero call sites anywhere, including composables. **A's stricter finding is
correct**, see **K5**.
**Adds (B):** `ManagerApprovalOverlay.kt:64-65` hands any non-empty typed string
to `onApprove` with no HOTP check, no counter consumption, and no throttling,
which RFC 4226 §7.3 requires. So the overlay is not merely unwired, it would pass
anything if it were wired.

**Why this matters beyond "unfinished".** The risk is that RBAC is recorded as
done. Four commits say `feat(auth): implement RBAC permission checks` and the
code looks complete in isolation, so when POS and inventory are built on top the
natural assumption will be that checks are enforced upstream. Nothing will fail
loudly to correct that assumption.

**Fix.** Wire one restricted action end to end before building more surface: gate
a single destructive operation on `SessionManager.hasPermission`, present
`ManagerApprovalOverlay` when it returns false, validate the entered code through
`HOTPGenerator.validateCode`, and add throttling. Then enforce in the use-case
layer in `:shared-kmp` (C's recommendation) so UI checks are a convenience only.

### H4. No rate limiting or lockout on PIN entry

**Sources: A (H4), B (1.3), C (C3).**

`LockScreen.kt:83-89` handles a wrong PIN with a flat 500 millisecond cosmetic
delay and a field reset. There is no attempt counter, no escalating backoff, no
lockout, and no persistence of failed attempts across process death. A 4 digit
PIN has 10,000 combinations, so at roughly two attempts per second an exhaustive
search takes under an hour and a half, and it survives app restarts because
nothing is recorded. `SECURITY.md` does not mention attempt limiting, so this is
not a documented deferral.

**Adds (B):** the lock screen auto-submits at exactly 4 digits, which removes even
the friction of a submit tap from an automated attack.

**Fix.** Persist a failed-attempt count and a lockout-until timestamp on the user
row so they survive restarts, apply escalating backoff after roughly 5 failures,
and add an owner-override path (C's suggestion). Because Kaup is offline-first the
lockout must be local and time-based, and the timestamp should be checked against
elapsed realtime rather than wall-clock time so changing the device clock does not
clear it.

### H5. Duplicate parallel domain hierarchies: the ADR-canonical interfaces are the dead ones

**Sources: A (H5), B (§4), C (M1, M4).**

`:shared-kmp` contains two parallel sets of the same core abstractions in
different packages. In every case the version the app uses is the thinner,
undocumented one, and the version the ADRs and `CONTEXT.md` describe is dead.

| Abstraction | Live in app | Dead |
|---|---|---|
| `Permission` | `shared.domain.models.auth.Permission` (31-33 values, 9 imports from app code) | `shared.models.Permission` (10 values, 0) |
| `Role` | `shared.domain.models.auth.Role` = OWNER/MANAGER/CASHIER/**CREW** | `shared.models.Role` = OWNER/MANAGER/CASHIER/**WAITER** |
| Role defaults | `Role.getDefaultPermissions()` (MANAGER minus `USERS_*`) | `RoleDefaults` (MANAGER = full set, 0 call sites) |
| `SyncBackend` | `shared.sync.SyncBackend`, Hilt-bound in `NetworkModule:17-19` | `shared.domain.sync.SyncBackend` (rich `PendingRecord`/`SyncResult`) |
| `NoSyncBackend` | `core-network/.../backend/NoSyncBackend.kt` | `shared.domain.sync.NoSyncBackend` |
| `NotificationBackend` | `shared.sync.NotificationBackend` | `shared.domain.notification.NotificationBackend` |

Three consequences, in order of importance:

1. **The test suite tests the dead implementation.** `NoSyncBackendTest` exercises
   `shared.domain.sync.NoSyncBackend`, which the app never instantiates. The
   `NoSyncBackend` Hilt actually binds, in `:core-network`, has no tests. The
   green test is not evidence about shipped behaviour. This is C's M4 stated from
   the other direction.
2. **`CONTEXT.md` documents the dead side.** It names `RoleDefaults` as the source
   of truth, and `CONTEXT.md:137` lists `WAITER` as a built-in role. `WAITER`
   exists only in the dead enum; the live enum uses `CREW` (renamed in ADR-009).
   A contributor following the architecture contract will edit the wrong file and
   observe no effect. The two definitions also disagree on policy:
   `RoleDefaults` grants MANAGER everything while the live
   `getDefaultPermissions()` strips `USERS_*`.
3. The `:shared-kmp/sync-contracts` source set that `CONTEXT.md` names as the home
   of all pluggable interfaces does not exist at all.

**Adds (B):** `NoSyncBackend` and `LocalNotificationBackend` each exist in
**triplicate** across `:shared-kmp` and `:core-network`, and the dead
`shared.models.*` types are what the domain engines (`SalesCalculator`,
`InventoryEngine`, `ConflictResolver`) are built on. Wiring those engines into
`:feature-pos` and `:feature-inventory` later will therefore require bridging two
type systems, which is the real cost of leaving this in place.
**Note:** the two `LocalNotificationBackend` files in `androidMain` and `jvmMain`
are **not** an instance of this problem. Per-target implementations are ordinary
KMP structure and are correct.

**Fix.** Delete one side in a single commit, keeping the richer `domain.*`
contracts (B's recommendation) and repointing the tests at the survivor. Then
correct `CONTEXT.md`, including the `WAITER`/`CREW` divergence and the manager
permission set. Do this before more features land, because every new import is a
coin flip about which hierarchy it picks.

### H6. Money arithmetic is routed through `Double`, and the totals do not reconcile

**Sources: A (H6), B (§2).** C's L1 covers a different `Money` defect, merged as
**L7**.

`Money` itself is well chosen: a `@JvmInline value class` over `Long` minor
units. The problems are around it. All arithmetic routes through `Double`
intermediates re-quantised with `roundToLong()` (`SalesCalculator.kt:13,19,38,51-52,62,68,72`),
and `LineItem.quantity` plus tax and discount rates are `Double`.

Four concrete defects:

- **The reported totals do not add up when any inclusive tax is present.**
  `SalesCalculator.kt:78-88` returns `subtotal` gross, `taxTotal` including
  inclusive tax, but `finalTotal` excluding it. One item priced 120 with 20
  percent inclusive tax gives subtotal 120, discount 0, tax 20, total 120. A
  receipt printing those four fields shows numbers that do not reconcile. For a
  POS, where the receipt is a legal document in many jurisdictions, this needs a
  defined contract.
- **Stacked inclusive taxes are each computed on the full gross.** Lines 65-75
  compute every inclusive tax against `finalTaxableItemValue` independently. A
  gross of 121 with two 10 percent inclusive taxes yields 22, where an additive
  regime gives 20.17 and a compounding one gives 21, so the result is wrong under
  either interpretation. B's worked example (1100 with two 10 percent inclusive
  taxes reporting 200 instead of about 183) is the same bug.
- **Per-line pro-rating of the cart discount does not sum to the cart total.**
  Line 62 rounds each line's share independently while line 48 computes
  `totalTaxableValue` directly from the cart. Three lines of 100 with a cart
  discount of 10 give a per-line taxable sum of 291 against a `totalTaxableValue`
  of 290, so tax is computed on a base one minor unit larger than the base the
  total uses.
- **A negative quantity silently zeroes the line and corrupts the discount total.**
  With a negative `rawSubtotal` the guard on line 25,
  `if (itemDiscount > rawSubtotal) itemDiscount = rawSubtotal`, inverts:
  `itemDiscount` becomes the whole negative subtotal, line 28 returns exactly 0,
  and line 27 has already added the negative value to `discountTotalMinorUnits`.
  Returns and refunds, which a POS must handle, vanish from the line total and
  appear as a negative discount. B additionally notes the same sign bug at line 43
  and that line 51's `> 0` guard skips the discount ratio for negative carts, so
  tax and total then use different bases.

**Adds (A):** `Money` carries no currency and no scale, so two different
currencies can be summed with `plus` with no error even though currency is
captured during onboarding and `CONTEXT.md` targets any country.
**Adds (B):** `roundToLong` is half-up toward positive infinity, not banker's
rounding, and asymmetric for negatives, so a sale and its equal-and-opposite
refund can differ by a minor unit. Drift is bounded to about one minor unit per
rounding site, which is a defensible design but is not exact and is undocumented.
**Timing (B):** none of this domain logic is referenced outside `:shared-kmp`
tests yet, so this is the cheapest it will ever be to fix.

**Fix.** Define the rounding and presentation contract first, in an ADR, then test
it: decide whether `subtotal` is net or gross, compute inclusive tax once per item
from a combined rate, allocate cart discounts with a largest-remainder method,
reject or explicitly support negative quantities, and add the invariant test that
`subtotal - discountTotal + taxTotal == finalTotal`. That single property test
would have caught three of the five problems above.

### H7. Inventory stock is accumulated in `Double`, contradicting ADR-002

**Sources: A (H7), B (§2).**

`InventoryEngine.computeStock` folds quantities into a `Double`
(`InventoryEngine.kt:9-18`), as do `computeStockAsOf` and
`ConflictResolver.detectNegativeStockViolations`. ADR-002 specifies `BigDecimal`.
A verified the consequences by executing the same IEEE-754 arithmetic:

```text
Ledger: receive 0.1, receive 0.1, receive 0.1, sell 0.3   (exactly balanced)
computeStock result:  5.551115123125783e-17     == 0.0 ? False

Same four movements, reordered: 2.7755575615628914e-17
Two orderings equal? False

Ledger: receive 0.7, receive 0.1, sell 0.8   (exactly balanced)
result: -1.1102230246251565e-16   flagged as negative-stock violation? True
```

So a balanced ledger does not report zero stock, the same movements in a
different order produce a different answer, and a balanced ledger is reported as
an oversell. Any out-of-stock comparison against `0.0`, any equality check, and
any unrounded display will be wrong for ordinary decimal weights, which is the
normal case for the grocery and market-stall users Kaup targets.

**Adds (A):** the sort keys differ between the two classes that replay the same
log. `computeStock` sorts by `timestamp` alone while
`ConflictResolver.sortDeterministically` sorts by `timestamp`, then `deviceId`,
then `id`, so two devices replaying an identical log with any tied timestamps can
compute different stock values, which directly undermines the multi-device
offline-first premise.
**Adds (B):** the movement enum (`StockMovement.kt:5-7`) has no VOID or RETURN
type and the engine ignores `type` entirely, so a voided sale can only be modelled
as a compensating movement that corrupts receiving reports. See also **M14**.

**Fix.** Represent quantities as scaled integers (as `Money` already does
correctly) or as `BigDecimal` per ADR-002, and make both classes use one shared
canonical ordering. Scaled integers fit better here because they keep
`:shared-kmp` free of platform decimal differences and make equality meaningful.

### H8. `ConflictResolver` contains no conflict resolution

**Sources: A (H8), B (§2).**

`CONTEXT.md:113` describes `ConflictResolver` as the class that resolves
simultaneous writes from multiple offline devices. The class has two methods,
`sortDeterministically` and `detectNegativeStockViolations`. It orders events and
reports which crossed zero. It never merges, chooses a winner, or reconciles
anything, and the documented `CONFLICT` to `SYNCED` transition attributed to this
class is performed by nothing.

`detectNegativeStockViolations` also under-reports: it flags a movement only when
`previousStock >= 0.0 && currentStock < 0.0`, so it reports the single crossing
event and silently ignores every subsequent oversell while stock stays negative.
If three devices each sell the last unit offline, one violation is reported, not
two. (Also indexed as **L6**.)

**Adds (B):** what exists handles `StockMovement` only, with no LWW, no merge, and
no `PendingRecord`/`RemoteUpdate` handling, and it relies on device wall-clock
timestamps with no hybrid-logical-clock or skew mitigation.
**Note:** `sortDeterministically` is good code and the right foundation. Ordering
by timestamp, then `deviceId`, then `id` gives a stable total order across devices
without coordination.

**Fix.** Decide the resolution policy explicitly in an ADR, since this is a domain
decision: stock movements are an append-only log and inherently commutative, so
they should simply be merged, while mutable records such as items and prices need
a rule (last-write-wins with a vector clock, or per-field merge). Then either
implement resolution here or rename the class to `StockLedgerAuditor`, which is
what it currently is, and correct `CONTEXT.md`.

### H9. `syncStatus` exists on one of four Room entities

**Sources: A (H9), B (3.4), C (M3).**

`CONTEXT.md:145-157` states that every syncable Room entity carries a
`syncStatus` column and describes the `PENDING` to `SYNCED` lifecycle with
WorkManager detecting `PENDING` rows. A repository-wide search returns two
matches, re-verified at `ef89f62`:

```text
core/core-data/.../entities/LocationEntity.kt:13:    val syncStatus: String = "PENDING"
shared-kmp/.../models/StockMovement.kt:26:            val syncStatus: SyncStatus = SyncStatus.PENDING
```

Of the four Room entities only `LocationEntity` has the column. `ItemEntity`,
`StockMovementEntity`, and `UserEntity` do not. The second match is the shared
**domain** model, not a persisted entity, which sharpens the gap: `StockMovement`
carries a typed `SyncStatus` that has nowhere to be stored, so the value is lost
on every write and reconstructed as `PENDING` on every read.

Two related observations: `LocationEntity.syncStatus` is a `String` while the
domain model uses an enum, with no type converter, so the two representations are
unrelated; and `UserEntity` has no `locationId`, so users are not location-scoped
despite `CONTEXT.md` requiring location-awareness.

**Adds (B):** because no entity except `locations` carries `syncStatus`, the
documented "WorkManager detects PENDING records" behaviour is currently
**unimplementable**, not merely unimplemented. See **H12**.

**Fix.** Add `syncStatus` to the three entities that lack it, add a type converter
so the enum is the single representation, and add `locationId` to `UserEntity`.
Doing this during the destructive-migration alpha window costs nothing; after
`0.2-alpha` it requires real migrations.

### H10. There is no CI, and the README advertises a badge for a workflow that does not exist

**Sources: A (H10), B (§5).**

`.github/` contains only `ISSUE_TEMPLATE` and `pull_request_template.md`, with no
`workflows` directory, re-verified at `ef89f62`. The README renders a CI status
badge pointing at a `ci.yml` that does not exist, and `CONTRIBUTING.md:200` makes
"CI passes, all unit and integration tests green" a merge requirement, a checkbox
that can never be satisfied.

This finding is the reason several others exist. Nothing has ever checked that
the tree compiles (**C4**), that the wrapper is executable (**H2**), or that the
existing tests still pass. A single workflow running `./gradlew build` would have
caught the most severe build-level findings on the commit that introduced them.

**Adds (B):** there is no detekt, ktlint, Android lint, or coverage tooling
anywhere either, and no automated enforcement of the module-boundary or F-Droid
rules that the project calls non-negotiable.

**Fix.** Add `.github/workflows/ci.yml` running assemble, release assemble, and
`test` on pull requests, point the badge at it, and add ktlint or detekt in the
same workflow. This is the highest-leverage single change in this document,
because it converts most of the other findings from invisible to self-reporting.

### H11. Zero tests in all five Android modules

**Sources: A (H11), B (§2 test-quality note), C (test-coverage note).**

**Updated at `ef89f62`.** A measured 32 `@Test` in 8 files at `b7f756e`. The
current count is **51 `@Test` across 10 files**, after PR #148 added
`models/auth/RolePermissionsTest.kt` and `models/MoneyTest.kt`, exactly as C
stated (see **K1**). The structural finding is unchanged: **all** of them are in
`:shared-kmp`.

| Module | Test files |
|---|---|
| `:shared-kmp` | 10 files, 51 `@Test` |
| `:core:core-data` | 0 |
| `:core:core-ui` | 0 |
| `:core:core-network` | 0 |
| `:feature:feature-auth` | 0 |
| `:android-app` | 0 |

Testing the pure domain module first is the right instinct, but the untested
modules are where this document's most severe findings live: **C1**, **C5**,
**H1**, and **H9**. Each is the kind of defect a single cheap test catches: one
Room instrumented test inserting an item into a fresh database, one test
asserting the stored credential differs from the entered PIN, one test onboarding
a 6 digit PIN and unlocking with it.

**Adds (B):** test *quality* in the existing suite is uneven. The HOTP suite is
strong because it asserts published RFC 4226 Appendix D vectors rather than
whatever the implementation produces. The rest is happy-path: `SalesCalculator`
numbers all divide evenly, and there is no rounding-tie, negative-quantity,
multi-tax, or discount-plus-tax interaction case, so the entire
proportional-allocation path is never exercised with a nonzero ratio.
**Adds (A):** part of the suite covers a dead code path (**H5**), so effective
coverage of shipped behaviour is lower than the count suggests.

**Fix.** Add a Room instrumented test source set for `:core:core-data` and unit
tests for the auth view models, add the missing edge cases to
`SalesCalculatorTest`, and wire everything into the CI workflow from **H10**.

### H12. The sync stack is inert end to end

**Sources: B (3.1) only.** New to the merged list. Neither A nor C examined
`:core-network` wiring.

Offline-first (non-negotiable #1) is not violated, because no UI action gates on
the network and the default backend is a no-op. But nothing in the sync path can
run, for four independent reasons:

- `SyncManager.schedulePeriodicSync()` and `triggerImmediateSync()` have **zero
  callers**, re-verified at `ef89f62`: the only matches are the declarations
  themselves in `core/core-network/.../sync/SyncManager.kt`.
- The Hilt worker factory is configured in `KaupApplication.kt:10-18`, but the
  manifest never removes `androidx.work.WorkManagerInitializer`, so the default
  factory runs and `@HiltWorker SyncWorker` cannot be instantiated. Confirmed:
  the manifest contains no `provider` node at all.
- There is no `sync_queue` table. The only reference is a comment,
  `SyncWorker.kt:22: // Note: In a real flow, we query sync_queue via core-data DAOs.`
- No entity except `locations` carries `syncStatus` (**H9**), so "WorkManager
  detects PENDING records" has nothing to detect.

The live `LocalNotificationBackend` is in the same state: it posts to a channel
nobody creates, and the manifest declares no `POST_NOTIFICATIONS` permission, so
notifications are a silent no-op.

**Why High.** Like **H3**, the risk is that this is recorded as built. The
classes, the worker, and the DI wiring all exist and read as complete, so the
first feature that needs sync will assume the queue works.

**Fix.** Remove the default WorkManager initializer in the manifest so the Hilt
factory is used, call `schedulePeriodicSync()` from application start, create the
`sync_queue` table and the notification channel, and declare
`POST_NOTIFICATIONS`. Sequence this after **H9**, since the queue needs the
column.

### H13. Override codes are reissued identically within a session, because the HOTP counter is read from a stale snapshot

**Sources: B (1.4) only.** New to the merged list. A's **M8** covers the
validation side of counter handling; this is the generation side.

`OverrideCodeGenerationViewModel.generateCode()` reads
`sessionManager.currentUser.value.hotpCounter`, generates the code, then writes
`counter + 1` through `userDao.updateUserHotpCounter(...)`. It never refreshes the
session. `SessionManager.kt:20-23` populates `currentUser` once at login, so the
snapshot still holds the pre-increment counter. Verified at `ef89f62`.

Every "Generate New Code" tap within a session therefore re-reads the same stale
counter and reissues the **identical** code, which breaks single-use semantics
before the code even reaches a validator. Combined with **M8**, where validation
never consumes the counter either, an override code is effectively permanent in
both directions.

**Fix.** Have the DAO write be the source of truth: re-read the user row (or make
`currentUser` a `Flow` off the DAO) after incrementing, and increment atomically
in a single transaction that returns the new value.

### H14. `SECURITY.md` documents security controls that do not exist

**Sources: B (§5) only.** New to the merged list. A's **M5** covers documentation
overreach in general, but not this file specifically, and the security case is
materially more serious than the feature case.

`SECURITY.md` asserts an encrypted database, AES-encrypted backups, an audit log,
biometric authentication, and a Ktor server with HTTPS and JWT. Of these only the
HOTP-secret Keystore encryption is implemented. There is no encrypted database
(**C2**), no backup encryption (the app instead allows plain platform backup,
**C6**), no audit log anywhere in the repository, no biometric code path, and no
`ktor-server` project at all (**M5**).

This is the same category of defect as **C2**: a false security claim in a
user-facing document from a project whose entire positioning is privacy and data
ownership. It is filed High rather than Critical only because, unlike **C2**, it
does not by itself describe an exploitable exposure.

**Fix.** Rewrite `SECURITY.md` to describe the actual `0.1-alpha` state, moving
everything unimplemented into an explicitly labelled "planned" section, and do it
in the same commit as the **C2** documentation withdrawal.

### H15. The toolchain versions are badly skewed and the build is unproven

**Sources: B (§5) only.** New to the merged list. Related to **C4**, which is
about a missing plugin rather than versions.

`gradle/libs.versions.toml` pins `agp = "9.2.0"` and `hilt = "2.59.2"`, and the
wrapper uses `gradle-9.5.0`. B could not confirm these during its own review,
but all three are real published releases: AGP 9.2.0 (April 2026, requires
Gradle 9.4.1 or newer), Gradle 9.5.0 (28 April 2026), and Hilt 2.59.2
(20 February 2026). So the "unverifiable" half of B's finding does not stand,
and the versions are internally consistent with each other.

What does stand is the skew: that 2026 build toolchain sits alongside Kotlin
2.1.0 (November 2024) and compose-bom 2024.09.02 (September 2024), roughly
eighteen months older, and AGP 9.2.0's headline compatibility work targets the
Kotlin 2.1.x line rather than pinning it. Combined with **C4** (no Kotlin
plugin), **M1** (missing `proguard-rules.pro`), and **H10** (no CI has ever built
the tree), the build should still be treated as unproven rather than working,
because nothing has resolved the dependency graph end to end.

B also notes that no Android module applies the Kotlin plugin and infers reliance
on AGP built-in Kotlin from `android.disallowKotlinSourceSets=false`; that thread
is tracked in **C4** and **K3**.

**Fix.** Realign Kotlin and compose-bom with the 2026 build toolchain, resolve
the whole catalog against a real dependency resolution, and let the CI workflow
from **H10** be the proof. This finding closes itself the first time CI goes
green.

---

## Medium findings

### M1. The release build cannot succeed: `proguard-rules.pro` does not exist

**Sources: A (M1), B (§5).**

`android-app/build.gradle.kts:36-40` sets `isMinifyEnabled = true` and names
`proguard-rules.pro`. A search for any path matching `proguard*` returns nothing,
so the release build fails on a missing file. Once the file exists it will also
need keep rules for Room entities and for the reflective enum access in **M4**,
because R8 will otherwise rename the enum constants that `enumValueOf` looks up
by name.

**Fix.** Add `android-app/proguard-rules.pro`, even if initially empty, and build
a release variant in CI so this is caught.

### M2. Build flavors are declared but no flavor source sets exist

**Sources: A (M2), B (3.3).**

Three flavors are declared (`android-app/build.gradle.kts:21-34`): `github`,
`fdroid`, `playstore`. `android-app/src/` contains exactly one directory, `main`.
`CONTEXT.md:166-168` instructs contributors to use flavor-specific Hilt modules in
`:android-app/src/<flavorName>/` and gives `src/github/GitHubUpdateModule.kt` as
the example. No such directory or file exists.

So the flavor mechanism that the third non-negotiable depends on for keeping
proprietary code out of `fdroid` is declared but not usable, and the documented
`UpdateChecker` bindings (`GitHubUpdateChecker`, `NoOpUpdateChecker`) do not exist
in any form.

**Adds (B):** `GitHubUpdateChecker` exists as a stub returning `UpToDate` and,
lacking a flavor source set, is compiled into all three flavors including
`fdroid`. **Divergence:** A states the `UpdateChecker` implementations do not
exist at all while B describes a stub; B's reading is the more specific one and
the practical consequence (github-only code in the fdroid build) is the same.

**Fix.** Create the three source sets and move the flavor-specific Hilt bindings
into them. Sequence this with **M3**, which is the same fix from the dependency side.

### M3. The app updater ships in the F-Droid flavor, violating a stated non-negotiable

**Sources: A (M3), B (3.3), C (H3).**

`CONTEXT.md:164` states the `kmp-app-updater` dependency must never be added to
the `fdroid` flavor. It is declared at `shared-kmp/build.gradle.kts:23` inside
`commonMain.dependencies`, re-verified at `ef89f62`. `commonMain` is compiled into
every target and every consumer, so the dependency is present in all three
flavors. This is a direct breach of the third non-negotiable as the project
defines it, and F-Droid's inclusion policy independently rejects apps with
self-updating mechanisms.

The mitigating fact, noted by both A and B, is that the dependency is currently
unused by any Kotlin source, so nothing is lost by removing it now.

F-Droid compliance is otherwise good and all three reviews agree on this: no
Firebase, no Google Play Services, and no committed API key or secret anywhere.
This single dependency is the only breach found.

**Fix.** Remove it from `commonMain` while it is unused. When the updater is
actually needed, keep `NoOpUpdateChecker` in shared code and bind the real
implementation only in `:android-app/src/github/` (**M2**).

### M4. `RoleConverter` crashes on any unrecognised role value

**Sources: A only.**

`core/core-data/.../converters/RoleConverter.kt:8` is
`fun toRole(value: String): Role = enumValueOf<Role>(value)`. `enumValueOf` throws
`IllegalArgumentException` for any non-matching value with no fallback, so an
unexpected string in the `role` column crashes on read rather than degrading to a
least-privileged default.

This is more than theoretical. The role enum was renamed from `WAITER` to `CREW`
(ADR-009) and the dead enum still contains `WAITER` (**H5**), so a `WAITER` row is
a realistic input. Once sync exists, role strings will arrive from other devices
possibly running other versions, which is exactly where unknown enum values come
from.

**Fix.** `enumValues<Role>().firstOrNull { it.name == value } ?: Role.CASHIER`,
choosing the least-privileged role rather than the most, and log the miss once
**L3**'s logging abstraction exists.

### M5. The documented product is roughly ten times the implemented one

**Sources: A (M5), B (§5).**

The documentation describes a substantially larger system, written in the present
indicative as descriptions of what the code does rather than as plans.

| Documented | Exists |
|---|---|
| 11 `feature-*` modules | 1 (`:feature:feature-auth`) |
| `ktor-server` Gradle project | absent |
| `:shared-kmp/sync-contracts` source set | absent |
| 26 Room tables (`docs/modules.md`) | 3 of those 26 |
| `PaymentGateway`, `CaptureOnlyGateway` | absent |
| `ReceiptEmailSender`, `IntentEmailSender` | absent |
| `PrinterService` | absent |
| `UpdateChecker` implementations | absent (or a stub, see **M2**) |

`docs/modules.md` is the clearest case: 511 lines documenting per-module
ownership, permissions checked, and Room tables for ten modules that do not
exist. It also does not document `locations`, which is one of the four tables that
does exist and the one with the foreign key everything else depends on.

**Adds (B):** `CONTRIBUTING.md` instructs contributors to run `:feature-pos:test`,
a task that cannot exist, so the documented contributor workflow fails on first
use just as the build does (**H2**).

**Fix.** A one-line status banner at the top of each such file ("Design target,
not yet implemented as of 0.1-alpha") resolves almost all of the documentation
findings here at very low cost, and is much cheaper than either deleting the
design work or implementing it.

### M6. The three backend setup guides describe infrastructure that does not exist

**Sources: A only.**

`docs/setup-tier1.md`, `docs/setup-supabase.md`, and `docs/setup-appwrite.md` give
step-by-step instructions for configuring sync backends. None of the code they
reference exists: no Supabase or Appwrite backend implementation, no `SyncBackend`
variant beyond `NoSyncBackend`, and no settings screen to select one, since
settings is a `DummyScreen`. `docs/setup-supabase.md:84` contains SQL for a `users`
table with a `role` column commented `OWNER, MANAGER, CASHIER, CREW`, a schema for
a server that does not exist in this repository.

These are the highest-risk documents in the repository from a user-trust
perspective, because they read as operational runbooks rather than design notes.
**H12** compounds this: even the local half of the sync path cannot run.

**Fix.** Banner them per **M5**, and say explicitly that no backend implementation
exists yet.

### M7. ADR compliance is partial, and four ADRs have no implementation at all

**Sources: A only.**

Of the 18 ADRs, roughly 68 of 180 individual technical claims are currently true.
ADR-006 (GPL v3 licence), ADR-007, ADR-015 (payment gateway), and ADR-017 have no
corresponding implementation whatsoever. ADR-002's `BigDecimal` requirement is
contradicted by the code (**H7**), and ADR-005's documented HOTP look-ahead window
of 10 is implemented as 5 (**M8**).

ADR-006 is the notable one: an entire decision record justifying GPL v3, with no
licence file in the repository (**C3**).

**Fix.** Add a compliance status line to each ADR, and treat ADR-002 and ADR-005
as bugs to fix rather than documents to amend, since both describe the safer
behaviour.

### M8. HOTP validation is timing-unsafe, permits replay, and uses the wrong window

**Sources: A (M8), B (1.4, 1.6), C (H1).**

The generation side is correct and all three reviews say so: it matches the RFC
4226 Appendix D vectors and the tests assert those published vectors. Validation
has three problems (`shared-kmp/.../HOTPGenerator.kt:32-47`):

1. **Timing-unsafe comparison.** `generated == inputCode` at line 42 is
   `String.equals`, which short-circuits on the first differing character. The
   PIN comparison in **C1** has the same flaw.
2. **No consumption, so codes replay.** The function returns the matched counter
   but nothing persists it or advances the stored counter, and RFC 4226 requires
   the counter to advance on success. An override code shared once with a cashier
   keeps working forever. C states the contract precisely: validation must return
   the matched counter offset and the caller must persist `counter = matched + 1`
   atomically before treating the approval as granted.
3. **Window is 5, ADR-005 documents 10** (`ADR-005:48` and `:86`).

**Adds (B):** RFC 4226 §7.3 also requires throttling on validation attempts, and
there is none.
**Note:** because `validateCode` has no production call sites (**H3**), none of
this is currently exploitable. All of it should be fixed before it is wired up.
See **H13** for the matching defect on the generation side.

**Fix.** Constant-time comparison, atomic counter advance on success, window of
10, and attempt throttling.

### M9. `minSdk` is 24, but the documentation states API 26

**Sources: A (M9), B (§5).**

`CONTEXT.md:17` states API 26. Every module sets `minSdk = 24`, re-verified at
`ef89f62` across all six build files. Worth resolving deliberately rather than by
picking one: API 24 and 25 lack some keystore behaviour that `KeystoreManager`
relies on for its stronger guarantees, and the documented 26 was very likely
chosen for that reason.

`targetSdk = 34` alongside `compileSdk = 36` is also inconsistent with
`CONTEXT.md:18` ("latest stable"), and `tools:targetApi="31"` in the manifest
matches neither.

**Fix.** Raise `minSdk` to 26 to match the security assumption, or lower the
documented figure and verify the keystore code paths on API 24. Reconcile the
three SDK numbers in the same change.

### M10. No `DATABASE_VERSION` constant, and one schema version has no schema change

**Sources: A only.** C's related L3 was dropped, see **K2**.

`CONTEXT.md:180-181` instructs contributors to increment `DATABASE_VERSION` in
`KaupDatabase` on every entity change. No such constant exists; the version is an
inline literal at `KaupDatabase.kt:26`. The exported schemas show a version bump
with no schema change: re-verified at `ef89f62`, `2.json` and `3.json` have
identical `identityHash` values (`d0aaa1cc13fbac7563053865aa1b0a5d`), meaning
version 3 differs from version 2 in no way Room can detect. Harmless under the
current destructive-migration policy, but it would produce a migration with
nothing to migrate later.

Schemas are also exported to `core/core-data/schemas/`, not `/app/schemas/` as
`CONTEXT.md:184` states. The export itself is healthy: `1.json` through `4.json`
are all present and `4.json` matches the declared `version = 4`.

**Fix.** Introduce the `DATABASE_VERSION` constant the contract names, correct the
documented schema path, and collapse or document the no-op version 3.

### M11. No `res/` directory and no `strings.xml`, so localisation is blocked

**Sources: A (M11), B (§5), C (M5).**

There is no `res/` directory and no `strings.xml` anywhere. `CONTEXT.md` lists
"do not write user-facing strings as hardcoded literals, use `strings.xml`" in its
"what not to do" section, and `CONTRIBUTING.md` has a translation-contributions
section. Every user-facing string is a hardcoded Kotlin literal, so there is
nothing for a translator to translate.

**Adds (C):** names the concentrations, `LockScreen.kt` (button labels, error
text) and the onboarding package (step titles, validation messages).

Fixing this early is much cheaper than later: extracting strings after POS,
inventory, and reports are written is a large mechanical change across every
composable, whereas establishing the pattern now costs almost nothing.

**Fix.** Add `res/values/strings.xml` per module, move the existing literals, and
reference via `stringResource(...)`.

### M12. `CONTEXT.md` contradicts itself on `:core-network` dependencies

**Sources: A (M12), B (3.2).**

`CONTEXT.md:39-40` describes `:core-network` as depending on `:shared-kmp` and
`:core-data`. `CONTEXT.md:80` states that `core-*` modules may import
`:shared-kmp` **only**. Both cannot hold, and `:core-network` does declare a
dependency on `:core-data`. This matters because it is the architecture contract
and module boundaries are one of the three non-negotiables: a contributor cannot
follow a rule that contradicts the example beside it.

**Adds (B):** `:core-network` never actually imports anything from `:core-data`,
so the declared dependency is currently unused and the cheapest resolution may be
to delete it and keep the stricter rule.

**Fix.** Either drop the unused dependency and keep `CONTEXT.md:80` as written, or
state the exception explicitly in both places.

### M13. Broken documentation links

**Sources: A (M13), B (hygiene).**

- `docs/adr/ADR-015-payment-gateway-architecture.md.md` has a doubled `.md`
  extension, and `README.md:156` links the single-extension name, so the link 404s.
- `README.md:9`, `:184`, and `:187` all link `LICENSE`, which does not exist (**C3**).
- The README CI badge points at a workflow that does not exist (**H10**).

**Fix.** Rename the ADR file, and let **C3** and **H10** resolve the rest.

### M14. `StockMovementEntity` drifts from the domain `StockMovement` model

**Sources: C (M3), B (§4).** Promoted from a supporting detail in **H9** to its
own finding, because the entity is missing more than `syncStatus`.

`core-data/.../entities/StockMovementEntity.kt` does carry a `type`
discriminator, but as an unvalidated free `String` that cannot safely map to the
domain enum: no type converter, no constraint, and its own comment enumerates
`"SALE"`, `"RECEIPT"`, `"ADJUSTMENT"` while the domain enum value is
`RECEIVING`, so `"RECEIPT"` does not resolve to any enum constant. What is
genuinely absent is a movement direction, a link to the originating transaction,
and the `syncStatus` column the documented sync lifecycle requires.
`InventoryEngine` replays movements to compute stock, and without a direction
field replay semantics rely on signed quantities that nothing validates.

**Adds (B):** the shared `StockMovement` enum has no VOID or RETURN type
(**H7**), so a voided sale can only be modelled as a compensating movement that
corrupts receiving reports.

**Fix.** Replace the `type: String` column with `movementType` as a converted
enum whose constants match the domain enum exactly (this is a replacement, not
an additional field, so the two cannot drift again), add `transactionId` as a
nullable FK and `syncStatus`, add VOID and RETURN to the domain enum, and
increment the database version. Do it inside the destructive-migration window
alongside **H9**.

### M15. `SessionManager` trusts `permissionsOverride` read from the plaintext database

**Sources: B (1.5) only.** New to the merged list.

`SessionManager.login()` (`SessionManager.kt:22`) reads
`UserEntity.permissionsOverride` from the database and trusts it as the session's
permission set. Combined with **C2**, anyone who can write that plaintext SQLite
file can grant themselves the full permission set, and combined with **C6** they
can do it through a device backup round-trip without root.

This is separate from **H3**: even once permission checks are wired up, they will
be reading an attacker-writable source.

**Fix.** This closes largely as a consequence of **C2**. Additionally, treat
`permissionsOverride` as needing integrity protection (a MAC over the row keyed
from `KeystoreManager`), or drop the column and derive permissions from the role
plus a server-signed grant once sync exists.

### M16. The HOTP secret is displayed in plaintext during provisioning and is never zeroed

**Sources: B (1.6) only.** New to the merged list. Related to **L2**, which covers
`FLAG_SECURE` generally.

`HotpProvisioningScreen.kt:57-74` displays the HOTP secret both as a QR code and
as plaintext Base32, on a screen with no screenshot or screen-recording
protection, and the override code screen has none either. `FLAG_SECURE` appears
nowhere in the repository. Separately, the decrypted secret in `rawSecret:
ByteArray` is never zeroed after use, so it lingers in the heap for the lifetime
of the process.

The secret is the root credential for every future manager override, so it is a
higher-value target than any individual code.

**Fix.** Set `FLAG_SECURE` on the provisioning, override-code, and lock screens,
and zero the `ByteArray` in a `finally` block after use. Both are small changes
and should land with **L2**.

---

## Low findings

### L1. `KeystoreManager` could take three further hardening steps

**Sources: A (L1), B (1.6).**

`core/core-data/.../crypto/KeystoreManager.kt` is the strongest code in the
repository. Three optional improvements, none of them a defect:

- `setUserAuthenticationRequired(true)` would bind key use to device
  authentication, so keys cannot be used while the device is locked. Without it,
  any code running as the app UID can silently decrypt the HOTP secret (B).
- `setIsStrongBoxBacked(true)`, guarded by a feature check and fallback, would use
  hardware-isolated key storage where available.
- **Adds (B):** `KeyPermanentlyInvalidatedException` is not handled. Once
  `setUserAuthenticationRequired` is enabled this becomes a real failure mode, as
  a biometric enrolment change invalidates the key and the HOTP secret becomes
  permanently undecryptable with no recovery path.

`setUnlockedDeviceRequired` is a fourth option B raises, worth considering only if
it does not conflict with offline-first POS usage patterns.

### L2. Placeholder application resources, and no `FLAG_SECURE`

**Sources: A (L2), B (1.6).**

`AndroidManifest.xml:8-11` uses framework placeholders
(`@android:drawable/sym_def_app_icon`, `@android:style/Theme.NoTitleBar`) rather
than app resources. Expected at alpha, and it follows from having no `res/`
directory (**M11**). Separately, no window sets `FLAG_SECURE`, so PIN entry and
any future payment screen appear in the system recents screenshot and in screen
recordings. See **M16** for the higher-value case, the HOTP provisioning screen.

**Fix.** `FLAG_SECURE` on the lock screen is a one-line change worth making
alongside **C1**.

### L3. Code hygiene: 8 non-null assertions, `println` instead of logging

**Sources: A (L3), B (§2 tail).**

Measured by A across all 83 Kotlin files:

| Pattern | Count |
|---|---|
| `!!` non-null assertions | 8 |
| `println` | 2 |
| `TODO` comments | 2 |
| `Log.*` calls | **0** |

The notable line is the last one: there is no logging in the application at all,
and the only diagnostic output is two `println` calls, which do not reach logcat
usefully on Android. Establishing a small logging abstraction in `:core-data` or
`:shared-kmp` now is worth more than it appears, because the offline-first sync
engine (**H12**) is not debuggable without one.

**Adds (B):** `AnalyticsAggregator.kt` is an empty `// TODO` stub, which is one of
the two TODO matches.

### L4. Two of fourteen commits follow the project's own commit format

**Sources: A (L4), B (hygiene).**

`CONTRIBUTING.md:162-175` mandates `type(scope): short description` followed by a
`Closes #123` footer. Exactly 2 of the 14 commits reviewed carry the footer. Scope
usage drifts too: several commits use multiple comma-separated scopes where the
specification calls for a single module name, one commit has its entire body
collapsed onto the subject line as hyphenated bullets, and one subject appears
twice in consecutive commits (B independently noted the near-duplicate pair).

Worth fixing now via a commit-message hook or a CI check, since the history is
short and the convention is already written down.

### L5. Five dependency coordinates bypass the version catalog

**Sources: A only.**

`gradle/libs.versions.toml` is well organised and used consistently almost
everywhere, which makes the exceptions stand out. For example
`shared-kmp/build.gradle.kts:28` pins
`implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")` outside
the catalog, so a catalog-wide coroutines upgrade leaves it behind and can produce
version skew between the main and test classpaths.

**Fix.** Move all five into the catalog.

### L6. `detectNegativeStockViolations` reports only the crossing event

**Sources: A (L6), B (§2).**

Indexed here and covered in **H8**: the guard
`if (previousStock >= 0.0 && currentStock < 0.0)` flags only the movement that
crosses zero, so further oversells while stock is already negative are not
reported.

### L7. `Money` arithmetic is unchecked and can silently overflow

**Sources: C (L1) only.** New to the merged list. Distinct from **H6**, which is
about rounding and reconciliation rather than range.

`Money` operators use raw `Long` math, so `Long.MAX_VALUE` minor units plus one
wraps to a negative amount with no exception
(`shared-kmp/.../models/Money.kt`, operator functions). Unreachable in normal
retail flows, but a multiplication bug (quantity times unit price with a corrupted
quantity) would corrupt totals silently rather than failing loudly, which is the
worse failure mode for financial code.

**Note:** C states that a documented-behaviour test pinning the current
wrap-around was added in PR #148. `MoneyTest.kt` is present at `ef89f62`, so the
current behaviour is now pinned, which means changing it is a deliberate decision
rather than an accident.

**Fix.** Use checked operators (`Math.addExact`-equivalent, or manual range
checks) in the `Money` operator functions and update the pinned test to assert
the exception.

### L8. Repository hygiene: committed IDE files, gaps in `.gitignore`, a dead catalog entry

**Sources: B (hygiene) only.** New to the merged list.

- 15 `.idea/` files are committed. Confirmed: `.idea/` is present in the tree.
- `.gitignore` lacks keystore and signing-material patterns (`*.jks`,
  `*.keystore`, `keystore.properties`, `local.properties`), which matters more
  than usual for a project that will sign three flavors.
- The version catalog carries a dead `ktor` entry for the server project that does
  not exist (**M5**).
- `docs/adr/ADR-015-...md.md` double extension, also indexed as **M13**.

**Fix.** Remove `.idea/` from tracking and add it to `.gitignore` along with the
signing patterns, and drop the dead catalog entry.

### L9. The alpha destructive migration has no removal guardrail

**Sources: C (L2) only.** New to the merged list.

`fallbackToDestructiveMigration()` in the database builder is acceptable per
ADR-018 and project policy until `v0.2-alpha`, but nothing marks the removal
point. A version bump past alpha with the call still active silently wipes
production stores, which for the intended user is their sales and stock history.

This interacts with **H1**: the same destructive recreation is why the default
location must be seeded from a `RoomDatabase.Callback` rather than from onboarding.

**Fix.** Add a `TODO(v0.2-alpha)` with a release-blocking checklist entry, or gate
the call on `BuildConfig.DEBUG` so a release build cannot carry it.

---

## What is already good

All three reviews independently praised the same things, which is a stronger
signal than any single review's positives section.

**`KeystoreManager` is genuinely well written.** AES-256-GCM, a fresh random IV
per encryption (B verified there is no IV reuse), keys generated in and never
leaving the Android keystore, and critically **no plaintext fallback path**. Many
first attempts at keystore code include an "if the keystore is unavailable, store
it in plaintext" branch which silently destroys the guarantee. This one does not.
It is also most of what is needed to fix **C2**.

**HOTP generation is correct.** Big-endian counter, dynamic truncation with
`& 0x0F` and `0x7F`, matching the RFC 4226 Appendix D vectors, with a test suite
that asserts those published vectors rather than whatever the implementation
happens to produce. That is the right way to test a standardised algorithm and it
is frequently done wrong. The secret uses `SecureRandom` at a correct 160-bit
size, with no `java.util.Random` or `Math.random` anywhere.

**`ConflictResolver.sortDeterministically` is the right idea.** Ordering by
timestamp, then `deviceId`, then `id` produces a stable total order across devices
without coordination, which is precisely what an offline-first multi-device system
needs. The problem in **H8** is that the rest of the class does not build on it.

**`Money` as a `value class` over `Long` minor units is the correct choice.**
Floating-point currency is one of the most common and most damaging mistakes in
POS software, and the author avoided it in the type. **H6** and **L7** are about
arithmetic performed around `Money`, not about `Money` itself.

**Module boundaries are respected, and this is the strongest part of the
codebase.** Verified by both A and B across all build files and imports:
`:feature-auth` depends only on `:shared-kmp` and `:core-*`, `:core-data` and
`:core-ui` on `:shared-kmp` only, `:android-app` is the sole aggregator, and
`:shared-kmp` commonMain has zero `android.*` or `androidx.*` imports. The only
blemish is the documentation contradiction in **M12**. Getting KMP boundary
discipline right this early is uncommon.

**F-Droid discipline is real.** No Firebase, no Google Play Services, no analytics,
no committed secrets or hardcoded service URLs. The single breach (**M3**) is an
unused dependency in the wrong source set. C's dependency review adds that every
library in the catalog (Kotlin, Compose BOM, Room, Hilt, WorkManager, Ktor)
comes from an actively maintained project under a permissive licence, with no
abandoned entries; its only two observations were **M3** and **C2**, both
already in this list.

That claim is narrower than C states it, and it is a licence and
project-liveness check, not a vulnerability audit. It covers the direct versions
declared in `gradle/libs.versions.toml` and says nothing about the transitive
graph, which nobody has resolved (**H10**). Spot-checking advisories against the
declared versions already turns up one hit: `ktor = "2.3.12"` is affected by
CVE-2024-49580 / GHSA-8qv4-773j-c979, an information disclosure in the
`HttpCache` plugin fixed in 2.3.13, so "no known-vulnerable entries" is not
accurate as written. Bump Ktor to 2.3.13 or later, and treat the full dependency
graph as unaudited until CI resolves it and a scanner runs over the result.

**Room schema export is real.** `1.json` through `4.json` are committed and
`4.json` matches the declared version, contrary to C's L3 (see **K2**).

**The Gradle version catalog is well organised** and used consistently, with only
the five exceptions in **L5**, and the wrapper is complete.

**The ADRs are real, dated, well-argued decisions** and the PR and issue templates
operationalise the project's rules.

**Writing down the architecture contract at all is the best decision in the
repository.** `CONTEXT.md` is why these reviews could measure the project against
its own intent rather than against outside preference. Most findings above are
gaps between `CONTEXT.md` and the code, which is only possible to state because
`CONTEXT.md` exists and is specific.

---

## Merged remediation plan

A's staged plan, with B's ordering merged in and the new findings inserted. Each
stage makes the next one safer.

### Stage 0: stop the bleeding (hours, one PR)

1. Commit the GPL v3 `LICENSE` file (**C3**). Five minutes, removes the legal
   defect, unblocks F-Droid.
2. `git update-index --chmod=+x gradlew` (**H2**).
3. Set `android:allowBackup="false"` (**C6**).
4. Make the PIN length bounds agree (**C5**).
5. Withdraw the false security claims in `CONTEXT.md:20`, `README.md:23`, and
   `SECURITY.md` (**C2**, **H14**). Withdrawing a false security claim is urgent
   even though implementing it is not.

### Stage 1: make the project verifiable (days)

6. Run `./gradlew assembleGithubDebug` and find out whether the tree builds
   (**C4**, **H15**). Nothing below can be verified otherwise. Add
   `alias(libs.plugins.kotlin.android)` to the five Android modules if it fails.
7. Add `android-app/proguard-rules.pro` (**M1**).
8. Add `.github/workflows/ci.yml` running assemble, release assemble, and `test`
   on pull requests, plus ktlint or detekt, and point the README badge at it
   (**H10**).

### Stage 2: fix the credential chain (days)

9. Replace plaintext PIN storage with a salted KDF, move the comparison out of the
   Composable into a repository, add a migration, and add the test that the stored
   value differs from the input (**C1**).
10. Add persistent failed-attempt counting, escalating backoff, and an owner
    override path (**H4**).
11. Encrypt the database with SQLCipher, sealing the passphrase with the existing
    `KeystoreManager`, then restore the documentation claims removed in step 5
    (**C2**), which also closes the tampering vector in **M15**.
12. Add `FLAG_SECURE` to the lock, provisioning, and override-code screens, and
    zero the raw HOTP secret after use (**L2**, **M16**).

### Stage 3: remove the ambiguity before building on it (days)

13. Delete one of the two duplicate domain hierarchies, keeping the richer
    `domain.*` contracts, repoint the tests, and reconcile `CONTEXT.md` including
    `WAITER` versus `CREW` (**H5**).
14. Seed the default location from a Room callback and add an instrumented test
    that inserts an item (**H1**).
15. Add `syncStatus` to the three entities missing it, plus a type converter,
    `locationId` on `UserEntity`, and the `StockMovementEntity` reconciliation
    (**H9**, **M14**). Do this inside the destructive-migration window, where it
    is free.
16. Give `RoleConverter` a least-privileged fallback (**M4**).
17. Remove `kmp-app-updater` from `commonMain` while it is unused, and create the
    three flavor source sets (**M3**, **M2**).

### Stage 4: money and stock correctness (1 to 2 weeks)

18. Write an ADR defining the rounding, tax-presentation, and negative-quantity
    contract, fix `SalesCalculator` against it, and add the reconciliation
    property test (**H6**). Add checked arithmetic to the `Money` operators
    (**L7**).
19. Replace `Double` quantities with scaled integers or `BigDecimal`, and unify
    the replay ordering between `InventoryEngine` and `ConflictResolver` (**H7**).
20. Decide and implement the conflict-resolution policy, or rename the class to
    match what it does (**H8**, **L6**).
21. Fix HOTP validation: constant-time comparison, atomic counter advance on
    success, window of 10, and attempt throttling (**M8**). Fix the generation
    side so the counter is not read from a stale session snapshot (**H13**).
22. Wire one restricted action end to end through `hasPermission`,
    `ManagerApprovalOverlay`, and `validateCode`, and move enforcement into the
    use-case layer (**H3**).

### Stage 5: make the sync stack real (days, after stage 3)

23. Remove the default `WorkManagerInitializer` from the manifest so the Hilt
    worker factory is used, schedule the worker, create the `sync_queue` table,
    create the notification channel, and declare `POST_NOTIFICATIONS` (**H12**).

### Stage 6: documentation truth and hygiene (ongoing)

24. Add a status banner to every unimplemented design document, and to all three
    setup guides in particular (**M5**, **M6**), plus ADR compliance lines
    (**M7**).
25. Resolve the `CONTEXT.md` self-contradiction on `:core-network` (**M12**) and
    reconcile `minSdk` with the documented API level (**M9**).
26. Fix the doubled ADR-015 filename and the broken links (**M13**).
27. Introduce `res/strings.xml` and extract strings before POS and inventory are
    written (**M11**).
28. Add a `DATABASE_VERSION` constant and correct the documented schema path
    (**M10**), a logging abstraction (**L3**), catalog the stray dependencies
    (**L5**), untrack `.idea/` and extend `.gitignore` (**L8**), add a
    commit-message check (**L4**), and add the `TODO(v0.2-alpha)` guardrail on
    destructive migration (**L9**).

---

## Appendix A: source concordance

Every finding in all three source documents, mapped to its merged ID. Use this to
confirm nothing was dropped in the merge.

### A (`CODE_REVIEW_NORMAL_UNSPECIFIED.md`, master) to merged

All 36 findings carried over with their IDs unchanged: `C1-C6`, `H1-H11`,
`M1-M13`, `L1-L6`. Two were updated with newer measurements (**H11** test counts,
**M10** schema verification) and one had B's dissent recorded (**H1** severity).

### B (`CODE_REVIEW_FINDINGS_HIGH_UNSPECIFIED.md`) to merged

| B section | Merged ID |
|---|---|
| 1.1 PINs stored and verified in plaintext | **C1** |
| 1.2 Room database not encrypted | **C2** |
| 1.2 `allowBackup` / Auto Backup off-device | **C6** |
| 1.3 No brute-force protection | **H4** |
| 1.3 5-6 digit PIN "lockout-by-bug" | **C5** |
| 1.4 HOTP enforcement half does not exist | **H3** |
| 1.4 Overlay accepts any input, no throttling | **H3**, **M8** |
| 1.4 Counter reuse from stale session snapshot | **H13** (new) |
| 1.5 RBAC enforced only in UI | **H3** (corrected, see **K5**) |
| 1.5 `permissionsOverride` trusted from plaintext DB | **M15** (new) |
| 1.6 No `FLAG_SECURE`, secret shown as QR + Base32, never zeroed | **L2**, **M16** (new) |
| 1.6 Keystore hardening, `KeyPermanentlyInvalidatedException` | **L1** |
| 1.6 Non-constant-time comparison | **M8**, **C1** |
| 2 Money via `Double`, `roundToLong` asymmetry | **H6** |
| 2 Negative-quantity lines broken | **H6** |
| 2 Multiple inclusive taxes overstate tax | **H6** |
| 2 Cart-discount proration loses minor units | **H6** |
| 2 `ConflictResolver` does not resolve | **H8**, **L6** |
| 2 `InventoryEngine` `Double` accumulator | **H7** |
| 2 Movement enum has no VOID/RETURN | **H7**, **M14** |
| 2 Test quality: happy-path only | **H11** |
| 2 `AnalyticsAggregator` empty stub | **L3** |
| 3.1 Sync stack inert (worker, initializer, channel, `sync_queue`) | **H12** (new) |
| 3.2 Module boundaries clean | positives |
| 3.2 `:core-network` declares unused `:core-data` dep | **M12** |
| 3.3 Updater ships to fdroid | **M3** |
| 3.3 No flavor source sets, stub compiled into all flavors | **M2** |
| 3.4 No default location seeded | **H1** (severity per master, see **K4**) |
| 3.4 `syncStatus` missing, no `sync_queue` table | **H9**, **H12** |
| 4 Duplicated divergent type hierarchies | **H5** |
| 4 Triplicate `NoSyncBackend`/`LocalNotificationBackend` | **H5** |
| 4 `StockMovementEntity` drift, `RECEIPT` vs `RECEIVING` | **M14** (new) |
| 5 No LICENSE | **C3** |
| 5 No CI, no static analysis | **H10** |
| 5 Suspicious toolchain versions | **H15** (new) |
| 5 No Kotlin plugin applied | **C4** |
| 5 Documented scope exceeds built scope | **M5** |
| 5 `SECURITY.md` over-claims | **H14** (new) |
| 5 Config inconsistencies (`minSdk`, `compileSdk`, no `res/`) | **M9**, **M11** |
| 5 Hygiene (`.idea/`, `.gitignore`, dead ktor entry, ADR filename) | **L8** (new), **M13** |
| 5 Verified positives | positives |
| 6 Remediation order | merged plan |

### C (`CODE_REVIEW_FINDINGS_HIGH_SPECIFIED.md`) to merged

| C ID | Merged ID |
|---|---|
| C1 Room database not encrypted | **C2** |
| C2 PINs stored/compared in plaintext | **C1** |
| C3 No brute-force protection | **H4** |
| C4 Manager approval not wired to HOTP | **H3** |
| H1 HOTP counter never consumed | **M8** |
| H2 RBAC enforced only in UI | **H3** (corrected, see **K5**) |
| H3 `kmp-app-updater` leaks into all flavors | **M3** |
| H4 `allowBackup="true"` | **C6** |
| M1 `RoleDefaults` dead code conflicting with live RBAC | **H5** |
| M2 PIN length rules disagree | **C5** (severity raised from Medium, rationale in the finding) |
| M3 `StockMovementEntity` missing fields | **M14**, **H9** |
| M4 Live and tested sync implementations inverted | **H5** (consequence 1) |
| M5 Hardcoded user-facing strings | **M11** |
| L1 `Money` arithmetic can overflow | **L7** (new) |
| L2 Destructive migration has no beta guardrail | **L9** (new) |
| L3 Schema export JSON not committed for current version | **dropped**, not reproducible (see **K2**) |
| Dependency check section | positives, **M3**, **C2** |
| Test coverage note (PR #148) | **H11** (resolved **K1**) |

---

## Appendix B: merged finding index

### Critical (6)

| ID | Finding | Sources |
|---|---|---|
| C1 | PIN stored and compared in plaintext in a column named `pinHash` | A, B, C |
| C2 | Database not encrypted, contradicting `CONTEXT.md`, `README.md`, `SECURITY.md` | A, B, C |
| C3 | GPL v3 project ships no LICENSE file | A, B |
| C4 | No module applies the Kotlin Android plugin | A, B |
| C5 | A 5 or 6 digit PIN permanently locks the owner out | A, B, C |
| C6 | `allowBackup="true"` exposes the plaintext database and PIN | A, B, C |

### High (15)

| ID | Finding | Sources |
|---|---|---|
| H1 | Default location never seeded while `locationId` is a non-null FK | A, B |
| H2 | `gradlew` committed non-executable | A |
| H3 | RBAC, manager approval, and HOTP validation are dead code | A, B, C |
| H4 | No rate limiting or lockout on PIN entry | A, B, C |
| H5 | Duplicate domain hierarchies; the tested one is dead | A, B, C |
| H6 | Money arithmetic via `Double`; totals do not reconcile | A, B |
| H7 | Stock accumulated in `Double`, contradicting ADR-002 | A, B |
| H8 | `ConflictResolver` contains no conflict resolution | A, B |
| H9 | `syncStatus` on one of four Room entities | A, B, C |
| H10 | No CI, and the README badge points at a missing workflow | A, B |
| H11 | Zero tests in all five Android modules | A, B, C |
| H12 | Sync stack is inert end to end | B |
| H13 | Override codes reissued identically from a stale counter | B |
| H14 | `SECURITY.md` documents controls that do not exist | B |
| H15 | Toolchain versions skewed, build unproven | B |

### Medium (16)

| ID | Finding | Sources |
|---|---|---|
| M1 | Release build cannot succeed: `proguard-rules.pro` missing | A, B |
| M2 | Flavors declared but no flavor source sets exist | A, B |
| M3 | App updater ships in the F-Droid flavor | A, B, C |
| M4 | `RoleConverter` crashes on an unrecognised role | A |
| M5 | Documented product is roughly ten times the implemented one | A, B |
| M6 | Three setup guides describe non-existent infrastructure | A |
| M7 | ADR compliance partial; four ADRs unimplemented | A |
| M8 | HOTP validation timing-unsafe, replayable, wrong window | A, B, C |
| M9 | `minSdk` 24 versus documented API 26 | A, B |
| M10 | No `DATABASE_VERSION`; one schema bump has no change | A |
| M11 | No `res/` or `strings.xml`; localisation blocked | A, B, C |
| M12 | `CONTEXT.md` contradicts itself on `:core-network` | A, B |
| M13 | Broken documentation links | A, B |
| M14 | `StockMovementEntity` drifts from the domain model | B, C |
| M15 | `SessionManager` trusts `permissionsOverride` from the plaintext DB | B |
| M16 | HOTP secret shown in plaintext during provisioning, never zeroed | B |

### Low (9)

| ID | Finding | Sources |
|---|---|---|
| L1 | `KeystoreManager` hardening opportunities | A, B |
| L2 | Placeholder resources, no `FLAG_SECURE` | A, B |
| L3 | 8 `!!`, `println` instead of logging, no `Log.*` | A, B |
| L4 | 2 of 14 commits follow the project's commit format | A, B |
| L5 | Five dependencies bypass the version catalog | A |
| L6 | `detectNegativeStockViolations` reports only the crossing event | A, B |
| L7 | `Money` arithmetic can silently overflow | C |
| L8 | Committed `.idea/`, `.gitignore` gaps, dead catalog entry | B |
| L9 | Destructive migration has no removal guardrail | C |

**Total: 46 findings (6 Critical, 15 High, 16 Medium, 9 Low).**

One finding from the source documents was dropped: C's L3 (stale schema export),
which is not reproducible at `ef89f62`. See **K2**.

---

## Appendix C: reproducing the merge verification

Commands run against `ef89f62` to resolve the conflicts in **K1** through **K6**.

```bash
# K1: test count and location
grep -rn "@Test" --include=*.kt . | wc -l                    # 51
find . -path "*Test*" -name "*.kt" -not -path "./.git/*"     # 10 files, all :shared-kmp
                                                             # includes MoneyTest.kt and
                                                             # models/auth/RolePermissionsTest.kt (PR #148)

# K2: schema export currency
python3 -c "
import json,glob
for f in sorted(glob.glob('core/core-data/schemas/*/*.json')):
    d=json.load(open(f)); print(f, d['database']['version'], d['database']['identityHash'])"
# 1.json 1 619da4c9cb0ca4f53df880f98f8a4a84
# 2.json 2 d0aaa1cc13fbac7563053865aa1b0a5d
# 3.json 3 d0aaa1cc13fbac7563053865aa1b0a5d   <- identical to 2
# 4.json 4 5cc8d3566baaf7cecef115bb281153d2   <- matches @Database(version = 4)

# K3: Kotlin Android plugin
grep -rn "kotlin.android" --include=*.kts --include=*.toml .
# build.gradle.kts:5 (apply false) and libs.versions.toml:46 only

# Standing findings re-confirmed
git ls-files --stage gradlew                                 # 100644
ls LICENSE                                                   # no such file
ls .github/workflows/                                        # no such directory
grep -rn "syncStatus" --include=*.kt .                       # 2 matches
grep -rn -i "MessageDigest\|bcrypt\|argon\|PBKDF2" \
  --include=*.kt --include=*.kts --include=*.toml . | wc -l  # 0
grep -n "allowBackup" android-app/src/main/AndroidManifest.xml
grep -rn "minSdk" --include=*.kts .                          # 24 in all six modules
grep -n "updater" shared-kmp/build.gradle.kts                # line 23, commonMain

# H12: sync stack inert
grep -rn "schedulePeriodicSync\|triggerImmediateSync" --include=*.kt .
# declarations only, zero callers
grep -rn "sync_queue" --include=*.kt .
# SyncWorker.kt:22, in a comment

# H13: stale HOTP counter
sed -n '30,65p' feature/feature-auth/src/main/kotlin/app/kaup/feature/auth/ui/hotp/OverrideCodeGenerationViewModel.kt
# reads sessionManager.currentUser.value.hotpCounter, writes DAO, never refreshes session
```

**Not verified:** no JDK and no Android SDK are available, so no Gradle build, no
compilation, and no test run was performed during this merge, exactly as in the
three source reviews. **C4**, **M1**, and **H15** remain predictions from
configuration.
