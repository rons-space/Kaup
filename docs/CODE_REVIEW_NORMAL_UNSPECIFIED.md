# Kaup Code Review

Repository: `rons-space/Kaup`
Reviewed commit: `b7f756e` (`feat(auth): add HOTP override code generation UI for managers`)
Branch reviewed: `main` (the only branch; 14 commits total)
Review date: 2026-08-13

---

## Contents

- [About this review](#about-this-review)
- [Executive summary](#executive-summary)
- [Critical findings](#critical-findings)
  - [C1. The user PIN is stored and compared in plaintext, in a column named `pinHash`](#c1-the-user-pin-is-stored-and-compared-in-plaintext-in-a-column-named-pinhash)
  - [C2. The database is not encrypted, contradicting two explicit documentation claims](#c2-the-database-is-not-encrypted-contradicting-two-explicit-documentation-claims)
  - [C3. A GPL v3 project ships no LICENSE file](#c3-a-gpl-v3-project-ships-no-license-file)
  - [C4. No module applies the Kotlin Android plugin](#c4-no-module-applies-the-kotlin-android-plugin)
  - [C5. A 5 or 6 digit PIN permanently locks the owner out of the app](#c5-a-5-or-6-digit-pin-permanently-locks-the-owner-out-of-the-app)
  - [C6. `allowBackup="true"` makes the plaintext database and PIN extractable](#c6-allowbackuptrue-makes-the-plaintext-database-and-pin-extractable)
- [High findings](#high-findings)
  - [H1. The default location is never seeded, while `locationId` is a non-null foreign key](#h1-the-default-location-is-never-seeded-while-locationid-is-a-non-null-foreign-key)
  - [H2. `gradlew` is committed non-executable, so every documented build command fails](#h2-gradlew-is-committed-non-executable-so-every-documented-build-command-fails)
  - [H3. RBAC, manager approval, and HOTP validation are entirely dead code](#h3-rbac-manager-approval-and-hotp-validation-are-entirely-dead-code)
  - [H4. No rate limiting or lockout on PIN entry](#h4-no-rate-limiting-or-lockout-on-pin-entry)
  - [H5. Duplicate parallel domain hierarchies: the ADR-canonical interfaces are the dead ones](#h5-duplicate-parallel-domain-hierarchies-the-adr-canonical-interfaces-are-the-dead-ones)
  - [H6. Money arithmetic is routed through `Double`, and the totals do not reconcile](#h6-money-arithmetic-is-routed-through-double-and-the-totals-do-not-reconcile)
  - [H7. Inventory stock is accumulated in `Double`, contradicting ADR-002](#h7-inventory-stock-is-accumulated-in-double-contradicting-adr-002)
  - [H8. `ConflictResolver` contains no conflict resolution](#h8-conflictresolver-contains-no-conflict-resolution)
  - [H9. `syncStatus` exists on one of four Room entities](#h9-syncstatus-exists-on-one-of-four-room-entities)
  - [H10. There is no CI, and the README advertises a badge for a workflow that does not exist](#h10-there-is-no-ci-and-the-readme-advertises-a-badge-for-a-workflow-that-does-not-exist)
  - [H11. Zero tests in all four Android modules](#h11-zero-tests-in-all-four-android-modules)
- [Medium findings](#medium-findings)
- [Low findings](#low-findings)
- [What is already good](#what-is-already-good)
- [Remediation plan](#remediation-plan)
- [Appendix A: reproducing every measurement](#appendix-a-reproducing-every-measurement)
- [Appendix B: finding index by severity](#appendix-b-finding-index-by-severity)

---

## About this review

This review was produced by [code]smith, an autonomous coding agent, working
directly in a sandboxed clone of the repository at commit `b7f756e`.

### Method

The repository was read in full and audited along four independent passes:

1. **Architecture contract compliance.** Kaup is unusual in that it ships its
   own architecture contract, `CONTEXT.md`, which states three explicit
   non-negotiables (offline-first, module boundaries, F-Droid clean) and a
   list of "what not to do". That file makes this review much easier to ground:
   rather than imposing outside opinions, most findings below are measured
   against rules the project set for itself.
2. **Shared domain logic.** `:shared-kmp` money, tax, inventory, conflict, and
   HOTP logic, checked for correctness rather than style.
3. **Android data and auth layer.** Room entities, DAOs, converters, Hilt
   wiring, the keystore, and the only implemented feature module.
4. **Documentation and operations.** Roughly 600 individual factual claims
   across `CONTEXT.md`, `README.md`, `ROADMAP.md`, `CONTRIBUTING.md`,
   `SECURITY.md`, `docs/`, and all 18 ADRs, each checked against the code.

### What was verified by execution, and what was not

This distinction matters for how much weight to give each finding.

**Verified by execution** in the sandbox: file and directory existence, git
object metadata (including file modes via `git ls-files --stage`), full-text
searches across the repository, line-level reads of every file cited, commit
history and commit message format, and cross-referencing of every documentation
claim against the source it describes. Every line number and every "zero
matches" statement below was produced by a command that is reproduced in
[Appendix A](#appendix-a-reproducing-every-measurement).

**Not verified by execution:** the sandbox has no JDK and no Android SDK
(`java: command not found`, `ANDROID_HOME` unset), so **no Gradle build, no
compilation, no unit test run, and no APK inspection was performed.** This is a
real limit and it is called out explicitly where it matters, particularly in
[C4](#c4-no-module-applies-the-kotlin-android-plugin), which predicts a build
failure from configuration analysis alone. Findings about runtime behaviour are
derived from reading the code, not from observing it run. Where a claim would
have benefited from execution, the review says so rather than implying more
confidence than the evidence supports.

### A note on the stage of the project

Kaup is at `0.1-alpha`, 14 commits in, and honest about it. This review is
deliberately not graded on a curve for that, because the most severe findings
are precisely the kind that get harder to fix later: a plaintext credential
format that will need a migration, a licence file that is missing from a
project already accepting contributions, and a documentation set that has
drifted so far from the code that it now actively misleads. Several findings are
also cheap to fix today and expensive to fix after the first real user stores
real money in the app.

Findings are ordered by severity, and within a severity by how cheap they are
to fix.

---

## Executive summary

Kaup is a well-architected skeleton with a serious credential-handling problem
and a documentation set that describes a product roughly ten times larger than
what exists. The module structure, the Gradle version catalog, the KMP split,
and `KeystoreManager` are genuinely good. Almost everything the documentation
says about features, security, and completeness is not currently true.

The single most important thing in this review: **the owner's PIN is stored in
the database in plaintext, in a column named `pinHash`, in a database that is
not encrypted, in an app that allows backup extraction.** Those three findings
compose into one chain, and each link is independently confirmed below.

### Measured state

| Dimension | Measured | Documented / expected | Status |
|---|---|---|---|
| Kotlin source | 3,394 LOC across 83 files | n/a | n/a |
| Modules in `settings.gradle.kts` | 6 | 16 (11 `feature-*`, 4 `core`/shared, plus `ktor-server`) | 10 absent |
| Feature modules implemented | 1 (`:feature:feature-auth`) | 11 | 10 absent |
| Room entities | 4 (`items`, `locations`, `stock_movements`, `users`) | 26 tables in `docs/modules.md` | 3 of 26 exist; 23 absent; `locations` undocumented |
| Navigable screens with real content | 3 (onboarding, lock, HOTP) | POS, inventory, reports, settings | 4 are `DummyScreen` placeholders |
| PIN storage | plaintext | hashed (column is named `pinHash`) | not met |
| Hashing primitives in repo | 0 | at least one | not met |
| Database encryption | none | "encrypted" (`CONTEXT.md:20`, `README.md:23`) | not met |
| `allowBackup` | `true` | not stated | risk |
| LICENSE file | absent | GPL v3, linked 3 times | absent |
| Modules applying `kotlin-android` | 0 of 5 Android modules | 5 | build blocker |
| `gradlew` file mode | `100644` | `100755` | not executable |
| `syncStatus` on Room entities | 1 of 4 | "every syncable entity" (`CONTEXT.md`) | 1 of 4 |
| Tests | 32 `@Test` in 8 files, all in `:shared-kmp` | n/a | 0 in 4 Android modules |
| CI workflows | 0 | README renders a `ci.yml` badge | absent |
| ADR claims true | 68 of 180 | 180 | 38 percent |
| Setup guide claims true | 0 of 37 | 37 | 0 percent |
| Commits matching documented format | 2 of 14 | 14 | 14 percent |
| Build verified by this review | no (no JDK, no Android SDK) | n/a | see above |

### Severity definitions

- **Critical.** Causes data loss, credential exposure, legal exposure, or
  prevents the project from building or being used at all. Fix before any
  further feature work.
- **High.** Produces incorrect financial or inventory results, leaves a
  documented security control unenforced, or blocks contributors. Fix before
  `0.2-alpha`.
- **Medium.** Correctness, maintainability, or accuracy problems that will
  compound as the codebase grows.
- **Low.** Hygiene and polish.

Totals: **6 Critical, 11 High, 13 Medium, 6 Low** (36 findings). The full index
is in [Appendix B](#appendix-b-finding-index-by-severity).

---

## Critical findings

### C1. The user PIN is stored and compared in plaintext, in a column named `pinHash`

`feature/feature-auth/.../onboarding/OnboardingViewModel.kt:92` writes the
owner's PIN straight into the field named `pinHash`:

```kotlin
val owner = UserEntity(
    id = java.util.UUID.randomUUID().toString(),
    name = state.ownerName,
    pinHash = state.ownerPin, // Note: In production, hash this properly
    role = Role.OWNER
)
```

`feature/feature-auth/.../ui/LockScreen.kt:83` then compares the typed PIN to
that column directly:

```kotlin
if (pin == user.pinHash) {
    onSuccess()
}
```

There is no hashing anywhere in the repository. A search for
`MessageDigest`, `bcrypt`, `scrypt`, `argon`, `PBKDF2`, `sha256`, and `hashOf`
across all Kotlin, Gradle, and TOML files returns **zero matches**. So the
column name is not merely aspirational, it is actively misleading: any future
contributor reading `UserEntity.pinHash`, or reading a DAO query against it,
will reasonably assume the value is a digest and will write code on that
assumption.

Why this is Critical rather than a known-alpha shortcut:

- The value is the sole credential guarding a point-of-sale application that
  holds takings, stock, and user records.
- It is stored in an unencrypted database ([C2](#c2-the-database-is-not-encrypted-contradicting-two-explicit-documentation-claims))
  in an app that permits backup extraction ([C6](#c6-allowbackuptrue-makes-the-plaintext-database-and-pin-extractable)).
  Those three findings are one attack chain, not three separate issues.
- Fixing it later requires a credential migration, because existing installs
  will hold plaintext values that cannot be distinguished from digests without
  a schema change or a format marker.
- PINs are heavily reused. A leaked store PIN is frequently a leaked phone
  unlock code or bank PIN for the same person.

The comment `// Note: In production, hash this properly` shows the author knew.
That is worth crediting, but a self-documented plaintext credential is still a
plaintext credential, and `SECURITY.md` makes no mention of it, so a user
reading the project's own security documentation would not learn about it.

**Fix.** Introduce a real KDF before any further auth work. Two viable routes:

1. Derive with PBKDF2 via `javax.crypto.SecretKeyFactory` in `:core-data`
   (no new dependency, available on all supported API levels), with a
   per-user random salt column and an iteration count stored alongside.
2. Add a Kotlin-multiplatform Argon2 or bcrypt binding if the KMP surface is
   preferred, subject to the licence rule in `CONTEXT.md`.

Either way: store salt and algorithm parameters, compare with a
constant-time comparison, rename the column only as part of the migration that
changes the format, and add a test that asserts the stored value is not equal to
the input PIN. That last test is one line and would have caught this.

### C2. The database is not encrypted, contradicting two explicit documentation claims

`CONTEXT.md:20` states:

```
**Database**: Room (local, encrypted)
```

`README.md:23` repeats the promise to users:

```
- **Your data, your device** ... all data is stored locally in an encrypted Room
```

The database is not encrypted. `android-app/.../di/DatabaseModule.kt:26-33`
builds it with no `openHelperFactory` and no passphrase:

```kotlin
return Room.databaseBuilder(
    context,
    KaupDatabase::class.java,
    "kaup_database"
)
// ADR-018: Destructive migration during alpha phase
.fallbackToDestructiveMigration()
.build()
```

A search for `sqlcipher`, `zetetic`, `SupportFactory`, `openHelperFactory`, and
`passphrase` across all Kotlin, Gradle, and TOML files returns **zero matches**.
There is no encryption dependency in `gradle/libs.versions.toml` and no
encrypted-database code path anywhere.

This is Critical for two distinct reasons. The first is the technical exposure:
`kaup_database` is a plaintext SQLite file containing user records with
plaintext PINs. The second is that this is a **false security claim made to
end users in the README of a privacy-positioning product.** Kaup's entire pitch
is "your data, your device, no proprietary cloud". A user who chooses Kaup
specifically because of the encryption claim is being misinformed. That is a
different and more serious category of defect than an unimplemented feature.

Note the irony that the repository already contains the hard part. See
[what is already good](#what-is-already-good): `KeystoreManager` is a correct
AES-256-GCM implementation backed by the Android keystore. The passphrase
management needed for SQLCipher is largely solved; it is simply not wired to
the database.

**Fix.** Either implement the claim or withdraw it, and do the withdrawal in the
same commit as the discovery. Concretely: add SQLCipher for Android, generate a
random database passphrase, seal it with the existing `KeystoreManager`, and
pass it via `openHelperFactory`. If that is deferred past alpha, change
`CONTEXT.md:20` and `README.md:23` to say "planned" today, because those two
lines are currently untrue in a user-facing document.

### C3. A GPL v3 project ships no LICENSE file

There is no `LICENSE` file in the repository. A case-insensitive search for any
path containing `licen` returns exactly one result, and it is a decision record,
not a licence:

```
./docs/adr/ADR-006-gpl-v3-license.md
```

Meanwhile the repository asserts GPL v3 in five places and links to the
non-existent file three times:

| Location | Content |
|---|---|
| `README.md:9` | GPL v3 badge, linked to `LICENSE` |
| `README.md:25` | "free forever under GPL v3" |
| `README.md:181` | `## License` section heading |
| `README.md:184` | links `LICENSE` as "GNU General Public License v3.0" |
| `README.md:187` | "The full license text is in [LICENSE](LICENSE)" |
| `docs/adr/ADR-006-gpl-v3-license.md` | an entire ADR on choosing GPL v3 |

`README.md:187` is the clearest problem: it states the full licence text is in a
file that does not exist. All three `LICENSE` links render as 404s on GitHub.

This is Critical because it is a legal defect, not a cosmetic one:

- Without a licence file, the default position is **exclusive copyright**. The
  code is technically not open source, whatever the README badge says.
  Contributors have no grant of rights, and redistributors have no permission.
- The repository is already accepting contributions. `CONTRIBUTING.md` exists,
  issue and PR templates exist. Every contribution accepted before a licence
  file lands is made under unclear terms, which is exactly the situation that is
  painful to unwind later because it requires contacting every contributor.
- F-Droid, which the project explicitly targets with a dedicated build flavor,
  **requires** a machine-readable licence to package an application. The
  `fdroid` flavor cannot ship as things stand.

**Fix.** Commit the verbatim GPL v3 text as `LICENSE` in the repository root.
This is a five-minute change and it is the cheapest Critical in this review. Add
the standard per-file GPL header to source files if the project wants the full
convention, and verify GitHub's sidebar detects the licence afterwards.

### C4. No module applies the Kotlin Android plugin

Every Android module in the project contains Kotlin sources under
`src/main/kotlin/`, and **none of them applies the Kotlin Android plugin.**

The plugin is declared at the root with `apply false`, which correctly makes it
available to subprojects without applying it there:

```kotlin
// build.gradle.kts:5
alias(libs.plugins.kotlin.android) apply false
```

It is then never applied. Searching the whole repository for `kotlin.android`,
`kotlin("android")`, and `org.jetbrains.kotlin.android` across all Gradle and
TOML files yields exactly two hits: the `apply false` line above, and the
version catalog definition at `gradle/libs.versions.toml:46`. No module
references it.

The five Android modules apply these plugins:

| Module | Plugins applied | `kotlin-android`? |
|---|---|---|
| `:android-app` | `android.application`, `ksp`, `hilt`, `compose.compiler` | no |
| `:core:core-data` | `android.library`, `ksp` | no |
| `:core:core-ui` | `android.library`, `compose.compiler` | no |
| `:core:core-network` | `android.library`, `ksp`, `hilt` | no |
| `:feature:feature-auth` | `android.library`, `ksp`, `hilt`, `compose.compiler` | no |

`:shared-kmp` is unaffected, because it applies `kotlin.multiplatform`, which
brings its own Kotlin compilation.

Consequences, in descending order of confidence:

1. `compose.compiler` is `org.jetbrains.kotlin.plugin.compose`, a Kotlin
   compiler plugin. Applying it to a project with no Kotlin plugin is a
   configuration-time failure. This affects `:android-app`, `:core:core-ui`,
   and `:feature:feature-auth`.
2. Without `kotlin-android`, the Android Gradle Plugin registers no Kotlin
   compilation task, so `src/main/kotlin` is not compiled at all.
3. KSP and Hilt would consequently have no Kotlin sources to process, so Room
   DAOs and the Hilt graph would not be generated.

**Honest caveat, and it is a real one.** As stated in
[about this review](#about-this-review), there is no JDK or Android SDK in the
review sandbox, so **this prediction was not confirmed by running Gradle.** It
is derived from the configuration alone. Of all findings in this review, this is
the one a maintainer should verify first by simply running
`./gradlew assembleGithubDebug` (after fixing
[H2](#h2-gradlew-is-committed-non-executable-so-every-documented-build-command-fails)),
because if the build does succeed then some mechanism not visible in the build
scripts is supplying Kotlin compilation and this finding should be downgraded.

The reason it is filed as Critical rather than as a question is that
[H10](#h10-there-is-no-ci-and-the-readme-advertises-a-badge-for-a-workflow-that-does-not-exist)
establishes there is no CI, so nothing in the project has ever verified that the
committed tree builds. A repository where the build is unverified and the build
configuration is missing the language plugin is very likely a repository that
does not build.

**Fix.** Add `alias(libs.plugins.kotlin.android)` to the `plugins` block of all
five Android modules, then add the CI workflow from
[H10](#h10-there-is-no-ci-and-the-readme-advertises-a-badge-for-a-workflow-that-does-not-exist)
so this class of error cannot recur.

### C5. A 5 or 6 digit PIN permanently locks the owner out of the app

Onboarding accepts PINs of 4, 5, or 6 digits. The lock screen only ever
evaluates a PIN of exactly 4.

Onboarding gates the "continue" action on a minimum of four digits
(`OnboardingViewModel.kt:30`) and accepts input up to six
(`OnboardingViewModel.kt:56`):

```kotlin
get() = ownerName.isNotBlank() && ownerPin.length >= 4
...
if (pin.all { it.isDigit() } && pin.length <= 6) {
```

The lock screen checks the PIN only when the length is exactly four
(`LockScreen.kt:82`):

```kotlin
// Check PIN when length is 4
LaunchedEffect(pin) {
    if (pin.length == 4) {
        if (pin == user.pinHash) {
```

So an owner who follows the onboarding wizard and chooses a five or six digit
PIN, which the wizard explicitly permits, can never unlock the app again. On
reaching the fifth digit the comparison branch is simply never entered. There is
no submit button to force evaluation, no error, and no recovery path: there is no
PIN reset flow, no recovery code, and no alternative authentication anywhere in
the repository.

The only escape is clearing app data or reinstalling, which destroys the
database, which for the intended user is their sales and stock history.

This is Critical because it is reachable through the documented happy path by a
user making a perfectly reasonable choice, and the consequence is total loss of
access and data. It is also a two-character fix.

**Fix.** Make the two bounds agree, and validate at one place. The minimal
change is to compare against the stored credential length, or better, add an
explicit confirm action rather than triggering on length. Then add a test that
onboards a 6 digit PIN and unlocks with it, which is the test that should have
existed for a wizard that offers a range.

### C6. `allowBackup="true"` makes the plaintext database and PIN extractable

`android-app/src/main/AndroidManifest.xml:7` enables backup, with no rules
restricting what is included:

```xml
<application
    android:name=".KaupApplication"
    android:allowBackup="true"
```

There is no `android:fullBackupContent` and no `android:dataExtractionRules`
attribute, so the default applies and the app's databases directory is
included.

On its own this is a routine hardening finding. In this repository it completes
the chain from [C1](#c1-the-user-pin-is-stored-and-compared-in-plaintext-in-a-column-named-pinhash)
and [C2](#c2-the-database-is-not-encrypted-contradicting-two-explicit-documentation-claims):
the backup contains `kaup_database`, that database is unencrypted, and the user
table holds the PIN in plaintext. The credential is therefore extractable from a
device backup without root, and it is also copied to whatever cloud backup
destination the device is configured to use, which for a project whose headline
promise is "your data, your device" is a direct contradiction of intent.

**Fix.** For an offline financial application the appropriate default is
`android:allowBackup="false"`. If backup is wanted later as a user-facing
feature, and `ROADMAP.md` suggests it is, implement it as an explicit encrypted
export rather than relying on platform backup, and exclude the database via
`dataExtractionRules` in the meantime. Also consider `FLAG_SECURE` on the lock
screen, noted separately in [L2](#low-findings).

---

## High findings

### H1. The default location is never seeded, while `locationId` is a non-null foreign key

`CONTEXT.md:184-186` states that a "single default location is seeded on first
launch" and instructs contributors never to omit `locationId` from a
location-aware entity. The entities follow the instruction. The seeding does not
happen.

`ItemEntity` declares a non-null `locationId` with a cascading foreign key to
`locations`:

```kotlin
// core/core-data/.../entities/ItemEntity.kt:11-22
ForeignKey(
    ...
    childColumns = ["locationId"],
    onDelete = ForeignKey.CASCADE
)
...
val locationId: String,
```

Nothing ever inserts a location:

- No `RoomDatabase.Callback`, no `addCallback`, no `createFromAsset`, and no
  prepopulation exists anywhere in the repository. The only `onCreate` matches
  are `MainActivity`'s activity lifecycle method, which is unrelated.
- `LocationDao` is referenced in exactly three places: its own declaration, the
  abstract accessor on `KaupDatabase`, and the Hilt `@Provides` method in
  `DatabaseModule`. **It is never injected into any consumer and none of its
  methods are ever called.**

Room enables foreign key enforcement by default, so once any code attempts to
insert an item, the insert fails with a constraint violation because the
referenced location row cannot exist.

**Why this is High and not Critical.** It is currently latent, not live. There is
no code that inserts items yet: `ItemDao` and `StockMovementDao` have zero
consumers outside the DI module and the database class, and the POS, inventory,
reports, and settings destinations are all `DummyScreen` placeholders
(`KaupAppShell.kt:130-133`). So no user can hit this today. It becomes a hard
blocker the moment the first inventory or POS write is implemented, and it will
present as a confusing constraint-violation crash in a feature that looks
correct, rather than as an obviously missing seed.

**Fix.** Add a `RoomDatabase.Callback` in `DatabaseModule` that inserts the
default location in `onCreate`, and add an instrumented test that opens a fresh
database and inserts one item. Seeding in the Room callback rather than in
onboarding matters, because the destructive-migration policy in ADR-018 will
recreate the database and the seed must survive that.

### H2. `gradlew` is committed non-executable, so every documented build command fails

The wrapper script is committed without the executable bit:

```
$ git ls-files --stage gradlew
100644 b9bb139f790567973216cd313e69ae65789c3754 0	gradlew
```

The mode is `100644`, not `100755`. On a fresh clone on Linux or macOS, every
`./gradlew` invocation fails with a permission error. `README.md` and
`CONTRIBUTING.md` instruct contributors to run `./gradlew` commands, so the
documented first-run experience is broken for every new contributor until they
work out that they need `chmod +x gradlew`.

This is filed as High rather than Low because it lands on first contact,
affects every contributor on the two most common development platforms, and
combines badly with [C4](#c4-no-module-applies-the-kotlin-android-plugin): a new
contributor hits a permission error, fixes it, and then hits a build failure.

**Fix.** `git update-index --chmod=+x gradlew` and commit. Note that the file
mode is a property of the git index, so fixing the local filesystem permission
alone does not fix the clone.

### H3. RBAC, manager approval, and HOTP validation are entirely dead code

`CONTEXT.md:120-134` describes RBAC as the mechanism that gates every restricted
action, with `ManagerApprovalOverlay` and offline HOTP verification for actions
that exceed the current role. Four commits are dedicated to building it. None of
it is connected to anything.

| Component | Definition | Call sites in production code |
|---|---|---|
| `ManagerApprovalOverlay` | `core-ui/.../components/ManagerApprovalOverlay.kt:25` | **0** |
| `PermissionHelpers.hasPermission` | `core-ui/.../auth/PermissionHelpers.kt:10` | **0** (only used by line 28 of its own file) |
| `SessionManager.hasPermission` | `core-data/.../auth/SessionManager.kt:30` | **0** |
| `HOTPGenerator.validateCode` | `shared-kmp/.../HOTPGenerator.kt:32` | **0** (4 references, all in `HOTPGeneratorTest`) |

So the authorization layer exists as a library that nothing calls. Every action
in the app is currently unguarded, and there is no path by which a manager
override can be requested or verified at runtime. The HOTP provisioning and
override-code-generation screens exist, so a manager can generate a code, but
nothing anywhere can validate one.

The navigation layer makes the same point explicitly. `KaupAppShell.kt:67`
contains:

```kotlin
// Simulate entering PIN and successful unlock
```

**Why this matters beyond "unfinished".** The risk is that RBAC is recorded as
done. Four commits say `feat(auth): implement RBAC permission checks`, the
roadmap can reasonably be read as ticking this off, and the code looks complete
in isolation. When the POS and inventory screens are built on top, the natural
assumption will be that permission checks are already enforced somewhere upstream.
Nothing will fail loudly to correct that assumption.

**Fix.** Wire one restricted action end to end before building more surface:
gate a single destructive operation on `SessionManager.hasPermission`, present
`ManagerApprovalOverlay` when it returns false, and validate the entered code
through `HOTPGenerator.validateCode`. That one vertical slice turns the library
into an enforced control and gives the pattern for everything after it.

### H4. No rate limiting or lockout on PIN entry

`LockScreen.kt:83-89` handles a wrong PIN with a flat 500 millisecond delay and
a field reset:

```kotlin
} else {
    isError = true
    delay(500)
    pin = ""
    isError = false
}
```

There is no attempt counter, no escalating backoff, no lockout, and no
persistence of failed attempts across process death. A 4 digit PIN has 10,000
combinations, so at roughly two attempts per second an exhaustive search takes
under an hour and a half, and it survives app restarts because nothing is
recorded.

`SECURITY.md` does not mention attempt limiting, so this is not a documented
deferral.

**Fix.** Persist a failed-attempt count and a lockout-until timestamp on the user
row so they survive restarts, apply escalating backoff, and lock the account
after a threshold. Because Kaup is offline-first the lockout must be local and
time-based, and the timestamp should be checked against elapsed realtime rather
than wall-clock time so that changing the device clock does not clear it.

### H5. Duplicate parallel domain hierarchies: the ADR-canonical interfaces are the dead ones

`:shared-kmp` contains two parallel sets of the same core abstractions, in
different packages. In every case the version the app actually uses is the
thinner, undocumented one, and the version the ADRs describe is dead.

| Abstraction | Live in app | Dead |
|---|---|---|
| `Permission`, `Role` | `shared.domain.models.auth` (9 imports from app code) | `shared.models` (0) |
| `SyncBackend` | `shared.sync.SyncBackend`, Hilt-bound in `NetworkModule:17-19` | `shared.domain.sync.SyncBackend` |
| `NoSyncBackend` | `core-network/.../backend/NoSyncBackend.kt` | `shared.domain.sync.NoSyncBackend` |
| `NotificationBackend` | `shared.sync.NotificationBackend` | `shared.domain.notification.NotificationBackend` |
| Default role permissions | `getDefaultPermissions` in `domain/models/auth/Permission.kt` | `RoleDefaults` (0 call sites) |

Three consequences, in order of importance:

1. **The test suite tests the dead implementation.** `NoSyncBackendTest` exercises
   `shared.domain.sync.NoSyncBackend`, which the app never instantiates. The
   `NoSyncBackend` that Hilt actually binds, in `:core-network`, has no tests.
   The green test is therefore not evidence about shipped behaviour.
2. **`CONTEXT.md` documents the dead side.** It states that role defaults live in
   `RoleDefaults`, which is dead code, and `CONTEXT.md:137` lists `WAITER` as one
   of the four built-in roles. `WAITER` exists only in the dead
   `shared/models/Role.kt`; the live enum uses `CREW` (renamed in ADR-009). A
   contributor following the architecture contract will edit the wrong file and
   observe no effect.
3. The `:shared-kmp/sync-contracts` source set that `CONTEXT.md` names as the
   home of all pluggable interfaces does not exist at all.

Note that the two `LocalNotificationBackend` files, in `androidMain` and
`jvmMain`, are **not** an instance of this problem. Per-target implementations
are ordinary Kotlin Multiplatform structure and are correct.

**Fix.** Delete the dead packages in one commit: `shared/models/Permission.kt`,
`shared/models/Role.kt`, `RoleDefaults.kt`, `shared/domain/sync/`, and
`shared/domain/notification/NotificationBackend.kt`, keeping whichever side is
preferred and repointing the tests at the surviving implementation. Then correct
`CONTEXT.md` to match. Doing this before more features land is much cheaper than
after, because every new import is a coin flip about which hierarchy it picks.

### H6. Money arithmetic is routed through `Double`, and the totals do not reconcile

`Money` itself is well chosen: a `@JvmInline value class` over `Long` minor
units, with `plus`, `minus`, `times`, and `compareTo`. The problems are around it.

**The reported totals do not add up when any inclusive tax is present.**
`SalesCalculator.kt:78-88` returns `subtotal` gross, `taxTotal` including
inclusive tax, but `finalTotal` excluding it:

```kotlin
val taxTotalMinorUnits = inclusiveTaxSum + exclusiveTaxSum
// Final total only adds exclusive tax, because inclusive tax is already in the price
val finalTotalMinorUnits = totalTaxableValue + exclusiveTaxSum
```

Worked example, one item priced 120 with a 20 percent inclusive tax and no
discounts: `subtotal` is 120, `discountTotal` is 0, `taxTotal` is 20, and
`finalTotal` is 120. A receipt printing those four fields shows numbers that do
not reconcile, because 120 minus 0 plus 20 is 140, not 120. The comment on line
80 is correct about the arithmetic but the returned `subtotal` is gross rather
than net, so the four values cannot be presented together. For a POS, where the
receipt is a legal document in many jurisdictions, this needs a defined
contract: either return net subtotal plus tax, or return gross and label the tax
as included.

**Stacked inclusive taxes are each computed on the full gross.** Lines 65 to 75
loop over `item.taxes` and compute every inclusive tax against
`finalTaxableItemValue`, independently. For a gross of 121 carrying two 10
percent inclusive taxes, each is computed as 121 minus 110, giving a total of 22.
Neither an additive regime (20.17) nor a compounding one (21) produces 22, so
the result is wrong under either interpretation.

**Per-line pro-rating of the cart discount does not sum to the cart total.**
Line 62 rounds the pro-rated discount per line, while line 48 computes
`totalTaxableValue` directly from the cart. Three lines of 100 with a cart
discount of 10 give a ratio of 0.0333, a per-line rounded discount of 3, and a
per-line taxable sum of 291, while `totalTaxableValue` is 290. Tax is therefore
computed on a base one minor unit larger than the base the total uses.

**A negative quantity silently zeroes the line and corrupts the discount total.**
With a negative `rawSubtotal`, the guard on line 25 inverts:
`if (itemDiscount > rawSubtotal) itemDiscount = rawSubtotal` is true for a zero
discount against a negative subtotal, so `itemDiscount` becomes the whole
negative subtotal, line 28 returns exactly 0, and line 27 has already added the
negative value to `discountTotalMinorUnits`. Returns and refunds, which a POS
must handle, therefore vanish from the line total and appear as a negative
discount.

**`Money` carries no currency and no scale.** `CONTEXT.md` states Kaup targets
any country, and currency is captured during onboarding, but `Money` is only a
`Long`. Two different currencies can be summed with `plus` with no error, and
because there is no `div`, every division goes through `Double`
(lines 52, 62, 68).

**Fix.** Define the rounding and presentation contract first, in an ADR, then
test it. Specifically: decide whether `subtotal` is net or gross; compute
inclusive tax once per item from a combined rate; allocate cart discounts with a
largest-remainder method so the parts sum exactly to the whole; reject or
explicitly support negative quantities; and add the invariant test that
`subtotal - discountTotal + taxTotal == finalTotal` under the chosen contract.
That single property test would have caught three of the five problems above.

### H7. Inventory stock is accumulated in `Double`, contradicting ADR-002

`InventoryEngine.computeStock` folds quantities into a `Double`
(`InventoryEngine.kt:9-18`), as does `computeStockAsOf` and
`ConflictResolver.detectNegativeStockViolations`. ADR-002 specifies `BigDecimal`
for stock quantities.

The consequences were verified by executing the same IEEE-754 double arithmetic
the Kotlin code performs:

```
Ledger: receive 0.1, receive 0.1, receive 0.1, sell 0.3   (exactly balanced)
computeStock result:  5.551115123125783e-17     == 0.0 ? False
```

So a perfectly balanced ledger does not report zero stock. Any "is this item out
of stock" comparison against `0.0`, any equality check, and any display
formatting that does not round will be wrong for ordinary decimal weights, which
is the normal case for the grocery and market-stall users Kaup targets.

**The same movements in a different order produce a different answer:**

```
Same four movements, reordered: 2.7755575615628914e-17
Two orderings equal? False
```

This makes the sort on `InventoryEngine.kt:11` more than the redundant work it
first appears to be. Summation is mathematically commutative, so sorting cannot
change a correct total, but it does change the floating-point residual. And the
sort keys differ between the two classes that replay the same log:
`computeStock` sorts by `timestamp` alone, while
`ConflictResolver.sortDeterministically` sorts by `timestamp`, then `deviceId`,
then `id`. Two devices replaying an identical movement log with any tied
timestamps can therefore compute different stock values, which directly
undermines the multi-device offline-first premise.

**It also produces false conflict alarms.** In
`detectNegativeStockViolations`, the check is `currentStock < 0.0`:

```
Ledger: receive 0.7, receive 0.1, sell 0.8   (exactly balanced)
result: -1.1102230246251565e-16   flagged as a negative-stock violation? True
```

A balanced ledger is reported as an oversell.

**Fix.** Represent quantities as scaled integers (minor units, as `Money`
already does correctly) or as `BigDecimal` per ADR-002, and make both classes
use one shared canonical ordering. Scaled integers are the better fit here
because they keep `:shared-kmp` free of platform decimal differences and make
equality meaningful.

### H8. `ConflictResolver` contains no conflict resolution

`CONTEXT.md:113` describes `ConflictResolver` as the class that "resolves
simultaneous writes from multiple offline devices". The class has exactly two
methods: `sortDeterministically` and `detectNegativeStockViolations`. It orders
events and it reports which ones crossed zero. It never merges, chooses a
winner, or reconciles anything.

`sortDeterministically` is good code and the right foundation: ordering by
timestamp, then `deviceId`, then `id` gives a stable total order across devices.
But detection is not resolution, and the sync lifecycle documented in
`CONTEXT.md` has an explicit `CONFLICT` state whose transition to `SYNCED` is
attributed to this class. Nothing performs that transition.

`detectNegativeStockViolations` also under-reports. It flags a movement only when
`previousStock >= 0.0 && currentStock < 0.0`, so it reports the single event that
crossed zero and silently ignores every subsequent oversell while stock remains
negative. If three devices each sell the last unit offline, one violation is
reported, not two.

**Fix.** Decide the resolution policy explicitly and write it in an ADR, since
this is a domain decision rather than a coding one: stock movements are an
append-only log and are inherently commutative, so they do not conflict and
should simply be merged, while mutable records such as items and prices need a
rule (last-write-wins with a vector clock, or per-field merge). Then either
implement resolution here or rename the class to `StockLedgerAuditor`, which is
what it currently is, and correct `CONTEXT.md`.

### H9. `syncStatus` exists on one of four Room entities

`CONTEXT.md:145-157` states that every syncable Room entity carries a
`syncStatus` column, and describes the lifecycle from `PENDING` through
`SYNCING`, `SYNCED`, `FAILED`, and `CONFLICT`, with WorkManager detecting
`PENDING` rows.

A repository-wide search for `syncStatus` returns two matches:

```
core/core-data/.../entities/LocationEntity.kt:13:    val syncStatus: String = "PENDING"
shared-kmp/.../models/StockMovement.kt:26:    val syncStatus: SyncStatus = SyncStatus.PENDING
```

So of the four Room entities, only `LocationEntity` has the column.
`ItemEntity`, `StockMovementEntity`, and `UserEntity` do not. The second match is
the shared **domain** model, not the persisted entity, which makes the gap
sharper than a simple omission: `StockMovement` carries a typed `SyncStatus` in
the domain layer that has nowhere to be stored, so the value is lost on every
write and reconstructed as `PENDING` on every read.

Two related observations:

- `LocationEntity.syncStatus` is a `String`, while the domain model uses a
  `SyncStatus` enum. There is no type converter for it, so the two
  representations are unrelated.
- `UserEntity` has no `locationId`, so users are not location-scoped even though
  `CONTEXT.md` requires location-awareness for multi-location support and the
  roadmap includes per-location staff.

Because nothing yet writes items or stock movements
([H1](#h1-the-default-location-is-never-seeded-while-locationid-is-a-non-null-foreign-key)),
this is not currently losing data. It will silently lose sync state as soon as
those writes exist.

**Fix.** Add `syncStatus` to the three entities that lack it, add a type
converter so the enum is the single representation, and add `locationId` to
`UserEntity`. Doing this during the destructive-migration alpha window costs
nothing; doing it after `0.2-alpha` requires real migrations.

### H10. There is no CI, and the README advertises a badge for a workflow that does not exist

`.github/` contains only `ISSUE_TEMPLATE` and `pull_request_template.md`. There
is no `workflows` directory and no workflow file of any kind. The README renders
a CI status badge pointing at a `ci.yml` that does not exist.

`CONTRIBUTING.md:200` then makes CI a merge requirement:

```
- [ ] CI passes, all unit and integration tests green
```

That checkbox can never be satisfied. A contributor reading `CONTRIBUTING.md`
would reasonably conclude their PR will be verified automatically. Nothing is
verified.

This finding is the reason several others in this review exist. Nothing has ever
checked that the committed tree compiles
([C4](#c4-no-module-applies-the-kotlin-android-plugin)), that the wrapper is
executable ([H2](#h2-gradlew-is-committed-non-executable-so-every-documented-build-command-fails)),
or that the 32 existing tests still pass. A single workflow running
`./gradlew build` would have caught the most severe build-level findings here on
the commit that introduced them.

**Fix.** Add `.github/workflows/ci.yml` running assemble plus `test` on pull
requests, and make the badge point at it. Add `ktlint` or `detekt` in the same
workflow to catch the hygiene items in [L3](#low-findings) automatically. This is
the highest-leverage single change in this review, because it converts most of
the other findings from invisible to self-reporting.

### H11. Zero tests in all four Android modules

All 32 `@Test` methods across all 8 test files live in `:shared-kmp`. The four
Android modules have no test source sets at all:

| Module | Test files |
|---|---|
| `:shared-kmp` | 8 files, 32 `@Test` |
| `:core:core-data` | 0 |
| `:core:core-ui` | 0 |
| `:core:core-network` | 0 |
| `:feature:feature-auth` | 0 |
| `:android-app` | 0 |

Testing the pure domain module first is the right instinct, and 32 tests on
`:shared-kmp` is a reasonable start. But the untested modules are where this
review's most severe findings are: plaintext PIN storage
([C1](#c1-the-user-pin-is-stored-and-compared-in-plaintext-in-a-column-named-pinhash)),
the PIN length lockout ([C5](#c5-a-5-or-6-digit-pin-permanently-locks-the-owner-out-of-the-app)),
the missing location seed
([H1](#h1-the-default-location-is-never-seeded-while-locationid-is-a-non-null-foreign-key)),
and the unpersisted sync status
([H9](#h9-syncstatus-exists-on-one-of-four-room-entities)). Each is the kind of
defect a single cheap test would have caught:

- one Room instrumented test that inserts an item into a fresh database
- one test asserting the stored credential is not equal to the entered PIN
- one test onboarding a 6 digit PIN and unlocking with it

Note also from [H5](#h5-duplicate-parallel-domain-hierarchies-the-adr-canonical-interfaces-are-the-dead-ones)
that part of the existing 32 tests covers a dead code path, so effective coverage
of shipped behaviour is lower than the count suggests.

**Fix.** Add a Room instrumented test module for `:core:core-data` and unit tests
for the auth view models, and wire both into the CI workflow from
[H10](#h10-there-is-no-ci-and-the-readme-advertises-a-badge-for-a-workflow-that-does-not-exist).

---

## Medium findings

### M1. The release build cannot succeed: `proguard-rules.pro` does not exist

`android-app/build.gradle.kts:36-40` enables minification and names a rules file:

```kotlin
release {
    isMinifyEnabled = true
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
}
```

A search for any path matching `proguard*` in the repository returns nothing.
The release build therefore fails on a missing file. Separately, once the file
exists it will need keep rules for Room entities and the reflective enum access
in [M4](#m4-roleconverter-crashes-on-any-unrecognised-role-value), because R8
will otherwise rename the enum constants that `enumValueOf` looks up by name.

**Fix.** Add `android-app/proguard-rules.pro`, even if initially empty, and build
a release variant in CI so this is caught.

### M2. Build flavors are declared but no flavor source sets exist

Three flavors are declared (`android-app/build.gradle.kts:21-34`): `github`,
`fdroid`, and `playstore`. `android-app/src/` contains exactly one directory:
`main`.

`CONTEXT.md:166-168` instructs contributors to use flavor-specific Hilt modules
in `:android-app/src/<flavorName>/` and gives `src/github/GitHubUpdateModule.kt`
as the example. No such directory or file exists. So the flavor mechanism that
the three non-negotiables depend on for keeping proprietary code out of `fdroid`
is declared but not usable, and the documented `UpdateChecker` bindings
(`GitHubUpdateChecker`, `NoOpUpdateChecker`) do not exist in any form.

### M3. The app updater ships in the F-Droid flavor, violating a stated non-negotiable

`CONTEXT.md:164` states that the `kmp-app-updater` dependency must never be
added to the `fdroid` flavor. It is declared in
`shared-kmp/build.gradle.kts:23`, inside `commonMain.dependencies`:

```kotlin
commonMain.dependencies {
    ...
    implementation(libs.kmp.app.updater.core)
}
```

`commonMain` is compiled into every target and every consumer, so the dependency
is present in all three flavors including `fdroid`. This is a direct breach of
the third non-negotiable as the project itself defines it. The dependency is also
currently unused by any Kotlin source, so nothing is lost by removing it now.

F-Droid compliance is otherwise good: there is no Firebase, no Google Play
Services, and no committed API key or secret anywhere in the repository. This
single dependency is the only breach found.

### M4. `RoleConverter` crashes on any unrecognised role value

`core/core-data/.../converters/RoleConverter.kt:8`:

```kotlin
@TypeConverter
fun toRole(value: String): Role = enumValueOf<Role>(value)
```

`enumValueOf` throws `IllegalArgumentException` for any value not matching a
constant, and there is no fallback. Any unexpected string in the `role` column
crashes on read rather than degrading to a least-privileged default.

This is more than theoretical here, for two reasons. First, the role enum was
renamed from `WAITER` to `CREW` (ADR-009), and the dead enum in
`shared/models/Role.kt` still contains `WAITER`
([H5](#h5-duplicate-parallel-domain-hierarchies-the-adr-canonical-interfaces-are-the-dead-ones)),
so a `WAITER` row is a realistic input. Second, once sync exists, role strings
will arrive from other devices possibly running other versions, which is exactly
where unknown enum values come from.

**Fix.** Return a safe default and log, for example
`enumValues<Role>().firstOrNull { it.name == value } ?: Role.CASHIER`, choosing
the least-privileged role rather than the most.

### M5. The documented product is roughly ten times the implemented one

The documentation describes a system substantially larger than what exists. This
is normal for design documents, but here the documents are written in the present
indicative, as descriptions of what the code does, not as plans.

| Documented | Exists |
|---|---|
| 11 `feature-*` modules | 1 (`:feature:feature-auth`) |
| `ktor-server` Gradle project | absent |
| `:shared-kmp/sync-contracts` source set | absent |
| 26 Room tables (`docs/modules.md`) | 3 of those 26 |
| `PaymentGateway`, `CaptureOnlyGateway` | absent |
| `ReceiptEmailSender`, `IntentEmailSender` | absent |
| `PrinterService` | absent |
| `UpdateChecker` implementations | absent |

`docs/modules.md` is the clearest case: 511 lines documenting per-module
ownership, permissions checked, and Room tables for ten modules that do not
exist. It also does not document `locations`, which is one of the four tables
that does exist and the one with the foreign key everything else depends on.

**Fix.** Mark unimplemented documentation explicitly. A one-line status banner at
the top of each such file ("Design target, not yet implemented as of 0.1-alpha")
would resolve almost all of the documentation findings in this review at very low
cost, and is much cheaper than either deleting the design work or implementing it.

### M6. The three backend setup guides describe infrastructure that does not exist

`docs/setup-tier1.md`, `docs/setup-supabase.md`, and `docs/setup-appwrite.md`
give step-by-step instructions for configuring sync backends. None of the code
they reference exists: there is no Supabase or Appwrite backend implementation,
no `SyncBackend` variant beyond `NoSyncBackend`, and no settings screen to select
one, since settings is a `DummyScreen`. `docs/setup-supabase.md:84` contains SQL
for a `users` table with a `role` column commented as
`OWNER, MANAGER, CASHIER, CREW`, which is a schema for a server that does not
exist in this repository.

A user following any of these guides end to end cannot reach a working outcome.
These are the highest-risk documents in the repository from a user-trust
perspective, because they read as operational runbooks rather than as design
notes.

### M7. ADR compliance is partial, and four ADRs have no implementation at all

Of the 18 ADRs, this review found roughly 68 of 180 individual technical claims
currently true. ADR-006 (GPL v3 licence), ADR-007, ADR-015 (payment gateway), and
ADR-017 have no corresponding implementation whatsoever. ADR-002's `BigDecimal`
requirement is contradicted by the code
([H7](#h7-inventory-stock-is-accumulated-in-double-contradicting-adr-002)), and
ADR-005's documented HOTP look-ahead window of 10 is implemented as 5
([M8](#m8-hotp-validation-is-timing-unsafe-permits-replay-and-uses-the-wrong-window)).

ADR-006 is the notable one: an entire decision record justifying GPL v3, with no
licence file in the repository
([C3](#c3-a-gpl-v3-project-ships-no-license-file)).

### M8. HOTP validation is timing-unsafe, permits replay, and uses the wrong window

The HOTP code generation itself is correct. It was checked against the RFC 4226
Appendix D test vectors and matches, and the tests assert those vectors, which is
exactly right. `validateCode` has three problems
(`shared-kmp/.../HOTPGenerator.kt:32-47`):

1. **Timing-unsafe comparison.** `generated == inputCode` is `String.equals`,
   which short-circuits on the first differing character.
2. **No consumption, so codes replay.** The function returns the matched counter
   but nothing persists it or advances the stored counter. A manager override code
   therefore remains valid indefinitely and can be reused any number of times.
   RFC 4226 requires the counter to advance on successful validation. This is the
   most serious of the three: an override code shared once with a cashier keeps
   working forever.
3. **Window is 5, ADR-005 documents 10** (`ADR-005:48` and `:86`). The parameter
   default is `lookAheadWindow: Int = 5`.

Because `validateCode` has no production call sites
([H3](#h3-rbac-manager-approval-and-hotp-validation-are-entirely-dead-code)),
none of this is currently exploitable. All three should be fixed before it is
wired up.

### M9. `minSdk` is 24, but the documentation states API 26

`CONTEXT.md:17` states a minimum SDK of API 26. Every module sets `minSdk = 24`
(`android-app/build.gradle.kts:16`, `core-data`, `core-ui`, `core-network`,
`feature-auth`, and `shared-kmp/build.gradle.kts:12`).

This is worth resolving deliberately rather than by picking one. API 24 and 25
lack some keystore behaviour that `KeystoreManager` relies on for stronger
guarantees, and the documented 26 was very likely chosen for that reason. Either
raise `minSdk` to 26 to match the security assumption, or lower the documented
figure and verify the keystore code paths on API 24.

`targetSdk = 34` alongside `compileSdk = 36` is also inconsistent with
`CONTEXT.md:18` ("latest stable"), and `tools:targetApi="31"` in the manifest
matches neither.

### M10. No `DATABASE_VERSION` constant, and one schema version has no schema change

`CONTEXT.md:180-181` instructs contributors to increment `DATABASE_VERSION` in
`KaupDatabase` on every entity change. No such constant exists. The version is
an inline literal (`KaupDatabase.kt:26`):

```kotlin
version = 4,
```

The exported schemas show a version bump with no schema change: `2.json` and
`3.json` have **identical** `identityHash` values
(`d0aaa1cc13fbac7563053865aa1b0a5d`), meaning version 3 differs from version 2 in
no way that Room can detect. This is harmless under the current destructive
migration policy but would produce a migration with nothing to migrate later.

Schemas are also exported to `core/core-data/schemas/`, not `/app/schemas/` as
`CONTEXT.md:184` states.

### M11. No `res/` directory and no `strings.xml`, so localisation is blocked

There is no `res/` directory and no `strings.xml` anywhere in the repository.
`CONTEXT.md` lists "do not write user-facing strings as hardcoded literals, use
`strings.xml`" in its "what not to do" section, and `CONTRIBUTING.md` has a
translation-contributions section. Every user-facing string in the app is
currently a hardcoded Kotlin literal, so there is nothing for a translator to
translate.

Fixing this early is much cheaper than later: extracting strings after the POS,
inventory, and reports screens are written is a large mechanical change across
every composable, whereas establishing the pattern now costs almost nothing.

### M12. `CONTEXT.md` contradicts itself on `:core-network` dependencies

`CONTEXT.md:39-40` describes `:core-network` as depending on `:shared-kmp` and
`:core-data`. `CONTEXT.md:80` states the rule that `core-*` modules may import
`:shared-kmp` **only**. Those two statements cannot both hold, and
`:core-network` does in fact depend on `:core-data`.

This matters because it is the architecture contract, and the module-boundary
rule is one of the three non-negotiables. A contributor cannot follow a rule that
contradicts the example beside it. Resolve by stating the intended exception
explicitly.

### M13. Broken documentation links

- `docs/adr/ADR-015-payment-gateway-architecture.md.md` has a doubled `.md`
  extension. `README.md:156` links the single-extension name, so the link 404s.
- `README.md:9`, `README.md:184`, and `README.md:187` all link `LICENSE`, which
  does not exist ([C3](#c3-a-gpl-v3-project-ships-no-license-file)).
- The README CI badge points at a workflow that does not exist
  ([H10](#h10-there-is-no-ci-and-the-readme-advertises-a-badge-for-a-workflow-that-does-not-exist)).

---

## Low findings

### L1. `KeystoreManager` could take two further hardening steps

`core/core-data/.../crypto/KeystoreManager.kt` is the strongest code in the
repository (see [what is already good](#what-is-already-good)). Two optional
improvements, neither a defect:

- `setUserAuthenticationRequired(true)` would bind key use to device
  authentication, so keys cannot be used while the device is locked.
- `setIsStrongBoxBacked(true)`, guarded by a feature check and fallback, would use
  hardware-isolated key storage where available.

### L2. Placeholder application resources, and no `FLAG_SECURE`

`AndroidManifest.xml:8-11` uses framework placeholders rather than app resources:

```xml
android:icon="@android:drawable/sym_def_app_icon"
android:theme="@android:style/Theme.NoTitleBar"
```

Expected at alpha, and it follows from having no `res/` directory
([M11](#m11-no-res-directory-and-no-stringsxml-so-localisation-is-blocked)).
Separately, no window sets `FLAG_SECURE`, so the PIN entry screen and any future
payment screen appear in the system recents screenshot and in screen recordings.
`FLAG_SECURE` on the lock screen is a one-line change worth making alongside
[C1](#c1-the-user-pin-is-stored-and-compared-in-plaintext-in-a-column-named-pinhash).

### L3. Code hygiene: 8 non-null assertions, `println` instead of logging

Measured across all 83 Kotlin files:

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
engine is not debuggable without one.

### L4. Two of fourteen commits follow the project's own commit format

`CONTRIBUTING.md:162-175` mandates the format `type(scope): short description`
followed by a `Closes #123` footer. Exactly 2 of the 14 commits carry the footer.

Scope usage also drifts from the documented list: several commits use multiple
comma-separated scopes, for example
`feat(core-data, shared-kmp, feature-auth): implement session management and RBAC`,
where the specification calls for a single module name. One commit
(`feat(feature-auth, android-app): implement lock screen UI and DI setup`) has its
entire body collapsed onto the subject line as a run of hyphenated bullets, and
one subject appears twice in consecutive commits
(`feat(android-app): implement app shell, flavors, and adaptive navigation`).

Low severity, but worth fixing now via a commit-message hook or a CI check, since
the history is only 14 commits long and the convention is already written down.

### L5. Five dependency coordinates bypass the version catalog

`gradle/libs.versions.toml` is well organised and used consistently almost
everywhere, which makes the exceptions stand out. For example
`shared-kmp/build.gradle.kts:28`:

```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
```

This pins a version outside the catalog, so a catalog-wide coroutines upgrade
will leave it behind and can produce a version skew between the main and test
classpaths. Move these into the catalog.

### L6. `detectNegativeStockViolations` reports only the crossing event

Covered in [H8](#h8-conflictresolver-contains-no-conflict-resolution) and noted
here for the index: the guard
`if (previousStock >= 0.0 && currentStock < 0.0)` flags only the movement that
crosses zero, so further oversells while stock is already negative are not
reported.

---

## What is already good

This section is not filler. Several things here are done better than is typical,
and the remediation plan depends on them.

**`KeystoreManager` is genuinely well written.** AES-256-GCM, a fresh random IV
per encryption, keys generated in and never leaving the Android keystore, and
critically **no plaintext fallback path**. Many first attempts at keystore code
include a "if the keystore is unavailable, store it in plaintext" branch, which
silently destroys the guarantee. This one does not. It is also, ironically, most
of what is needed to fix
[C2](#c2-the-database-is-not-encrypted-contradicting-two-explicit-documentation-claims).

**HOTP generation is correct.** The implementation matches the RFC 4226 Appendix
D test vectors, and the test suite asserts those published vectors rather than
asserting whatever the implementation happens to produce. That is the right way
to test a standardised algorithm and it is frequently done wrong.

**`ConflictResolver.sortDeterministically` is the right idea.** Ordering by
timestamp, then `deviceId`, then `id` produces a stable total order across
devices without coordination, which is precisely what an offline-first
multi-device system needs. The problem is that the rest of the class does not
build on it, not the ordering itself.

**`Money` as a `value class` over `Long` minor units is the correct choice.**
Floating-point currency is one of the most common and most damaging mistakes in
POS software, and the author avoided it in the type. The findings in
[H6](#h6-money-arithmetic-is-routed-through-double-and-the-totals-do-not-reconcile)
are about arithmetic performed around `Money`, not about `Money` itself.

**The module structure is sound and the boundaries are actually respected.** With
the single exception noted in [M12](#m12-contextmd-contradicts-itself-on-core-network-dependencies),
which is a documentation contradiction rather than a code violation, no
`feature-*` module imports another, and `:shared-kmp` contains no `android.*` or
`androidx.*` imports. Two of the three non-negotiables are being followed in the
code. Getting KMP boundary discipline right this early is uncommon.

**The Gradle version catalog is well organised** and used consistently, with only
the five exceptions in [L5](#l5-five-dependency-coordinates-bypass-the-version-catalog).

**Writing down the architecture contract at all is the best decision in the
repository.** `CONTEXT.md` is why this review could measure the project against
its own intent rather than against outside preference. Most of the findings above
are gaps between `CONTEXT.md` and the code, which is only possible to state
because `CONTEXT.md` exists and is specific.

**F-Droid discipline is real.** No Firebase, no Google Play Services, no
committed secrets. The single breach
([M3](#m3-the-app-updater-ships-in-the-f-droid-flavor-violating-a-stated-non-negotiable))
is an unused dependency in the wrong source set.

---

## Remediation plan

Ordered so that each stage makes the next one safer. Stage 0 is deliberately tiny
and should land as one PR.

### Stage 0: stop the bleeding (hours, one PR)

1. Commit the GPL v3 `LICENSE` file
   ([C3](#c3-a-gpl-v3-project-ships-no-license-file)). Five minutes, removes the
   legal defect, unblocks F-Droid.
2. `git update-index --chmod=+x gradlew`
   ([H2](#h2-gradlew-is-committed-non-executable-so-every-documented-build-command-fails)).
3. Set `android:allowBackup="false"`
   ([C6](#c6-allowbackuptrue-makes-the-plaintext-database-and-pin-extractable)).
4. Make the PIN length bounds agree
   ([C5](#c5-a-5-or-6-digit-pin-permanently-locks-the-owner-out-of-the-app)).
5. Amend `CONTEXT.md:20` and `README.md:23` to stop claiming encryption until it
   exists ([C2](#c2-the-database-is-not-encrypted-contradicting-two-explicit-documentation-claims)).
   Withdrawing a false security claim is urgent; implementing it is not.

### Stage 1: make the project verifiable (days)

6. Add `alias(libs.plugins.kotlin.android)` to the five Android modules and
   confirm the tree builds
   ([C4](#c4-no-module-applies-the-kotlin-android-plugin)). Do this before
   anything else in this stage, since nothing below can be verified otherwise.
7. Add `android-app/proguard-rules.pro`
   ([M1](#m1-the-release-build-cannot-succeed-proguard-rulespro-does-not-exist)).
8. Add `.github/workflows/ci.yml` running assemble, release assemble, and `test`
   on pull requests, and point the README badge at it
   ([H10](#h10-there-is-no-ci-and-the-readme-advertises-a-badge-for-a-workflow-that-does-not-exist)).

### Stage 2: fix the credential chain (days)

9. Replace plaintext PIN storage with a salted KDF, add a migration, and add the
   test that the stored value differs from the input
   ([C1](#c1-the-user-pin-is-stored-and-compared-in-plaintext-in-a-column-named-pinhash)).
10. Add persistent failed-attempt counting and lockout
    ([H4](#h4-no-rate-limiting-or-lockout-on-pin-entry)).
11. Encrypt the database with SQLCipher, sealing the passphrase with the existing
    `KeystoreManager`, then restore the documentation claim removed in step 5
    ([C2](#c2-the-database-is-not-encrypted-contradicting-two-explicit-documentation-claims)).
12. Add `FLAG_SECURE` to the lock screen ([L2](#l2-placeholder-application-resources-and-no-flag_secure)).

### Stage 3: remove the ambiguity before building on it (days)

13. Delete one of the two duplicate domain hierarchies and repoint the tests at
    the surviving implementation
    ([H5](#h5-duplicate-parallel-domain-hierarchies-the-adr-canonical-interfaces-are-the-dead-ones)).
14. Seed the default location from a Room callback and add an instrumented test
    that inserts an item
    ([H1](#h1-the-default-location-is-never-seeded-while-locationid-is-a-non-null-foreign-key)).
15. Add `syncStatus` to the three entities missing it, plus a type converter, and
    `locationId` to `UserEntity`
    ([H9](#h9-syncstatus-exists-on-one-of-four-room-entities)). Do this inside the
    destructive-migration window, where it is free.
16. Give `RoleConverter` a least-privileged fallback
    ([M4](#m4-roleconverter-crashes-on-any-unrecognised-role-value)).
17. Move `kmp-app-updater` out of `commonMain`, or remove it while unused
    ([M3](#m3-the-app-updater-ships-in-the-f-droid-flavor-violating-a-stated-non-negotiable)).

### Stage 4: money and stock correctness (1 to 2 weeks)

18. Write an ADR defining the rounding, tax-presentation, and negative-quantity
    contract, then fix `SalesCalculator` against it and add the reconciliation
    property test
    ([H6](#h6-money-arithmetic-is-routed-through-double-and-the-totals-do-not-reconcile)).
19. Replace `Double` quantities with scaled integers or `BigDecimal`, and unify
    the replay ordering between `InventoryEngine` and `ConflictResolver`
    ([H7](#h7-inventory-stock-is-accumulated-in-double-contradicting-adr-002)).
20. Decide and implement the conflict-resolution policy, or rename the class to
    match what it does ([H8](#h8-conflictresolver-contains-no-conflict-resolution)).
21. Fix HOTP validation: constant-time comparison, counter advance on success,
    window of 10
    ([M8](#m8-hotp-validation-is-timing-unsafe-permits-replay-and-uses-the-wrong-window)).
22. Wire one restricted action through `hasPermission`, `ManagerApprovalOverlay`,
    and `validateCode` end to end
    ([H3](#h3-rbac-manager-approval-and-hotp-validation-are-entirely-dead-code)).

### Stage 5: documentation truth and hygiene (ongoing)

23. Add a status banner to every unimplemented design document, and to all three
    setup guides in particular
    ([M5](#m5-the-documented-product-is-roughly-ten-times-the-implemented-one),
    [M6](#m6-the-three-backend-setup-guides-describe-infrastructure-that-does-not-exist)).
24. Resolve the `CONTEXT.md` self-contradiction and the `WAITER` reference
    ([M12](#m12-contextmd-contradicts-itself-on-core-network-dependencies)).
25. Reconcile `minSdk` with the documented API level
    ([M9](#m9-minsdk-is-24-but-the-documentation-states-api-26)).
26. Fix the doubled ADR-015 filename and the broken links
    ([M13](#m13-broken-documentation-links)).
27. Introduce `res/strings.xml` and extract strings before the POS and inventory
    screens are written
    ([M11](#m11-no-res-directory-and-no-stringsxml-so-localisation-is-blocked)).
28. Add flavor source sets, a `DATABASE_VERSION` constant, a logging
    abstraction, catalog the stray dependencies, and add a commit-message check
    ([M2](#m2-build-flavors-are-declared-but-no-flavor-source-sets-exist),
    [M10](#m10-no-database_version-constant-and-one-schema-version-has-no-schema-change),
    [L3](#l3-code-hygiene-8-non-null-assertions-println-instead-of-logging),
    [L5](#l5-five-dependency-coordinates-bypass-the-version-catalog),
    [L4](#l4-two-of-fourteen-commits-follow-the-projects-own-commit-format)).

---

## Appendix A: reproducing every measurement

All commands are run from the repository root at commit `b7f756e`.

**Repository size and test counts**

```bash
find . -name "*.kt" -not -path "./.git/*" | wc -l                    # 83
find . -name "*.kt" -not -path "./.git/*" -exec cat {} + | wc -l     # 3394
grep -rn "@Test" --include=*.kt . | wc -l                            # 32
find . -path "*src/*Test*" -name "*.kt" -not -path "./.git/*" \
  | sed 's|/src/.*||' | sort -u                                      # ./shared-kmp only
```

**C1: plaintext PIN, and no hashing anywhere**

```bash
sed -n '88,95p' feature/feature-auth/src/main/kotlin/app/kaup/feature/auth/ui/onboarding/OnboardingViewModel.kt
sed -n '80,92p' feature/feature-auth/src/main/kotlin/app/kaup/feature/auth/ui/LockScreen.kt
grep -rn -i "MessageDigest\|bcrypt\|scrypt\|argon\|PBKDF2\|sha256\|hashOf" \
  --include=*.kt --include=*.kts --include=*.toml .                  # zero matches
```

**C2: database not encrypted**

```bash
grep -rn -i "sqlcipher\|zetetic\|SupportFactory\|openHelperFactory\|passphrase" \
  --include=*.kt --include=*.kts --include=*.toml .                  # zero matches
cat android-app/src/main/kotlin/app/kaup/android/di/DatabaseModule.kt
grep -n -i "encrypt" CONTEXT.md README.md
```

**C3: no LICENSE file**

```bash
ls -a                                                # no LICENSE entry
find . -iname "*licen*" -not -path "./.git/*"        # only docs/adr/ADR-006-gpl-v3-license.md
grep -n -i "licen\|gpl" README.md                    # badge and 3 links to LICENSE
```

**C4: Kotlin Android plugin never applied**

```bash
grep -rn "kotlin.android\|kotlin(\"android\")\|org.jetbrains.kotlin.android" \
  --include=*.kts --include=*.toml .
# exactly 2 hits: build.gradle.kts:5 (apply false) and libs.versions.toml:46

for f in android-app/build.gradle.kts core/core-data/build.gradle.kts \
         core/core-ui/build.gradle.kts core/core-network/build.gradle.kts \
         feature/feature-auth/build.gradle.kts shared-kmp/build.gradle.kts; do
  echo "--- $f"; sed -n '/^plugins {/,/^}/p' $f
done
```

**C5: PIN length mismatch**

```bash
grep -n "length" feature/feature-auth/src/main/kotlin/app/kaup/feature/auth/ui/onboarding/OnboardingViewModel.kt
# :30  ownerPin.length >= 4        :56  pin.length <= 6
grep -n "pin.length" feature/feature-auth/src/main/kotlin/app/kaup/feature/auth/ui/LockScreen.kt
# :82  if (pin.length == 4)
```

**C6 and L2: manifest**

```bash
cat android-app/src/main/AndroidManifest.xml
grep -rn "FLAG_SECURE" --include=*.kt .              # zero matches
```

**H1: location never seeded**

```bash
grep -rn "addCallback\|RoomDatabase.Callback\|createFromAsset" --include=*.kt .
# zero matches (the onCreate hits are MainActivity's activity lifecycle)
grep -rn "locationDao\|LocationDao" --include=*.kt . # only declaration, accessor, DI provider
grep -rn "itemDao\|stockMovementDao" --include=*.kt . \
  | grep -v "di/DatabaseModule\|KaupDatabase.kt"     # zero consumers
grep -n "locationId\|ForeignKey" core/core-data/src/main/kotlin/app/kaup/core/data/entities/ItemEntity.kt
```

**H2: gradlew file mode**

```bash
git ls-files --stage gradlew                         # 100644, expected 100755
```

**H3: dead RBAC and HOTP**

```bash
grep -rn "ManagerApprovalOverlay" --include=*.kt .   # definition only
grep -rn "hasPermission" --include=*.kt .            # definitions only
grep -rn "validateCode" --include=*.kt .             # definition plus 4 test references
```

**H5: duplicate hierarchies**

```bash
grep -rn "import app.kaup.shared.*\(Permission\|Role\)" --include=*.kt core feature android-app \
  | sed 's/.*import/import/' | sort | uniq -c | sort -rn
# 5 domain.models.auth.Permission, 3 domain.models.auth.Role, 1 getDefaultPermissions
grep -rn "RoleDefaults" --include=*.kt .             # definition only, zero call sites
grep -rn "WAITER\|CREW" --include=*.kt --include=*.md .
grep -rn "SyncBackend" --include=*.kt core android-app  # all use shared.sync.SyncBackend
```

**H7: floating-point stock, verified by execution**

```bash
python3 -c "
def compute(m):
    acc=0.0
    for d,q in m: acc = acc+q if d=='IN' else acc-q
    return acc
print(repr(compute([('IN',0.1),('IN',0.1),('IN',0.1),('OUT',0.3)])))
print(repr(compute([('IN',0.1),('OUT',0.3),('IN',0.1),('IN',0.1)])))
print(repr(compute([('IN',0.7),('IN',0.1),('OUT',0.8)])))
"
# 5.551115123125783e-17
# 2.7755575615628914e-17   (same movements, different order, different result)
# -1.1102230246251565e-16  (balanced ledger reported as negative stock)
```

**H9: syncStatus coverage**

```bash
grep -rn "syncStatus" --include=*.kt .               # 2 matches only
grep -rn "tableName" --include=*.kt core/core-data   # items, locations, stock_movements, users
```

**H10 and H11: no CI, no Android tests**

```bash
ls .github/                                          # ISSUE_TEMPLATE, pull_request_template.md
find . -name "*.yml" -path "*workflows*"             # nothing
```

**M1, M2, M3: build configuration**

```bash
find . -name "proguard*" -not -path "./.git/*"       # nothing
sed -n '20,45p' android-app/build.gradle.kts         # 3 flavors, isMinifyEnabled = true
ls android-app/src/                                  # main only
sed -n '18,26p' shared-kmp/build.gradle.kts          # updater in commonMain
```

**M5: documented versus actual tables**

```bash
python3 - <<'PY'
import re
lines=open('docs/modules.md').read().split('\n')
tables=set(); cur=None
for l in lines:
    if l.startswith('### Room tables'): cur=True; continue
    if l.startswith('#'): cur=None if not l.startswith('####') else cur
    if cur: tables.update(re.findall(r'`([a-z][a-z0-9_]*)`', l))
print(len(tables), sorted(tables))
PY
# 26 documented; only items, stock_movements, users exist; locations is undocumented
```

**M10: schema versions**

```bash
python3 -c "
import json,glob
for f in sorted(glob.glob('core/core-data/schemas/*/*.json')):
    d=json.load(open(f)); print(f, d['database']['identityHash'])
"
# 2.json and 3.json share identityHash d0aaa1cc13fbac7563053865aa1b0a5d
```

**M11 and L3: resources and hygiene**

```bash
find . -name "strings.xml" -o -type d -name "res" -not -path "./.git/*"   # nothing
grep -rn '!!' --include=*.kt . | wc -l               # 8
grep -rn 'println' --include=*.kt . | wc -l          # 2
grep -rn 'TODO' --include=*.kt . | wc -l             # 2
grep -rn 'Log\.' --include=*.kt . | wc -l            # 0
```

**L4: commit format**

```bash
git log --format="%H" | while read h; do git log -1 --format="%b" $h; done \
  | grep -ci "closes #"                              # 2 of 14
```

**Build verification was not possible**

```bash
which java javac gradle                              # not found
echo "$ANDROID_HOME $ANDROID_SDK_ROOT"               # both unset
```

---

## Appendix B: finding index by severity

### Critical (6)

| ID | Finding |
|---|---|
| [C1](#c1-the-user-pin-is-stored-and-compared-in-plaintext-in-a-column-named-pinhash) | PIN stored and compared in plaintext in a column named `pinHash` |
| [C2](#c2-the-database-is-not-encrypted-contradicting-two-explicit-documentation-claims) | Database not encrypted, contradicting `CONTEXT.md` and `README.md` |
| [C3](#c3-a-gpl-v3-project-ships-no-license-file) | GPL v3 project ships no LICENSE file |
| [C4](#c4-no-module-applies-the-kotlin-android-plugin) | No module applies the Kotlin Android plugin |
| [C5](#c5-a-5-or-6-digit-pin-permanently-locks-the-owner-out-of-the-app) | A 5 or 6 digit PIN permanently locks the owner out |
| [C6](#c6-allowbackuptrue-makes-the-plaintext-database-and-pin-extractable) | `allowBackup="true"` exposes the plaintext database and PIN |

### High (11)

| ID | Finding |
|---|---|
| [H1](#h1-the-default-location-is-never-seeded-while-locationid-is-a-non-null-foreign-key) | Default location never seeded while `locationId` is a non-null FK |
| [H2](#h2-gradlew-is-committed-non-executable-so-every-documented-build-command-fails) | `gradlew` committed non-executable |
| [H3](#h3-rbac-manager-approval-and-hotp-validation-are-entirely-dead-code) | RBAC, manager approval, and HOTP validation are dead code |
| [H4](#h4-no-rate-limiting-or-lockout-on-pin-entry) | No rate limiting or lockout on PIN entry |
| [H5](#h5-duplicate-parallel-domain-hierarchies-the-adr-canonical-interfaces-are-the-dead-ones) | Duplicate domain hierarchies; the tested one is dead |
| [H6](#h6-money-arithmetic-is-routed-through-double-and-the-totals-do-not-reconcile) | Money arithmetic via `Double`; totals do not reconcile |
| [H7](#h7-inventory-stock-is-accumulated-in-double-contradicting-adr-002) | Stock accumulated in `Double`, contradicting ADR-002 |
| [H8](#h8-conflictresolver-contains-no-conflict-resolution) | `ConflictResolver` contains no conflict resolution |
| [H9](#h9-syncstatus-exists-on-one-of-four-room-entities) | `syncStatus` on one of four Room entities |
| [H10](#h10-there-is-no-ci-and-the-readme-advertises-a-badge-for-a-workflow-that-does-not-exist) | No CI, and the README badge points at a missing workflow |
| [H11](#h11-zero-tests-in-all-four-android-modules) | Zero tests in all four Android modules |

### Medium (13)

| ID | Finding |
|---|---|
| [M1](#m1-the-release-build-cannot-succeed-proguard-rulespro-does-not-exist) | Release build cannot succeed: `proguard-rules.pro` missing |
| [M2](#m2-build-flavors-are-declared-but-no-flavor-source-sets-exist) | Flavors declared but no flavor source sets exist |
| [M3](#m3-the-app-updater-ships-in-the-f-droid-flavor-violating-a-stated-non-negotiable) | App updater ships in the F-Droid flavor |
| [M4](#m4-roleconverter-crashes-on-any-unrecognised-role-value) | `RoleConverter` crashes on an unrecognised role |
| [M5](#m5-the-documented-product-is-roughly-ten-times-the-implemented-one) | Documented product is roughly ten times the implemented one |
| [M6](#m6-the-three-backend-setup-guides-describe-infrastructure-that-does-not-exist) | Three setup guides describe non-existent infrastructure |
| [M7](#m7-adr-compliance-is-partial-and-four-adrs-have-no-implementation-at-all) | ADR compliance partial; four ADRs unimplemented |
| [M8](#m8-hotp-validation-is-timing-unsafe-permits-replay-and-uses-the-wrong-window) | HOTP validation timing-unsafe, replayable, wrong window |
| [M9](#m9-minsdk-is-24-but-the-documentation-states-api-26) | `minSdk` 24 versus documented API 26 |
| [M10](#m10-no-database_version-constant-and-one-schema-version-has-no-schema-change) | No `DATABASE_VERSION`; one schema bump has no change |
| [M11](#m11-no-res-directory-and-no-stringsxml-so-localisation-is-blocked) | No `res/` or `strings.xml`; localisation blocked |
| [M12](#m12-contextmd-contradicts-itself-on-core-network-dependencies) | `CONTEXT.md` contradicts itself on `:core-network` |
| [M13](#m13-broken-documentation-links) | Broken documentation links |

### Low (6)

| ID | Finding |
|---|---|
| [L1](#l1-keystoremanager-could-take-two-further-hardening-steps) | `KeystoreManager` hardening opportunities |
| [L2](#l2-placeholder-application-resources-and-no-flag_secure) | Placeholder resources, no `FLAG_SECURE` |
| [L3](#l3-code-hygiene-8-non-null-assertions-println-instead-of-logging) | 8 `!!`, `println` instead of logging, no `Log.*` |
| [L4](#l4-two-of-fourteen-commits-follow-the-projects-own-commit-format) | 2 of 14 commits follow the project's commit format |
| [L5](#l5-five-dependency-coordinates-bypass-the-version-catalog) | Five dependencies bypass the version catalog |
| [L6](#l6-detectnegativestockviolations-reports-only-the-crossing-event) | `detectNegativeStockViolations` reports only the crossing event |

**Total: 36 findings (6 Critical, 11 High, 13 Medium, 6 Low).**
