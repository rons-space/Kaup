package app.kaup.android.di

import android.content.Context
import app.kaup.android.BuildConfig
import app.kaup.core.data.crypto.DatabaseKeySealer
import app.kaup.core.data.crypto.SealedSecretUnrecoverableException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the SQLCipher passphrase (ADR-022, #159).
 *
 * The passphrase is random, generated once on this device, and never leaves it.
 * It is stored sealed by an Android Keystore key, so the bytes on disk are
 * useless without the hardware-held key, and the key cannot be exported.
 *
 * It is a hex string rather than raw random bytes. SQLCipher takes the
 * passphrase as a byte array and derives the key from it, and a raw random
 * array can contain a zero byte, which risks being treated as a terminator
 * somewhere in the native path. Hex costs nothing here and removes the
 * question.
 */
@Singleton
class DatabasePassphrase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sealer: DatabaseKeySealer
) {

    /**
     * Returns the passphrase, generating and persisting one on first run.
     *
     * The array is handed straight to SQLCipher and **must not be zeroed by the
     * caller**. Room opens the database lazily and the factory holds this array
     * by reference, so wiping it after construction would leave SQLCipher
     * deriving the key from a run of zeroes. This is the one place in the
     * codebase where the zero-the-plaintext rule followed everywhere else is
     * wrong, which is exactly why it is written down here.
     */
    fun getOrCreate(): ByteArray {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val sealed = preferences.getString(KEY_SEALED_PASSPHRASE, null)

        if (sealed != null) {
            try {
                return sealer.decrypt(sealed)
            } catch (e: SealedSecretUnrecoverableException) {
                // The Keystore key is gone, most often because the device's
                // secure lock screen was removed or the app's keys were reset.
                // The database can never be opened again: the passphrase was
                // the only thing that could unlock it and nothing else has a
                // copy. There is no recovery, only a choice about how to fail.
                if (!AlphaMigrationWindow.permits(BuildConfig.VERSION_NAME)) {
                    // Past ADR-018 Phase 1 there may be real, unsynced sales in
                    // there. Silently recreating the database would destroy
                    // them without anyone deciding to. Fail loudly instead and
                    // let a human choose.
                    throw e
                }
                // Inside the alpha window the same bargain as the destructive
                // migration applies: alpha data is disposable and a terminal
                // that cannot start is worse than one that starts empty.
                context.deleteDatabase(DATABASE_NAME)
                preferences.edit().remove(KEY_SEALED_PASSPHRASE).apply()
            }
        }

        val generated = newPassphrase()
        // Sealed before it is used, and committed synchronously. If the process
        // died between opening an encrypted database and persisting the key
        // that opens it, the data would be unreadable on next launch.
        preferences.edit()
            .putString(KEY_SEALED_PASSPHRASE, sealer.encrypt(generated))
            .commit()
        return generated
    }

    private fun newPassphrase(): ByteArray {
        val random = ByteArray(PASSPHRASE_BYTES)
        SecureRandom().nextBytes(random)
        return random.joinToString("") { byte -> "%02x".format(byte) }.toByteArray(Charsets.UTF_8)
    }

    private companion object {
        const val PREFERENCES_NAME = "kaup_database_key"
        const val KEY_SEALED_PASSPHRASE = "sealed_passphrase"

        /** 256 bits of entropy, hex encoded into 64 ASCII characters. */
        const val PASSPHRASE_BYTES = 32
    }
}

/** Shared with DatabaseModule so the name cannot drift between the two. */
internal const val DATABASE_NAME = "kaup_database"
