# Contributing Guide

Thank you for considering a contribution to this project. This guide covers
everything you need to make a successful contribution — from setting up your
environment to getting your PR merged.

---

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Ways to Contribute](#ways-to-contribute)
- [Before You Start](#before-you-start)
- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [Module Boundary Rules](#module-boundary-rules)
- [Branch Naming](#branch-naming)
- [Commit Messages](#commit-messages)
- [Pull Request Requirements](#pull-request-requirements)
- [Adding a New Module](#adding-a-new-module)
- [Adding a New Permission](#adding-a-new-permission)
- [Translation Contributions](#translation-contributions)
- [Documentation Contributions](#documentation-contributions)
- [Review Turnaround](#review-turnaround)

---

## Code of Conduct

Be respectful. Critique code, not people. Contributions from all backgrounds
and experience levels are welcome. Issues and PRs that are disrespectful will
be closed without engagement.

---

## Ways to Contribute

You do not need to write code to contribute meaningfully:

- **Fix a bug** — look for issues labeled `bug`
- **Build a feature** — look for issues labeled `good first issue` or
  `help wanted`
- **Improve documentation** — fix unclear wording, add examples, improve
  setup guides
- **Translate the app** — add or improve strings in `res/values-*/strings.xml`
- **Test and report bugs** — install the latest alpha, try to break it,
  open a detailed issue
- **Propose a country-specific integration** — payment methods and regional
  tax adapters are explicitly community contribution territory; open a
  proposal issue first

---

## Before You Start

1. **Search existing issues** before opening a new one — your bug or feature
   may already be tracked
2. **Comment on the issue** to claim it before starting work — this prevents
   two contributors building the same thing simultaneously
3. **For large changes**, open a discussion issue first describing your
   approach and wait for maintainer feedback before writing code — this
   prevents wasted effort on approaches that will not be merged

---

## Development Setup

### Requirements

- Android Studio Meerkat or later
- JDK 17 or later
- Android SDK API 24 minimum, latest stable target SDK
- Docker (optional — only needed to run the Ktor server locally)

### Steps

```bash
# 1. Fork the repository on GitHub, then clone your fork
git clone https://github.com/YOUR_USERNAME/PROJECT_NAME.git
cd PROJECT_NAME

# 2. Open in Android Studio
# File → Open → select the root directory

# 3. Let Gradle sync complete

# 4. Run the app on an emulator or device
# Run → Run 'android-app'

# 5. Run unit tests (no emulator needed)
./gradlew :shared-kmp:allTests
./gradlew test

# 6. Run a specific feature module's tests
./gradlew :feature-pos:test
```

### Running the Ktor Server (Tier 1 — optional)

```bash
cd ktor-server
docker-compose up
# Server starts at http://localhost:8080
# Configure the Android app to point to your machine's LAN IP in Settings
```

---

## Project Structure

See [`/docs/modules.md`](docs/modules.md) for a full description of every
module, what it owns, and which Room tables it accesses.

See [`/docs/architecture.md`](docs/architecture.md) for data flow diagrams,
sync lifecycle, and auth flow.

---

## Module Boundary Rules

These rules are non-negotiable. PRs that violate them will not be merged
regardless of the feature quality.

```
feature-*   →  may depend on core-* and :shared-kmp ONLY
feature-*   →  MUST NOT import from another feature-* module
core-*      →  may depend on :shared-kmp ONLY
shared-kmp  →  MUST NOT import any android.* or androidx.* classes
```

Cross-feature communication happens via:
- Shared data models in `:core-data`
- Navigation events handled in `:android-app`

If your feature genuinely needs data from another feature module, expose
it through a repository interface in `:core-data` — do not create a direct
module dependency.

---

## Branch Naming

```
feat/short-description        → new feature
fix/short-description         → bug fix
docs/short-description        → documentation only
test/short-description        → tests only
refactor/short-description    → refactor with no behavior change
chore/short-description       → build, CI, dependency updates
```

Examples:
```
feat/hotp-qr-provisioning
fix/stock-movement-negative-display
docs/improve-tier1-setup-guide
test/sales-calculator-edge-cases
```

---

## Commit Messages

This project uses **Conventional Commits**. Every commit message must follow
this format:

```
type(scope): short description

Optional longer body explaining the why, not the what.
The what is visible in the diff.

Closes #123
```

**Types**: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `perf`

**Scope**: the module name without the colon prefix — `pos`, `inventory`,
`auth`, `shared-kmp`, `ktor-server`, `docs`, `ci`

**Examples:**
```
feat(inventory): add reorder level threshold per item

fix(pos): prevent cart total from rounding incorrectly on split payment

docs(adr): add ADR-014 for barcode label printing decision

test(shared-kmp): add edge cases for ConflictResolver with simultaneous writes

Closes #88
```

---

## Pull Request Requirements

Every PR must satisfy all of the following before it will be reviewed:

- [ ] CI passes — all unit and integration tests green
- [ ] New behavior is covered by tests — untested features will be sent back
- [ ] Module boundary rules are not violated
- [ ] No proprietary dependencies introduced — all new libraries must be
      MIT, Apache 2.0, LGPL, or GPL compatible
- [ ] No hardcoded strings — all user-facing text uses `strings.xml` entries
- [ ] No hardcoded API keys, URLs, or secrets in source
- [ ] PR description explains **what** changed and **why**
- [ ] If the PR closes an issue, it includes `Closes #NUMBER` in the description
- [ ] If the PR adds a new restricted action, a corresponding `Permission`
      constant has been added — see [Adding a New Permission](#adding-a-new-permission)
- [ ] If the PR adds a new module, the module guide has been updated —
      see [Adding a New Module](#adding-a-new-module)

---

## Adding a New Module

1. Create the Gradle module under `feature/` following the existing module
   structure
2. Add the module dependency to `:android-app/build.gradle.kts`
3. Register the module's nav graph in `:android-app` conditionally if it
   requires a feature flag
4. If the module requires a feature flag, add the flag to `FeatureFlags`
   in `:core-data` and to the Settings UI in `:feature-settings`
5. Add a section for the new module in `/docs/modules.md` covering:
   - What it owns
   - What it does NOT own
   - Permissions checked
   - Room tables read and written
6. Open a PR — module proposal issues should be discussed and approved
   before implementation begins

---

## Adding a New Permission

Every new user-facing action that should be restricted to specific roles
must have a corresponding permission constant.

1. Add the constant to the `Permission` enum in `:shared-kmp/models`
2. Add the permission to the appropriate role's default set in
   `RoleDefaults` in `:shared-kmp/domain`
3. Add the permission to the permission catalogue table in
   `/docs/modules.md` under the relevant module section
4. Add the UI check in the feature module:
   ```kotlin
   if (session.hasPermission(Permission.YOUR_NEW_PERMISSION)) {
       YourRestrictedComposable()
   }
   ```
5. If the action requires manager approval rather than being hidden entirely,
   wrap it in `ManagerApprovalOverlay` from `:core-ui`

---

## Translation Contributions

All user-facing strings are in `android-app/src/main/res/values/strings.xml`.

To add or improve a translation:

1. Create or edit `res/values-LANGUAGE_CODE/strings.xml`
   (e.g., `values-id/strings.xml` for Bahasa Indonesia)
2. Translate all strings from the base `values/strings.xml`
3. Do not translate string keys — only the values
4. Open a PR with the title: `docs(i18n): add/improve LANGUAGE_NAME translation`

Translation PRs do not require code review beyond a quick sanity check —
they will be merged promptly.

---

## Documentation Contributions

Documentation improvements are as valuable as code contributions.

- **ADRs** — if you believe a significant architectural decision is missing
  an ADR, open an issue proposing it before writing
- **Module guide** — if a module description is unclear or outdated, open
  a PR directly with the correction
- **Setup guides** — if a setup step failed for you and you figured out the
  fix, please document it
- **User manual** — post-v1.0 community contribution; any store workflow
  documentation is welcome

---

## Review Turnaround

The maintainer aims to provide an initial review within **7 days** of a PR
being opened. For `good first issue` PRs the target is **3 days**.

If your PR has not received a review after 7 days, leave a comment on the
PR to prompt a review — do not open a duplicate PR.

Complex architectural changes may require a longer review cycle. Opening a
discussion issue before implementing significantly reduces this risk.
