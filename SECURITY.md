# Security Policy

---

## Supported Versions

| Version | Supported |
|---|---|
| Latest alpha / beta | ✅ Active |
| v1.0 and later stable releases | ✅ Active |
| Older stable releases | ❌ Not supported — please update |

---

## Reporting a Vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

If you discover a security vulnerability, please report it privately:

1. Go to the **Security** tab of this repository on GitHub
2. Click **"Report a vulnerability"**
3. Fill in the details — include steps to reproduce, affected versions,
   and the potential impact

You will receive an acknowledgment within **72 hours**. We aim to provide
a fix or mitigation within **14 days** of a confirmed report, depending
on severity.

We will credit you in the release notes for the fix unless you prefer to
remain anonymous.

---

## Implementation Status

> **Read this before relying on anything below.** Kaup is at `0.1-alpha` and
> most of the security architecture is designed but not yet built. This section
> previously described the target state as though it were shipped. It does not
> any more.
>
> **Do not run an alpha build in a real store.**

Implemented today:

- PINs are stored as PBKDF2 hashes with a per-user random salt, and the
  iteration count is recorded per credential so the cost can be raised later
  without invalidating existing PINs
- PIN entry is rate limited by an escalating lockout that is persisted, so
  killing the app is not a way to reset the attempt counter
- The database is encrypted at rest with SQLCipher. The passphrase is random
  per device, never leaves it, and is sealed by an Android Keystore key under
  its own alias, separate from the one protecting HOTP secrets (ADR-022)
- HOTP secret keys are encrypted with a key held in Android Keystore, in
  StrongBox where the hardware provides it
- Sessions are held in memory only and are never written to disk
- Platform backup and device-to-device transfer are disabled, so the database
  cannot be extracted through Android's backup mechanism
- The lock, HOTP provisioning and override code screens set `FLAG_SECURE`, so
  they cannot be screenshotted or screen-recorded and do not appear in the
  recent apps preview
- Manager override is wired end to end: codes are validated in constant time
  against the approving manager's secret, attempts are throttled per manager
  and survive process death, the counter is consumed in the same transaction
  as the audit row, and the granted action re-checks that audit row before it
  runs
- Every granted override is written to an append-only `override_log` table
  recording the approver, the requester, the permission, the scope and the
  counter consumed

Not implemented yet, each tracked by an issue:

| Control | Status |
|---|---|
| AES-encrypted backups | Not implemented. The backup feature itself does not exist yet |
| RBAC enforcement | Partial. The mechanism works and gates HOTP provisioning; the POS operations that most need it (void, refund, price override) are still placeholder screens |
| Biometric enrollment | Not implemented, planned for v0.2-alpha |
| Ktor server, HTTPS, JWT | The server does not exist yet |

---

## Security Architecture (design target)

This is the design the project is building toward. Consult the table above for
what is real today, and the ADRs for the reasoning.

### Authentication and Session Management

- All staff sessions are PIN-protected, 6 digits for newly created PINs
- Optional biometric enrollment uses Android BiometricPrompt — the private
  key never leaves Android Keystore
- Sessions are held in memory only — never persisted to disk
- Auto-lock timeout clears the session on idle — configurable by admin
- See [ADR-009](docs/adr/ADR-009-rbac-permission-system.md)

### Manager Authorization (HOTP)

- Restricted actions require a cryptographically signed HOTP code (RFC 4226)
- HOTP secret keys are stored in Android Keystore — the app cannot extract them
- Every HOTP code is single-use — consumed immediately on validation, and
  accepting a drifted code invalidates every code behind it
- All authorization events are written to an audit log
- A code's scope is enforced by the validating device and recorded in the audit
  row; it is not carried inside the code, which is a documented limitation of
  using standard HOTP rather than an oversight
- See [ADR-005](docs/adr/ADR-005-hotp-offline-authorization.md) and
  [ADR-021](docs/adr/ADR-021-authorization-enforcement-model.md)

### Screen Capture

- The lock screen, the HOTP provisioning screen and the override code screen
  set `FLAG_SECURE`
- This covers screenshots, screen recording, casting, and the thumbnail Android
  keeps for the recent apps list
- It is a window flag applied while those screens are composed rather than a
  manifest-wide setting, because the rest of the app has legitimate reasons to
  be captured, receipts and reports among them

### Data at Rest

- Full database backups are AES-encrypted before writing to storage
- HOTP secret keys and API tokens are stored in Android Keystore
- Media files (receipt photos, refund evidence) are stored in app-private
  storage inaccessible to other apps without root

### Data in Transit

- Ktor server enforces HTTPS — plaintext HTTP connections are rejected
- JWT tokens are used for device-to-server authentication
- All API inputs are sanitized and validated server-side

### Permission Model

- All RBAC permission checks are enforced locally on the Android client
- Enforcement lives below the UI, in the use case that performs the operation.
  The Compose helpers apply the same shared rule, but only to decide what to
  draw
- Restricted UI elements are hidden entirely — not merely disabled
- The Ktor server performs JWT validation; server-side RBAC enforcement
  is a post-v1 hardening milestone
- See [ADR-009](docs/adr/ADR-009-rbac-permission-system.md)

### Third-Party Dependencies

- All dependencies are MIT, Apache 2.0, LGPL, or GPL licensed
- No proprietary trackers, analytics SDKs, or crash reporting services
  are included without explicit opt-in by the user
- FCM is an optional enhancement in the Play Store build only — it is
  never a hard dependency
- See [ADR-013](docs/adr/ADR-013-fdroid-izzydroid-distribution.md)

---

## Known Limitations

The following are known design trade-offs, not vulnerabilities:

- **Client-side RBAC only (v1)** — the Ktor server does not independently
  enforce permissions in v1. A modified Android client could theoretically
  bypass local permission checks. Server-side enforcement is planned
  post-v1. Operators with high security requirements should treat the
  Android app as a trusted client and restrict physical device access.

- **BLE Proximity Approval (Option D, Could Have)** — Bluetooth-based
  proximity authorization is known to be susceptible to proximity-spoofing
  attacks. This method is labeled with a security caveat in the UI and is
  not recommended as a primary authorization method. See
  [ADR-005](docs/adr/ADR-005-hotp-offline-authorization.md).

- **HOTP look-ahead window** — The HOTP validator accepts the next 10 valid
  codes to handle counter desynchronization. This is the RFC 4226
  recommended mitigation and represents a negligible brute-force risk in
  a physical retail environment once rate limiting and audit logging are in
  place. Neither is implemented yet.

- **General elevation tokens** — If enabled by the admin, a manager can
  generate a time-limited general elevation token rather than an
  action-specific code. This is a deliberate user-configurable trade-off
  between security and operational speed. Admins can disable this option
  entirely in Settings.
