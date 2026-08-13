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
