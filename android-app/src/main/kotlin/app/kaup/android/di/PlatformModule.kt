package app.kaup.android.di

import app.kaup.core.data.crypto.KeystoreManager
import app.kaup.core.data.crypto.SecretSealer
import app.kaup.core.data.time.SystemTimeProvider
import app.kaup.core.data.time.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the two platform seams introduced for #174 to their real
 * implementations.
 *
 * Both interfaces live in `:core-data` next to the code that depends on them,
 * but the binding has to be here: `:core-data` deliberately does not apply the
 * Hilt plugin, it declares `javax.inject` only and lets the application module
 * assemble the graph.
 *
 * These are the only two things in the auth path that a unit test cannot
 * construct for itself. `AndroidKeyStore` has no software implementation, and a
 * clock that cannot be moved is a clock that cannot be tested against a
 * lockout. Everything else in `OverrideAuthorizer` and `HotpCodeIssuer` is a
 * DAO, which is an interface, or the database, which Room can build in memory.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {

    @Binds
    @Singleton
    abstract fun bindSecretSealer(impl: KeystoreManager): SecretSealer

    @Binds
    @Singleton
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider
}
