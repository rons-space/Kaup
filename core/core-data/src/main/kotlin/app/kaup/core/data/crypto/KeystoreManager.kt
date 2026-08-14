package app.kaup.core.data.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A secret sealed by this key cannot be recovered.
 *
 * Raised when the Keystore entry is gone, has been invalidated, or the stored
 * blob is not a well-formed sealed value. Never a transient condition: retrying
 * cannot help, and the caller has to treat the plaintext as lost.
 */
class SealedSecretUnrecoverableException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Seals values with an AES-256-GCM key held in the Android Keystore.
 *
 * One instance owns one key, named by [alias]. Separate aliases per purpose is
 * deliberate: the HOTP secrets and the database passphrase have different
 * lifetimes and different consequences when they are lost, and sharing one key
 * would mean re-provisioning a manager's authenticator could not be done
 * without touching the key that opens the database.
 *
 * Two hardening decisions are deliberate omissions, recorded here because their
 * absence looks like an oversight:
 *
 * `setUserAuthenticationRequired(true)` is **not** set. It would require a
 * device credential or biometric for every code generation. A POS terminal is
 * frequently a shared device with no secure lock screen configured, where that
 * setting makes the key unusable outright, and on a device that does have one
 * it puts a keyguard prompt in front of a manager standing at a till with a
 * queue. ADR-005 option E covers biometric-gated generation as an opt-in
 * setting, which is the right shape for it: a per-store choice, not a default
 * that can brick authorization offline.
 *
 * `setUnlockedDeviceRequired(true)` is not set for the same reason, plus one
 * more: a till running an unattended order display is a normal deployment, and
 * that flag would break approvals on a locked screen. For the database key it
 * would be worse still, because the app could not open its own database while
 * the screen was off.
 */
abstract class AndroidKeystoreSealer(private val alias: String) : SecretSealer {

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrGenerateKey(): SecretKey {
        if (!keyStore.containsAlias(alias)) {
            generateKey()
        }
        return keyStore.getKey(alias, null) as? SecretKey
            ?: throw SealedSecretUnrecoverableException(
                "Keystore entry $alias is missing or is not a secret key"
            )
    }

    private fun generateKey() {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        // StrongBox puts the key in dedicated tamper-resistant hardware, which
        // is what stops a root exploit walking off with it. Support is a device
        // property, not an API level: plenty of API 28+ hardware has none, and
        // some that advertises it fails at generation time under memory
        // pressure. The fallback is therefore driven by the exception rather
        // than by a feature flag lookup, which is the only check that cannot
        // disagree with reality.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                keyGenerator.init(keySpec(strongBoxBacked = true))
                keyGenerator.generateKey()
                return
            } catch (e: StrongBoxUnavailableException) {
                // Fall through to a software-backed key. The secret is still
                // encrypted at rest; it just is not hardware isolated.
            }
        }

        keyGenerator.init(keySpec(strongBoxBacked = false))
        keyGenerator.generateKey()
    }

    private fun keySpec(strongBoxBacked: Boolean): KeyGenParameterSpec =
        KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply {
                if (strongBoxBacked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setIsStrongBoxBacked(true)
                }
            }
            .build()

    override fun encrypt(data: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrGenerateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)

        // Combine IV and Ciphertext and encode as Base64
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * @throws SealedSecretUnrecoverableException for every permanent failure:
     *   the key is gone, the key is no longer valid, or the stored blob is
     *   malformed, truncated, or fails its authentication tag. Callers can
     *   recover from this one exception, so nothing else may escape.
     */
    override fun decrypt(encryptedBase64: String): ByteArray {
        val combined = try {
            Base64.decode(encryptedBase64, Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            throw SealedSecretUnrecoverableException("Sealed value is not valid Base64", e)
        }

        // A truncated blob would otherwise fail as an index out of bounds from
        // deep inside a copy, which reads like a crash rather than a corrupt
        // record. An IV and a full tag have to be present: GCM emits the tag
        // even for empty plaintext, so anything shorter than the two together
        // cannot be a value this class produced.
        if (combined.size < GCM_IV_LENGTH + GCM_TAG_LENGTH_BYTES) {
            throw SealedSecretUnrecoverableException(
                "Sealed value is ${combined.size} bytes, too short to be a sealed secret"
            )
        }

        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        // doFinal belongs inside this try, not after it. Callers recover from
        // SealedSecretUnrecoverableException and nothing else, so an
        // AEADBadTagException from a corrupted or tampered blob would escape as
        // an unhandled crash on every launch, taking out the one code path that
        // knows how to recover from an unreadable secret.
        return try {
            cipher.init(Cipher.DECRYPT_MODE, getOrGenerateKey(), spec)
            cipher.doFinal(encrypted)
        } catch (e: KeyPermanentlyInvalidatedException) {
            // The key survived but the conditions it was bound to did not, for
            // example the device's secure lock screen was removed. Nothing can
            // recover the ciphertext, so say so precisely instead of letting a
            // generic failure suggest a retry might work.
            throw SealedSecretUnrecoverableException(
                "The Keystore key $alias was invalidated; anything it sealed is gone",
                e
            )
        } catch (e: GeneralSecurityException) {
            // Every remaining checked failure here is permanent: a bad tag, an
            // unusable key, an unrecoverable entry. None of them get better on
            // a retry. Note that ProviderException, which is how a Keystore
            // daemon failure surfaces, is deliberately not caught: it is a
            // transient system fault and is not evidence the secret is gone.
            throw SealedSecretUnrecoverableException(
                "The value sealed under $alias could not be decrypted",
                e
            )
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"

        /** GCM's standard IV length, in bytes. */
        const val GCM_IV_LENGTH = 12

        /** GCM authentication tag length, in bits. */
        const val GCM_TAG_LENGTH_BITS = 128

        /** The same tag length in bytes, as it appears in the sealed blob. */
        const val GCM_TAG_LENGTH_BYTES = GCM_TAG_LENGTH_BITS / 8
    }
}

/**
 * Seals the per-manager HOTP secrets (ADR-005).
 *
 * Losing this key costs every manager a re-provisioning of their authenticator,
 * which is recoverable by scanning a new QR code.
 */
@Singleton
class KeystoreManager @Inject constructor() : AndroidKeystoreSealer(HOTP_SECRET_KEY_ALIAS)

/**
 * Seals the SQLCipher passphrase (ADR-022, #159).
 *
 * Deliberately a different key from [KeystoreManager]. Losing this one costs
 * the entire database, which is a categorically worse outcome than losing the
 * HOTP secrets, so the two should not be able to take each other down.
 */
@Singleton
class DatabaseKeySealer @Inject constructor() : AndroidKeystoreSealer(DATABASE_KEY_ALIAS)

private const val HOTP_SECRET_KEY_ALIAS = "KaupHOTPSecretKey"
private const val DATABASE_KEY_ALIAS = "KaupDatabaseKey"
