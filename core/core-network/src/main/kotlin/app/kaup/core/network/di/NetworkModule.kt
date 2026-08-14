package app.kaup.core.network.di

import app.kaup.core.network.notifications.LocalNotificationBackend
import app.kaup.shared.domain.notification.NotificationBackend
import app.kaup.shared.domain.sync.NoSyncBackend
import app.kaup.shared.domain.sync.SyncBackend
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Tier 0 bindings: no server, notifications stay on the device.
 *
 * `SyncBackend` and `NotificationBackend` are the interfaces defined in
 * `:shared-kmp` per ADR-004 and ADR-011. Selecting a different tier rebinds
 * `SyncBackend` here rather than changing any caller (#219).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    abstract fun bindNotificationBackend(
        localNotificationBackend: LocalNotificationBackend
    ): NotificationBackend

    companion object {
        /**
         * The built-in default from ADR-004. It lives in `:shared-kmp` because
         * it is pure no-op logic with a test that runs on every target, so it
         * is provided rather than bound.
         */
        @Provides
        @Singleton
        fun provideSyncBackend(): SyncBackend = NoSyncBackend()
    }
}
