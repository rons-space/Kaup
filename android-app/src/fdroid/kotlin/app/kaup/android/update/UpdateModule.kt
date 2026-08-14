package app.kaup.android.update

import app.kaup.shared.domain.update.NoOpUpdateChecker
import app.kaup.shared.domain.update.UpdateChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * `fdroid` flavor: the store owns updates, so the app never checks and
 * never offers to install anything itself (ADR-014).
 */
@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    @Singleton
    fun provideUpdateChecker(): UpdateChecker = NoOpUpdateChecker()
}
