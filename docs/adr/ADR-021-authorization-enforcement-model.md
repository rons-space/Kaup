# ADR-021: Authorization Enforcement Model

- **Date**: 2026-08-14
- **Status**: Accepted
- **Deciders**: Core maintainer

---

## Context

ADR-009 defines the roles and permissions, and ADR-005 defines the HOTP override
that lets a manager approve a restricted action offline. Both were written, and
neither was connected to anything.

The audit that produced finding H3 found the shape of the feature with none of
its substance. `ManagerApprovalOverlay` handed any non-empty string to its
`onApprove` callback, so the overlay approved whatever was typed into it.
`KaupAppShell` carried a comment reading `// Simulate entering PIN and successful
unlock`. `SessionManager.hasPermission` existed and no caller invoked it.
`HOTPGenerator.validateCode` existed and no caller invoked it either. A build in
that state does not have a weak permission model, it has none, and the UI states
that suggest otherwise are worse than an honest absence because they invite a
store owner to rely on them.

Alongside that, the validation routine itself had defects that only matter once
something calls it: it compared codes with `==`, it stopped at the first match,
its look-ahead window was 5 where ADR-005 says 10, and nothing limited how many
codes could be guessed.

This ADR records how enforcement is wired, and, as importantly, what it does not
protect against, because several of the natural assumptions about an offline
override are wrong.

## Decision

### Enforcement lives below the UI

`AuthorizationPolicy` in `:shared-kmp` is the single decision function. It is
pure: permission, session permissions, optional elevation token, current uptime
in, decision out. It returns one of three outcomes, and the middle one is the
reason it is not a boolean:

- `GrantedBySession` — the signed-in user holds the permission.
- `GrantedByElevation` — a live elevation token covers it.
- `RequiresManagerApproval` — the action is restricted. This is an instruction
  to raise the approval overlay, not a refusal.

Every restricted operation asks the enforcement point again immediately before
it commits. The Compose helpers in `:core-ui` call the same policy, but only to
decide what to grey out. A hidden button is a courtesy to the cashier; it is
never the thing protecting the till.

The enforcement point that actually runs the override, `OverrideAuthorizer`,
lives in `:core-data` rather than `:shared-kmp`, because it needs Room and the
Android Keystore. This follows the split already used for PIN entry, where
`PinPolicy` and `PinLockoutPolicy` are pure and shared and `PinAuthenticator`
does the Android-side work. The rule is that decisions are shared and testable,
and only the I/O around them is platform code.

### Validation is constant time and does not stop early

`HOTPGenerator.validateCode` compares codes digit by digit without branching on
the result, and continues through the whole look-ahead window even after a
match.

The constant-time comparison is the familiar requirement. The second property is
less obvious and matters as much: returning at the first match makes the call
faster when the manager's counter has not drifted and slower when it has, which
tells an attacker how far ahead the counter is. Drift is precisely the
information that narrows a guess, so every call now performs
`lookAheadWindow + 1` HMACs regardless of outcome. Eleven SHA-1 HMACs per attempt
is nothing next to a throttled attempt rate.

The window is 10, as ADR-005 always specified.

### The counter advances in the same transaction as the grant

`validateCode` returns the counter that matched and persists nothing. The caller
writes `counter = matched + 1` and the `OverrideLog` row in one Room
transaction, and only then allows the action to proceed. Advancing past the
matched counter, rather than incrementing by one, is what closes the replay
window that drift opens: if a code from ten ahead is accepted, the nine codes
behind it are dead too.

Generation has the mirror-image problem. Reading the counter from the cached
session user meant every code generated in a session was identical, because the
cached row was never refreshed after the update. The counter is now read and
advanced inside a single transaction that returns the value used, making the
database the only source of truth for it.

### Attempts are throttled against the manager, not the session

`OverrideThrottlePolicy` allows three attempts and then escalates from one
minute to a thirty minute ceiling, which is deliberately harsher than PIN entry.
A cashier mistypes their own PIN constantly and locking them out of the till has
a real cost; an override code is transcribed once from a manager standing
nearby, so repeated failure is much more likely to be an attack, and recovery is
just asking for another code.

The counters are persisted against the manager whose approval is being sought.
Holding them in memory would make closing the overlay a reset, and holding them
against the staff session would let an attacker cycle staff accounts.

This also means the overlay asks *which* manager is approving before it asks for
a code. The alternative, trying the entered code against every manager who has a
secret, makes per-manager throttling meaningless and makes the audit row
ambiguous when two managers' windows overlap.

### Override scope is enforced by the validating device, not by the code

ADR-005 offers the manager a choice between a code bound to one action and a
general elevation token. Implementing that honestly requires stating a
limitation the ADR did not: **the scope is not carried inside the code.**

An RFC 4226 HOTP code is an HMAC over an 8-byte counter and nothing else. There
is no field in it for a permission or a transaction ID. Any code a manager
generates will therefore validate against whatever action the staff device
happens to be asking about, and the staff device cannot tell which scope the
manager intended.

What the scope does control is what happens after a code validates: whether the
approval is spent immediately on the one named permission and transaction, or
retained as an elevation token, and which of those the audit row records. In the
flow ADR-005 describes the manager is told what they are approving before they
generate anything, and the audit row names what the code was actually spent on,
so a mismatch is detectable after the fact.

Binding the scope cryptographically would mean HMACing it alongside the counter.
That was rejected because it stops the codes being standard HOTP, which breaks
the `otpauth://` provisioning path and the printed backup codes of ADR-005
option F. It is the right change to make if and when scope confusion turns out
to be a real problem rather than a theoretical one.

Elevation tokens are held in memory only, for the length of one staff session.
They carry the granting manager's own permission set, so staff can borrow a
manager's authority but never exceed it, and `AuthorizationPolicy` checks the
session's own permissions first so a token is not burned on an action the
cashier could already perform. The admin switch that disables elevation tokens
is checked at redemption as well as at issue, so turning the feature off kills
tokens already in flight. The default window is five minutes and the
configurable ceiling is fifteen.

### `permissionsOverride` is removed rather than authenticated

Finding M15 observed that `SessionManager` trusted a `permissionsOverride`
column read from an unencrypted database, and suggested either a MAC over the
row keyed from the Keystore, or dropping the column until a server-signed grant
exists. The column is dropped.

The MAC was rejected because it would have been theatre. The same attacker who
can rewrite `permissionsOverride` in a plaintext database can rewrite `role` in
the row next to it, and authenticating one column while leaving the other
exposed buys nothing but the appearance of protection. Row integrity against an
attacker with the database file is what encryption at rest is for, and that is
tracked separately as #159.

Dropping the column is also the smaller change, because nothing ever wrote it.
No DAO method set it and no screen offered it; it was read at login and
otherwise inert. Permissions now derive from role alone. When sync exists, a
per-user grant returns as a server-signed object whose signature is checked
against a key the device did not mint, which is the only version of this feature
that is worth having.

### Every grant is audited

A validated override writes an `override_log` row inside the granting
transaction: the manager, the requesting staff user, the permission, the
transaction it was bound to, the scope, the counter consumed, the location and a
wall-clock timestamp. Rows carry `syncStatus` like every other entity and are
pushed when connectivity returns.

The table deliberately has no foreign key to `users`. An audit record has to
outlive the account it names, and a `CASCADE` would let deleting a manager erase
the evidence of what they approved.

Timestamps here are wall clock, unlike the lockout deadlines, which are uptime.
The two are not interchangeable: a lockout must not be shortened by moving the
device clock, and an audit entry must be comparable with records from other
devices.

## Alternatives Considered

| Alternative | Reason Rejected |
|---|---|
| Enforce permissions in the UI layer only | Hidden buttons are not access control; any ViewModel path that skips the check bypasses it entirely |
| Boolean `hasPermission` at every call site | Cannot express "restricted, offer the overlay", which is the whole point of the manager override |
| MAC the `permissionsOverride` column | Leaves the adjacent `role` column equally forgeable; encryption at rest (#159) is the real fix |
| Keep `permissionsOverride` unauthenticated | A row edit in a pulled database file promotes any account to owner |
| Bind scope into the HMAC | Stops the codes being RFC 4226 HOTP, breaking authenticator provisioning and printed backup codes |
| Persist elevation tokens in Room | Makes a standing privilege escalation survive process death, in a database that is not yet encrypted |
| Try an entered code against every manager | Defeats per-manager throttling and makes the audit trail ambiguous |
| Increment the counter by one after a match | Leaves every code between the old counter and the matched one replayable |
| Throttle in memory | Dismissing and reopening the overlay would reset the attempt count |

## Consequences

**Positive:**

- One decision function, shared and unit tested, used by both the enforcement
  point and the UI.
- Codes are single use, and accepting a drifted code invalidates everything
  behind it.
- Guessing is bounded by an escalating lockout that survives process death.
- Every approval leaves a row naming who approved what, for whom, and when.
- Removing `permissionsOverride` deletes a privilege escalation path outright
  instead of decorating it.

**Negative:**

- Validation always costs `lookAheadWindow + 1` HMACs, which is the price of not
  leaking drift through timing.
- The overlay needs the manager identified before the code is entered, which is
  one more tap than a bare code field.
- Scope is enforced by the validating device rather than by the code, so a
  manager who approves without being told what they are approving can be
  misled. The audit row makes this detectable, not preventable.
- Per-user permission grants are gone until sync can sign them.
- Elevation tokens do not survive process death, so a crash mid-window means
  asking the manager again.
