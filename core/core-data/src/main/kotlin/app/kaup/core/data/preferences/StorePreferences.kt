package app.kaup.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import app.kaup.shared.domain.auth.ElevationTokenPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

class StorePreferences(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val STORE_NAME = stringPreferencesKey("store_name")
        val CURRENCY = stringPreferencesKey("currency")
        val AUTO_LOCK_TIMEOUT_MS = longPreferencesKey("auto_lock_timeout_ms")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val ELEVATION_TOKENS_ENABLED = booleanPreferencesKey("elevation_tokens_enabled")
        val ELEVATION_WINDOW_MS = longPreferencesKey("elevation_window_ms")
    }

    val storeName: Flow<String?> = dataStore.data.map { preferences ->
        preferences[Keys.STORE_NAME]
    }

    val currency: Flow<String?> = dataStore.data.map { preferences ->
        preferences[Keys.CURRENCY]
    }

    suspend fun saveStoreSetup(name: String, currency: String) {
        dataStore.edit { preferences ->
            preferences[Keys.STORE_NAME] = name
            preferences[Keys.CURRENCY] = currency
        }
    }

    val autoLockTimeoutMs: Flow<Long> = dataStore.data.map { preferences ->
        preferences[Keys.AUTO_LOCK_TIMEOUT_MS] ?: 300_000L // 5 minutes default
    }

    suspend fun setAutoLockTimeout(ms: Long) {
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_LOCK_TIMEOUT_MS] = ms
        }
    }

    /**
     * Whether a manager may issue a general elevation token rather than an
     * approval bound to one action, required as an admin switch by SECURITY.md
     * and ADR-005.
     *
     * Defaults to on because ADR-005 offers both scopes, and the UI warns
     * before issuing one. A store that decides the convenience is not worth it
     * turns this off, and ADR-021 has the switch checked at redemption as well
     * as at issue, so tokens already in flight stop working immediately rather
     * than lingering for their window.
     */
    val elevationTokensEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.ELEVATION_TOKENS_ENABLED] ?: true
    }

    suspend fun setElevationTokensEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.ELEVATION_TOKENS_ENABLED] = enabled
        }
    }

    /**
     * How long an elevation token stays usable, clamped to the ceiling
     * [ElevationTokenPolicy] enforces so a stored value from an older build, or
     * an edited preferences file, cannot widen the window past it.
     */
    val elevationWindowMs: Flow<Long> = dataStore.data.map { preferences ->
        val configured = preferences[Keys.ELEVATION_WINDOW_MS]
            ?: ElevationTokenPolicy.DEFAULT_WINDOW_MILLIS
        configured.coerceIn(1L, ElevationTokenPolicy.MAX_WINDOW_MILLIS)
    }

    suspend fun setElevationWindowMs(ms: Long) {
        dataStore.edit { preferences ->
            preferences[Keys.ELEVATION_WINDOW_MS] =
                ms.coerceIn(1L, ElevationTokenPolicy.MAX_WINDOW_MILLIS)
        }
    }

    /**
     * A stable identifier for this installation, generated on first use.
     *
     * Every stock movement carries it (ADR-002), and `ConflictResolver` uses it
     * as the tie-break when two devices write movements with the same
     * timestamp, so it has to be stable for the life of the install and
     * distinct between devices. It is deliberately not derived from any
     * hardware identifier: those need permissions, are not stable across
     * factory resets, and would tie a store's data to a serial number.
     *
     * It lives in DataStore rather than the database because it must survive
     * the destructive database recreations of the alpha phase.
     */
    suspend fun deviceId(): String {
        val existing = dataStore.data.map { it[Keys.DEVICE_ID] }.first()
        if (existing != null) return existing
        val generated = UUID.randomUUID().toString()
        // Another caller may have written first; keep whatever landed.
        return dataStore.edit { preferences ->
            preferences[Keys.DEVICE_ID] = preferences[Keys.DEVICE_ID] ?: generated
        }[Keys.DEVICE_ID] ?: generated
    }
}
