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
| Room entities | 4 | 26 tables in `docs/modules.md` | 22 absent |
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
