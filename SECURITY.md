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

- HOTP secret keys are encrypted with a key held in Android Keystore
- Sessions are held in memory only and are never written to disk
- Platform backup and device-to-device transfer are disabled, so the database
  cannot be extracted through Android's backup mechanism

Not implemented yet, each tracked by an issue:

| Control | Status |
|---|---|
| PIN hashing | Not implemented. The PIN is currently stored and compared in plaintext |
| PIN rate limiting and lockout | Not implemented |
| Database encryption at rest | Not implemented. There is no SQLCipher or equivalent in the build |
| AES-encrypted backups | Not implemented. The backup feature itself does not exist yet |
| RBAC enforcement | Not implemented. The permission model exists in code but no operation is gated on it |
| Manager approval and HOTP validation | Not wired up. The approval overlay does not yet validate a code |
| Audit log | Not implemented |
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
- Every HOTP code is single-use — consumed immediately on validation
- All authorization events are written to an audit log
- See [ADR-005](docs/adr/ADR-005-hotp-offline-authorization.md)

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
