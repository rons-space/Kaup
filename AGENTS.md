# Agent Instructions

Kaup, an offline-first Android point-of-sale application: Kotlin Multiplatform shared
domain logic, Jetpack Compose and Material 3 on Android, Room, Hilt, and Gradle with a
version catalog. This file covers the conventions that are **not** discoverable from the
code and that cause real damage when guessed at.

It deliberately does not repeat what is already written down:

- [`CONTEXT.md`](CONTEXT.md) is the architecture contract: module structure, the three
  non-negotiables (offline-first, module boundaries, F-Droid clean), RBAC, the sync
  status lifecycle, database rules, and naming conventions. Read it before writing code.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) covers branch naming, Conventional Commits, and
  the pull request checklist.
- [`docs/GIT_WORKFLOW.md`](docs/GIT_WORKFLOW.md) covers everything below in more detail.
  Read it before doing anything involving branches or merges.

## Branching, in one paragraph

`main` is the default branch. `dev` is the integration branch. Feature and fix branches
are cut from `dev` and open pull requests **into `dev`**. Batches of `dev` are promoted
to `main` through a promotion pull request. A workflow syncs `dev` after anything lands
on `main`: a promotion is a fast-forward, so the two branches sit at the **same commit**
between promotions, while a hotfix landing while `dev` has moved on is merged down
instead and leaves `dev` containing `main` but ahead of it.

## Rules that break things if broken

1. **Promotion and hotfix pull requests must be merged with "Create a merge commit".**
   Squash and rebase rewrite history into commits `dev` has never seen, which makes the
   automatic fast-forward impossible and needs a force push to recover. Both are disabled
   in repository settings. Do not re-enable them.

2. **Never force push `main` or `dev`, and never sync them by hand.**
   `.github/workflows/sync-dev-to-main.yml` owns that. If it fails, read its run summary
   rather than fixing the branches manually.

## CI

CI is a single workflow, `.github/workflows/android.yml`: JDK 26 (Temurin) running
`./gradlew build` on every push to `main` and `dev` and on every pull request targeting
either. There is no `paths-ignore`, so a documentation-only change costs a full build,
and there is no concurrency group, so every push to an open pull request is another full
run. Bundle small edits rather than pushing them one at a time.

Do not run the build or the test suite locally to validate a change. Push and let CI do
it. The Gradle build has toolchain and plugin configuration that is not reproducible in a
bare environment, so a local failure is weak evidence and a local pass is weaker.

The sync workflow does not spend CI: its push uses `GITHUB_TOKEN`, which GitHub does not
let retrigger workflows, so healing `dev` costs nothing even though `android.yml`
triggers on pushes to `dev`.

## Conventions

- Match the surrounding code. This repository favours explanatory comments that record
  *why* a non-obvious choice was made, especially in CI configuration, Gradle build
  files, and anything touching the sync or permission model. Preserve them, and add to
  them when the reasoning is not self-evident.
- The rules in `CONTEXT.md` under "What Not to Do" are enforced in review. The ones most
  often broken by accident: no `feature-*` module may depend on another `feature-*`
  module, `:shared-kmp` must contain no `android.*` or `androidx.*` imports, and
  user-facing strings belong in `strings.xml` rather than as Kotlin literals.
