package app.kaup.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
