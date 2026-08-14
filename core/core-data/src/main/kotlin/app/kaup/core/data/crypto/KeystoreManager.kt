package app.kaup.core.data.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thrown when the Keystore key that protects the HOTP secret is gone or no
 * longer usable.
 *
 * This is not the same as "decryption failed" and must not be reported as one.
 * The stored ciphertext is intact and permanently unreadable, so the only way
 * out is to provision a new HOTP secret. A caller that shows a generic error
 * leaves the manager tapping a button that can never work again.
 */
class HotpSecretUnrecoverableException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Wraps the Android Keystore key that encrypts the HOTP secret at rest.
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
 * that flag would break approvals on a locked screen.
 */
@Singleton
class KeystoreManager @Inject constructor() : SecretSealer {
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val alias = "KaupHOTPSecretKey"

    private fun getOrGenerateKey(): SecretKey {
        if (!keyStore.containsAlias(alias)) {
            generateKey()
        }
        return keyStore.getKey(alias, null) as? SecretKey
            ?: throw HotpSecretUnrecoverableException(
                "Keystore entry $alias is missing or is not a secret key"
            )
    }

    private fun generateKey() {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
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
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
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
     * @throws HotpSecretUnrecoverableException when the key is gone, the key is
     *   no longer valid, or the stored blob is too short to contain an IV.
     */
    override fun decrypt(encryptedBase64: String): ByteArray {
        val combined = try {
            Base64.decode(encryptedBase64, Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            throw HotpSecretUnrecoverableException("Stored HOTP secret is not valid Base64", e)
        }

        // A truncated blob would otherwise fail as an index out of bounds from
        // deep inside a copy, which reads like a crash rather than a corrupt
        // record. GCM_IV_LENGTH + at least a tag has to be present.
        if (combined.size <= GCM_IV_LENGTH) {
            throw HotpSecretUnrecoverableException(
                "Stored HOTP secret is ${combined.size} bytes, too short to contain an IV"
            )
        }

        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        try {
            cipher.init(Cipher.DECRYPT_MODE, getOrGenerateKey(), spec)
        } catch (e: KeyPermanentlyInvalidatedException) {
            // The key survived but the conditions it was bound to did not, for
            // example the device's secure lock screen was removed. Nothing can
            // recover the ciphertext, so say so precisely instead of letting a
            // generic failure suggest a retry might work.
            throw HotpSecretUnrecoverableException(
                "The Keystore key protecting the HOTP secret was invalidated; " +
                    "the secret must be provisioned again",
                e
            )
        }

        return cipher.doFinal(encrypted)
    }

    private companion object {
        /** GCM's standard IV length, in bytes. */
        const val GCM_IV_LENGTH = 12

        /** GCM authentication tag length, in bits. */
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
