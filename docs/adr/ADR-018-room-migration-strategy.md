# ADR-018: Room Database Migration Strategy

- **Date**: 2026-03-22
- **Status**: Accepted
- **Deciders**: Core maintainer

---

## Context

Room requires an explicit migration for every schema version change. Without a
migration, Room throws `IllegalStateException` on app upgrade, crashing existing
installations. During alpha development, the schema will change frequently as
features are added, columns are discovered to be missing, and ADR decisions
(notably ADR-016 multi-location) are implemented. Writing a formal migration for
every column added during rapid prototyping would create significant overhead and
slow iteration. At the same time, from the first public release onward, any schema
change that crashes an existing user's installation is unacceptable.

## Decision

A two-phase strategy is adopted, with a hard boundary at `v0.2-alpha`
(first public release):

### Phase 1 — Alpha (`v0.1-alpha` through pre-`v0.2-alpha`)

Room is configured with `fallbackToDestructiveMigration()`:

```kotlin
Room.databaseBuilder(context, KaupDatabase::class.java, "kaup_database")
    .fallbackToDestructiveMigration()
    .build()
```

If the schema version increments and no migration is provided, Room drops and
recreates all tables. All data is lost on upgrade.

This is acceptable during alpha because:
- No real store data exists — alpha users are developers and testers
- The release notes for every alpha explicitly state: *"Alpha releases may
  wipe local data on upgrade. Do not use in a production store."*
- A backup created before upgrading restores all data after the wipe

The schema version is incremented on every PR that changes any entity class.
Schema JSON exports are committed to `core/core-data/schemas/` on every version increment
to maintain a version history even during alpha.

### Phase 2 — Beta and Stable (`v0.2-alpha` onward)

`fallbackToDestructiveMigration()` is removed. Every schema change requires a
formal Room migration before the PR can be merged:

```kotlin
val MIGRATION_X_Y = object : Migration(X, Y) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // explicit ALTER TABLE or CREATE TABLE statements
    }
}

Room.databaseBuilder(context, KaupDatabase::class.java, "kaup_database")
    .addMigrations(MIGRATION_X_Y)
    .build()
```

**Migration rules (enforced in code review from v0.2-alpha):**
- Every PR that modifies an `@Entity` class must increment `DATABASE_VERSION`
  in `KaupDatabase` and include the corresponding `Migration` object
- Migrations are additive only — columns are never removed in a migration,
  only added (with a non-null default or nullable). Removal is a separate
  cleanup migration scheduled for the next major version
- All migrations are tested via `MigrationTestHelper` in the `:core-data`
  test suite — a migration PR without a passing test will not be merged

**Schema export (both phases):**
Room schema JSON export is enabled in `build.gradle.kts` throughout both phases:

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

Every schema version JSON is committed to `core/core-data/schemas/`. This gives a complete
version history and is required for `MigrationTestHelper` to function.

**The v0.2-alpha baseline migration:**
The transition from Phase 1 to Phase 2 at `v0.2-alpha` requires one final
migration that establishes the stable baseline schema. This migration is written
by hand, reviewed carefully against the exported schema JSON, and tested against
a database created at the last alpha version before the cutover. This is the most
critical migration in the project's lifecycle.

## Alternatives Considered

| Alternative | Reason Rejected |
|---|---|
| Proper migrations from day one | Significant overhead during rapid alpha iteration |
| Destructive migration forever | Unacceptable data loss for real store users from v0.2-alpha onward |
| Manual backup before every alpha upgrade | Impractical — requires user discipline; alpha users often forget |

## Consequences

**Positive:**
- Rapid schema iteration during alpha with zero migration overhead
- Clear, documented boundary at v0.2-alpha — no ambiguity about when
  migrations become mandatory
- Schema JSON history is maintained throughout both phases — full audit trail
- `MigrationTestHelper` tests give confidence that upgrades work correctly
  for every version pair from v0.2-alpha onward

**Negative:**
- Alpha users who do not read release notes may lose data on upgrade —
  the warning must be prominent in every alpha release note
- The v0.2-alpha baseline migration is high-risk — requires careful testing
  and must be treated as a release blocker if it fails
- Contributors adding entities during alpha must still increment
  `DATABASE_VERSION` even though no migration is written — this discipline
  must be established early or the schema version history becomes unusable