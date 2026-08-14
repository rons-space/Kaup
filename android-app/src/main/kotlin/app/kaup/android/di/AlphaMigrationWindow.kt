package app.kaup.android.di

/**
 * The version window in which ADR-018 Phase 1 permits the destructive fallback.
 *
 * ADR-018 puts a hard boundary at `v0.2-alpha`, the first public release: up to
 * that point the database is recreated on a schema change instead of migrated,
 * and every alpha release note carries the data loss warning. After it, every
 * schema change needs a real `Migration` and a `MigrationTestHelper` test.
 *
 * #203 is about the fact that nothing enforced the boundary. The cutover lived
 * in a hand-maintained `const val` and a TODO comment, so shipping `0.2-alpha`
 * with the flag still set would have silently wiped a real store's sales
 * history on first upgrade, exactly once, with no warning and no way back.
 *
 * Note this is deliberately *not* gated on `BuildConfig.DEBUG`, which is what
 * the finding originally suggested. Alpha releases are supposed to recreate the
 * database; that is the documented Phase 1 bargain, and debug-gating it would
 * mean a released alpha crashed on a schema change instead of doing the thing
 * ADR-018 says it should do. The boundary that matters is the version, not the
 * build type.
 *
 * Failing closed is the point. If the constant is ever left set after the
 * window closes, Room raises a missing-migration error instead of destroying
 * data, and `AlphaMigrationWindowTest` fails the build long before that, which
 * is the release blocker ADR-018 asks for.
 */
internal object AlphaMigrationWindow {

    /** The only `versionName` the destructive fallback is approved for. */
    const val PHASE_ONE_VERSION_NAME: String = "0.1-alpha"

    /**
     * Whether [versionName] is still inside ADR-018 Phase 1.
     *
     * An exact match rather than a `startsWith("0.1")` or a "contains alpha"
     * test: `0.1.1-alpha` and `0.2-alpha` are both outside the window, and a
     * prefix check would quietly let the first of those through.
     */
    fun permits(versionName: String): Boolean = versionName == PHASE_ONE_VERSION_NAME
}
