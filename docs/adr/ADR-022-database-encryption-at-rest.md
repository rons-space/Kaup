# ADR-022: Database encryption at rest

## Status

Accepted

## Context

The database was plaintext SQLite. `CONTEXT.md`, `README.md` and `SECURITY.md`
all described it as encrypted, which is finding C2 (#159) and was rated
Critical. Sprint 0 withdrew the claims; this ADR is the other half, the control
itself.

Anyone who can read the application's data directory can read every sale, every
staff record and every PIN hash. On a rooted terminal, a device pulled from a
till, or a stolen tablet, that is the whole store's history in a file. The audit
trail added in ADR-021 is also only as trustworthy as the file it lives in: an
append-only DAO stops the *application* editing history, but not someone with
the file. ADR-021 says as much and points here.

There is a second, more time-sensitive reason to do this now. ADR-018 Phase 1 is
still in force, so the database is recreated on a schema change and there is
nothing to preserve. Adopting encryption inside that window costs one recreated
database. After `v0.2-alpha` it would need a `sqlcipher_export()` migration over
a live store's real data, which is the riskiest kind of migration there is. The
cheap moment is now and it does not come back.

## Decision

Encrypt the whole database with SQLCipher, keyed by a per-device random
passphrase sealed by an Android Keystore key.

### The library

`net.zetetic:sqlcipher-android` (4.17.0), not the `android-database-sqlcipher`
artifact named in the original finding. That one is deprecated, no longer
updated, and lacks the 16 KB page size support Play now requires.

It does not need flavor isolation. SQLCipher Community is BSD-style licensed,
which satisfies the "MIT, Apache 2.0, LGPL, or GPL-compatible" rule in
`CONTEXT.md`, and F-Droid's inclusion policy explicitly permits prebuilt FLOSS
binaries sourced from trusted Maven repositories, Maven Central among them. This
is unlike `kmp-app-updater`, which is confined to the `github` flavor for policy
reasons rather than licensing ones.

### The passphrase

Generated once per device: 32 bytes from `SecureRandom`, hex encoded to 64 ASCII
characters. Hex rather than raw bytes because SQLCipher takes the passphrase as
a byte array and a raw random array can contain a zero byte, which risks being
treated as a terminator in the native layer. The encoding costs nothing and
removes the question.

Sealed with `AndroidKeystoreSealer` under its **own key alias**, separate from
the one protecting HOTP secrets. The two have different blast radii: losing the
HOTP key costs every manager a re-provisioning, which is a QR code away; losing
the database key costs the database. They should not be able to take each other
down, and re-provisioning one must not touch the other.

The sealed blob lives in a private `SharedPreferences` file and is committed
synchronously, before the database is opened. If the process died between
opening an encrypted database and persisting the key that opens it, the data
would be unreadable on the next launch. A failed commit aborts startup for the
same reason: a key that was handed to SQLCipher but never stored produces a
database that only the running process can read.

### The database that is already there

Encryption did not change the filename, so an install that predates it already
has a plaintext file at the path the encrypted database now wants. Nothing about
that file announces itself: SQLCipher encrypts page one along with everything
else, so opening it with a passphrase fails inside the native layer with "file
is not a database", and Room's destructive fallback cannot rescue it because
reading `user_version` means opening the database first. Left alone this is a
crash on every launch of an upgraded device, and the plaintext the encryption
was adopted to remove stays on disk.

`DatabaseModule` therefore checks for the 16-byte SQLite magic before the first
encrypted open and deletes the file when it finds it, sidecars included. The
check reads the header rather than a "we have encrypted this device" preference,
because a preference records what the app believes and the header records what
is on disk, and those disagree in exactly the interesting cases: a restored
backup, a downgrade, cleared app data.

Deletion is only correct inside the Phase 1 window, and that is the same bargain
made everywhere else in this ADR. Past `v0.2-alpha` finding a plaintext database
means a release shipped without the `sqlcipher_export()` migration that should
have converted it, so the code throws and says so instead of destroying sales
nobody agreed to lose.

### The zeroing exception

Every other secret in this codebase is zeroed in a `finally` block. The
SQLCipher passphrase is **not**, and must not be. Room opens the database
lazily, and `SupportOpenHelperFactory` holds the array by reference until it
does. Zeroing it after construction means SQLCipher derives the key from a run
of zeroes, and the database is written under the wrong key or fails to open.

This is a genuine exception to a rule the codebase otherwise follows without
deviation, so it is recorded in three places: here, on
`DatabasePassphrase.getOrCreate`, and at the call site in `DatabaseModule`.

### When the key is lost

If the Keystore key is invalidated, typically because the device's secure lock
screen was removed, the passphrase cannot be unsealed and the database can never
be opened again. Nothing else holds a copy; that is the property that makes the
encryption worth having. The only decision available is how to fail:

- **Inside the ADR-018 Phase 1 window**, delete the database and start fresh.
  This is the same bargain the destructive migration already makes: alpha data
  is disposable, and a till that will not start is worse than one that starts
  empty.
- **After `v0.2-alpha`**, rethrow. There may be real, unsynced sales in that
  file, and destroying them silently is not a decision code should take on a
  human's behalf.

## Consequences

Positive:

- The database file is useless without the device's hardware-held key.
- The claims in the documentation become true.
- ADR-021's audit trail gains the file-level integrity it explicitly lacked, and
  the `permissionsOverride` reasoning recorded there ("encryption at rest is the
  real fix") is now backed by something.
- Adopted inside the destructive window, so no data migration is needed. An
  existing plaintext database is discarded rather than converted, which is only
  acceptable because Phase 1 already treats that data as disposable.

Negative:

- Native libraries for four ABIs increase APK size.
- `System.loadLibrary("sqlcipher")` must run before any database access.
- Queries carry the cost of page-level encryption. For a single-till workload
  this is not expected to be measurable, but it has not been benchmarked.
- Losing the Keystore key is unrecoverable by design, which after v0.2-alpha
  means a support path that ends in data loss. A synced backend eventually
  softens this; an offline-only store has no second copy.

Neutral:

- No schema change, so no `DATABASE_VERSION` bump and no exported schema.
- The unit tests build their Room databases in memory without the SQLCipher
  factory, so the test suite is unaffected. The consequence is that the
  encryption path is exercised only on a real device.
