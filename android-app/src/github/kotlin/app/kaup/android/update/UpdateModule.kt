package app.kaup.android.update

import app.kaup.shared.domain.update.UpdateChecker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** `github` flavor: self-updates from GitHub Releases (ADR-014). */
@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {

    @Binds
    @Singleton
    abstract fun bindUpdateChecker(checker: GitHubUpdateChecker): UpdateChecker
}
