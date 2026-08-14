#!/usr/bin/env bash
#
# Closes the code review findings that sprints 0 through 2 actually resolved.
#
# Why this script exists: GitHub only auto-closes an issue when the pull request
# that references it merges into the *default* branch. Every sprint PR targets
# `dev`, so none of the "Closes #NNN" references ever fired, and promoting `dev`
# to `main` will not fire them retroactively either. On top of that, the
# integration token that filed these issues can create them but cannot close
# them, so this has to run as a real user.
#
# Run from a machine authenticated as a user with push access:
#
#   ./scripts/close-resolved-issues.sh
#
# It is idempotent: closing an already closed issue is a no-op.
#
# Every number below was verified against the merged tree on `dev`, not against
# what a pull request description claimed. The deliberate omissions are listed
# at the bottom; read them before adding to this list.
#
set -euo pipefail

REPO="${REPO:-rons-space/Kaup}"

command -v gh >/dev/null || { echo "gh is required" >&2; exit 1; }

close() { # $1=issue  $2=reason
  if gh issue close "$1" --repo "$REPO" --comment "$2" >/dev/null 2>&1; then
    echo "  closed #$1"
  else
    echo "  #$1 FAILED (already closed, or insufficient permission)" >&2
  fi
}

echo "Sprint 0 — stop the bleeding (PR #262)"
close 160 "Fixed in #262: GPL v3 LICENSE added at the repository root, per ADR-006."
close 162 "Fixed in #262: PinPolicy is now the single source of truth for PIN length and an explicit Unlock button replaces the auto-submit that locked the owner out."
close 163 "Fixed in #262: allowBackup is false and data_extraction_rules.xml excludes the database."
close 177 "Fixed in #262: SECURITY.md now separates documented claims from implementation status, and the unimplemented controls are marked as such."
close 179 "Fixed in #262: android-app/proguard-rules.pro exists with real keep rules, so the release build can run."
close 202 "Fixed in #262: .idea is untracked, signing material is gitignored, and the dead ktor catalog pin is gone."

echo "Sprint 1 — the schema window (PR #263)"
close 158 "Fixed in #263: PIN is stored as a PBKDF2 hash with a per-user salt and iteration count. PinHasher and PinAuthenticator own the verification path."
close 164 "Fixed in #263: LocationEntity.DEFAULT_ID is seeded by a RoomDatabase.Callback on create, so the non-null foreign key always resolves."
close 167 "Fixed in #263: PinLockoutPolicy applies escalating lockout on failed attempts, backed by failedPinAttempts and pinLockoutUntilUptimeMillis."
close 172 "Fixed in #263: syncStatus is on all four entities via a converter, confirmed in the committed schema 5.json."
close 182 "Fixed in #263: RoleConverter falls back to the least-privilege role instead of throwing on an unrecognised value."
close 188 "Fixed in #263: KaupDatabase.DATABASE_VERSION is a named constant with version history in the doc comment."
close 192 "Fixed in #263: StockMovementEntity carries movementType, direction and transactionId, reconciling it with the domain model and ADR-002."

echo "Sprint 2 — one domain hierarchy (PR #264)"
close 168 "Fixed in #264: the dead shared.models and shared.sync hierarchy is deleted and :core-network speaks the ADR-canonical interfaces."
close 180 "Fixed in #264: github, fdroid and playstore source sets exist under android-app/src/."
close 181 "Fixed in #264: kmp-app-updater is a githubImplementation dependency, so the sideload updater no longer ships in the F-Droid build."
close 190 "Fixed in #264: CONTEXT.md and docs/modules.md agree that :core-network depends on :shared-kmp only."

echo "Resolved by the toolchain"
close 161 "Resolved: AGP 9.2.0 applies Kotlin support to Android modules itself. Green CI on dev compiling Kotlin in all five Android modules is the evidence."

echo "Decisions settled in docs (PR #264)"
close 233 "Decided in #264: LockScreen stays whole in :feature-auth. Nothing outside that module composes it, so the :core-ui split cost a two-module round trip for nothing. ManagerApprovalOverlay stays in :core-ui because POS, inventory and settings all raise it."
close 234 "Decided in #264: the fourth role is CREW, matching ADR-009 and the enforced code. CONTEXT.md, README.md and ROADMAP.md were updated from WAITER."

echo
echo "Deliberately NOT closed:"
echo "  #159  the encryption claims were withdrawn, but the database is still"
echo "        plaintext. There is no SQLCipher anywhere. Real work remains."
echo "  #165  fixed in the sprint 3 PR, not sprint 0. The earlier chmod +x ran"
echo "        in a sandbox and never reached the git index."
echo "  #178  the vulnerable ktor pin is gone and the build is proven green,"
echo "        but the toolchain skew half of the finding is unreviewed."
echo "  #203  ALPHA_DESTRUCTIVE_MIGRATION exists with a TODO, but nothing"
echo "        enforces its removal before v0.2-alpha."
