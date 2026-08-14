# Agent Instructions

Kaup, a free, open-source, offline-first Android POS built with Kotlin Multiplatform,
Jetpack Compose (Material 3), Hilt and Room, distributed under GPL v3. This file covers
the conventions that are not discoverable from the code and that cause real damage when
guessed at. [`CONTEXT.md`](CONTEXT.md) is the primary architecture and conventions
reference; read it, and read [`docs/GIT_WORKFLOW.md`](docs/GIT_WORKFLOW.md) before doing
anything involving branches or merges.

## Branching, in one paragraph

`main` is the default branch and deploys. `dev` is the integration branch. Feature and
fix branches are cut from `dev` and open pull requests **into `dev`**. Batches of `dev`
are promoted to `main` through a promotion pull request. A workflow syncs `dev` after
anything lands on `main`: a promotion is a fast-forward, so the two branches sit at the
**same commit** between promotions, while a hotfix landing while `dev` has moved on is
merged down instead and leaves `dev` containing `main` but ahead of it.

## Rules that break things if broken

1. **Promotion and hotfix pull requests must be merged with "Create a merge commit".**
   Squash and rebase rewrite history into commits `dev` has never seen, which makes the
   automatic fast-forward impossible and needs a force push to recover. Both are disabled
   in repository settings. Do not re-enable them.

2. **Never force push `main` or `dev`, and never sync them by hand.**
   `.github/workflows/sync-dev-to-main.yml` owns that. If it fails, read its run summary
   rather than fixing the branches manually.

## CI

There is currently no automated test CI in this repository; the only workflow is the
branch-sync workflow above. Validation of code changes relies on the local Gradle build
and the `kotlin.test` suites in `commonTest`. Do not assume a green pipeline exists to
catch mistakes; when CI is added, prefer letting it run the full suite rather than
treating a local full build as a prerequisite for every change.

Match the surrounding code. This repository favours explanatory comments that record
*why* a non-obvious choice was made, especially in build configuration, migrations and
test setup. Preserve them, and add to them when the reasoning is not self-evident.
