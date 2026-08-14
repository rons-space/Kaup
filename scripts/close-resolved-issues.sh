#!/usr/bin/env bash
#
# Closes the code review findings that sprints 0 through 4 actually resolved.
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

echo "Sprint 3 part one — the money contract (PR #267)"
close 169 "Fixed in #267: ADR-019 states the money contract, and SalesCalculator now satisfies finalTotal == subtotal - discountTotal + exclusiveTaxTotal exactly. Cart-level amounts are allocated by largest remainder, so per-line tax bases sum to the cart tax base; 12 of 80 carts in the test matrix were previously taxed on a base one minor unit off. Multiple inclusive taxes are extracted once against their combined rate, and rounding is half away from zero so a refund is the exact negation of its sale."
close 201 "Fixed in #267: Money plus, minus, times and unaryMinus throw ArithmeticException instead of wrapping."
close 165 "Fixed in #267: gradlew is committed 100755. The sprint 0 chmod +x ran in a sandbox and never reached the git index, and CI masked it with its own chmod step."

echo "Sprint 3 part two — stock and conflicts (PR #268)"
close 170 "Fixed in #268: stock is a Quantity, a scaled integer of thousandths, and the stock_movements column is INTEGER quantityThousandths. ADR-020 records why a scaled integer replaces ADR-002's BigDecimal, which the Kotlin common standard library does not have."
close 171 "Fixed in #268: ADR-020 states the policy and ConflictResolver.resolve implements it. Total order by timestamp, deviceId then id; duplicates dropped by id with content divergence surfaced separately; timeline, final stock and violations returned from one replay. No last-write-wins anywhere, because the log is append-only and authoritative."
close 200 "Fixed in #268: detectNegativeStockViolations returns a StockViolation per movement that leaves stock negative, carrying the level after it and a flag on the first breach. It previously reported only the crossing event, hiding how deep an oversell went."

echo "Sprint 4 part one — the authorization domain (PR #272)"
close 186 "Fixed across #272 and #273: validateCode compares in constant time and runs the whole look-ahead window even after a match, because returning early leaked how far the manager's counter had drifted, and drift is what narrows a guess. The window is 10 per ADR-005, not 5. OverrideThrottlePolicy adds the RFC 4226 section 7.3 throttle: three attempts, then doubling from a minute to a half hour, persisted against the manager so it survives process death. The caller advances the counter to matched + 1 in the same transaction as the grant, which also kills every code behind an accepted drifted one."

echo "Sprint 4 part two — the override data layer (PR #273)"
close 176 "Fixed in #273: HotpCodeIssuer reads and advances the counter in one transaction, guarded by the value it read. The counter previously came from the SessionManager snapshot taken at login, which the update never refreshed, so every code generated in a session was the same code."
close 193 "Fixed in #273 by removing the column rather than authenticating it. A Keystore MAC over permissionsOverride would have been theatre while the role column beside it stayed equally forgeable in a plaintext database, and nothing ever wrote the column. Permissions derive from role; per-user grants return when sync can sign them. Row integrity against someone holding the database file is #159. Reasoning in ADR-021."
close 195 "Fixed in #273: StrongBox is used where the hardware provides it, with the fallback driven by catching StrongBoxUnavailableException rather than a feature flag that can disagree with reality, and KeyPermanentlyInvalidatedException now surfaces as HotpSecretUnrecoverableException so the UI can say the secret must be provisioned again instead of inviting a retry that can never work. setUserAuthenticationRequired and setUnlockedDeviceRequired are deliberately NOT set, and the class comment records why: a POS terminal is often a shared device with no secure lock screen, where the first makes the key unusable outright, and an unattended order display would be broken by the second. ADR-005 option E covers biometric-gated generation as an opt-in setting."

echo "Sprint 4 part three — screen security and resources (PR #274)"
close 194 "Fixed in #274: FLAG_SECURE via SecureScreen() on the provisioning, override code and lock screens; the Base32 secret is hidden behind an explicit reveal instead of being displayed throughout scanning; and the raw bytes are zeroed in a finally block and again in onCleared. The Base32 String cannot be zeroed, which is stated on the ViewModel rather than papered over, and it is dropped from the UI state on completion."
close 196 "Fixed in #274: a real vector launcher icon, adaptive on API 26+ with a layer-list for 24 and 25, replaces @android:drawable/sym_def_app_icon; Theme.Kaup with a values-night counterpart replaces @android:style/Theme.NoTitleBar; android:label moves to @string/app_name; and FLAG_SECURE is on the lock screen. Migrating the remaining hardcoded screen copy is #189."

echo "Sprint 4 part four — end to end wiring (PR #275)"
close 166 "Fixed in #275: the overlay is presentational and cannot approve anything, :core-ui has no access to :core-data, and a ViewModel in :feature-auth submits to OverrideAuthorizer. HOTP provisioning is gated on USERS_EDIT end to end: permission check, overlay on denial, constant-time validation, per-manager throttling, transactional counter consumption and audit write. The grant is a capability, not a boolean: saveAndComplete re-reads the override_log row and checks its permission, requester and age before writing. Provisioning rather than a POS void because the POS screens are still placeholders and gating a fake one would be the shape-without-substance this finding is about."
close 219 "Fixed in #272 and #275: OverrideScope models both ADR-005 scopes, elevation tokens are in-memory and single use and carry the granting manager's own permission set, the window defaults to five minutes with a fifteen minute ceiling, the UI warns before issuing one, and Settings has the admin switch that disables them. Turning it off cancels tokens already in flight, because the switch is checked at redemption as well as at issue. The limitation is documented in ADR-021: scope is not carried inside the code, because an RFC 4226 HOTP is an HMAC over a counter with no field for a permission, so it is enforced by the validating device and recorded in the audit row."
close 220 "Fixed in #273: a validated code advances the counter and writes an override_log row naming the approver, the requester, the permission, the transaction, the scope and the counter consumed, all in one transaction. Rows carry syncStatus like every other entity. The table has no foreign key to users on purpose, because an audit record must outlive the account it names."

echo
echo "Deliberately NOT closed:"
echo "  #159  the encryption claims were withdrawn, but the database is still"
echo "        plaintext. There is no SQLCipher anywhere. Real work remains."
echo "  #178  the vulnerable ktor pin is gone and the build is proven green,"
echo "        but the toolchain skew half of the finding is unreviewed."
echo "  #203  ALPHA_DESTRUCTIVE_MIGRATION exists with a TODO, but nothing"
echo "        enforces its removal before v0.2-alpha. Schema 7 has now landed"
echo "        inside the destructive window, so this is still the binding"
echo "        constraint on the next schema change."
echo
echo "Still open and worth knowing:"
echo "  #269  moves LineItem.quantity to Quantity, the last Double in the money"
echo "        path. Not a schema change, so it is not gated on the migration"
echo "        window."
echo "  #174  :core-data and :feature-auth still have no test harness, so the"
echo "        transactional glue added in sprint 4, OverrideAuthorizer and"
echo "        HotpCodeIssuer, has no automated coverage. The pure policy under"
echo "        it does. This is the largest known gap in sprint 4."
