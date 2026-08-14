package app.kaup.android.di

import app.kaup.android.BuildConfig
import app.kaup.core.data.KaupDatabase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The release blocker ADR-018 asks for, and #203 found missing.
 *
 * The important test here is [destructiveMigrationDoesNotOutliveItsWindow]. It
 * passes today and is meant to: it exists to fail on the day someone bumps
 * `versionName` past `0.1-alpha` without doing the Phase 2 work. The others
 * cover the predicate itself, so this file is not resting entirely on a check
 * that cannot fire yet.
 */
class AlphaMigrationWindowTest {

    @Test
    fun `the window admits exactly the version it was approved for`() {
        assertTrue(AlphaMigrationWindow.permits("0.1-alpha"))
    }

    @Test
    fun `the window excludes every later version`() {
        // 0.2-alpha is the ADR-018 boundary itself. 0.1.1-alpha is the case a
        // startsWith or a "contains alpha" check would have let through, which
        // is why permits() is an exact match.
        listOf("0.2-alpha", "0.1.1-alpha", "0.1-beta", "0.10-alpha", "1.0", "")
            .forEach { version ->
                assertFalse(
                    "versionName $version is outside ADR-018 Phase 1 and must not " +
                        "permit the destructive fallback",
                    AlphaMigrationWindow.permits(version)
                )
            }
    }

    @Test
    fun destructiveMigrationDoesNotOutliveItsWindow() {
        if (AlphaMigrationWindow.permits(BuildConfig.VERSION_NAME)) return

        assertFalse(
            """
            versionName is now ${BuildConfig.VERSION_NAME}, which is past the
            ADR-018 Phase 1 boundary, but KaupDatabase.ALPHA_DESTRUCTIVE_MIGRATION
            is still true.

            This is the v0.2-alpha cutover (#203), and it is a release blocker,
            not a follow-up. Before this build can ship:

              1. set ALPHA_DESTRUCTIVE_MIGRATION to false
              2. write the baseline Migration for the current DATABASE_VERSION
              3. add the MigrationTestHelper suite in :core-data

            Until then every upgrade of an installed app destroys the store's
            data on first launch.
            """.trimIndent(),
            KaupDatabase.ALPHA_DESTRUCTIVE_MIGRATION
        )
    }
}
