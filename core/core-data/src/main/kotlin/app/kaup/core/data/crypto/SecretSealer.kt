package app.kaup.core.data.crypto

/**
 * Seals and unseals a secret at rest.
 *
 * This exists so the things that depend on encryption can be tested. The only
 * production implementation is [KeystoreManager], which talks to
 * `AndroidKeyStore`, and `AndroidKeyStore` has no software implementation:
 * Robolectric does not provide it and a JVM test cannot reach it. Before this
 * interface, `HotpCodeIssuer` and `OverrideAuthorizer` named the concrete class
 * in their constructors, Kotlin classes are final, and so the two pieces of
 * code that decide whether a manager override succeeds could not be exercised
 * by any test at all. That is the structural half of #174.
 *
 * Deliberately expressed in `ByteArray` rather than `String`. Callers zero the
 * plaintext when they are done with it, which a `String` cannot support because
 * it is immutable and may be interned.
 */
interface SecretSealer {

    /**
     * Encrypts [data] and returns it as an opaque, storable string.
     *
     * The caller keeps ownership of [data] and is responsible for zeroing it.
     */
    fun encrypt(data: ByteArray): String

    /**
     * Reverses [encrypt].
     *
     * @throws SealedSecretUnrecoverableException if the sealing key is gone or no
     * longer usable, which on a real device means the user changed their lock
     * screen credential or the key was invalidated. Callers must treat this as
     * "this secret is gone forever", not as a transient failure.
     */
    fun decrypt(encryptedBase64: String): ByteArray
}
