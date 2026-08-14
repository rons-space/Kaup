package app.kaup.core.data.testing

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.kaup.core.data.KaupDatabase
import app.kaup.core.data.crypto.SealedSecretUnrecoverableException
import app.kaup.core.data.crypto.SecretSealer
import app.kaup.core.data.entities.LocationEntity
import app.kaup.core.data.entities.UserEntity
import app.kaup.core.data.time.TimeProvider
import app.kaup.shared.domain.models.auth.Role

/**
 * Shared scaffolding for the `:core-data` tests.
 *
 * The database is real. These tests exist to cover the transactional glue that
 * the pure policy classes in `:shared-kmp` cannot: the compare-and-set counter
 * claim, the grant and its audit row committing together, the throttle state
 * surviving a write. Faking the DAOs would test the mock rather than the SQL,
 * and the SQL is the part that was never exercised.
 */

/** An in-memory Room database with the default location already seeded. */
internal fun inMemoryDatabase(): KaupDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        KaupDatabase::class.java
    )
        // The DAOs are not suspend functions; the production callers wrap them
        // in withContext(Dispatchers.IO). Tests call them directly.
        .allowMainThreadQueries()
        .build()
        .also { database ->
            // Every location-aware table has a non-null locationId foreign key,
            // so nothing can be inserted until this row exists. In production
            // the DatabaseModule onCreate callback does this; an in-memory
            // builder has no such callback.
            database.locationDao().insert(
                LocationEntity(
                    id = LocationEntity.DEFAULT_ID,
                    name = LocationEntity.DEFAULT_NAME,
                    address = null
                )
            )
        }

/** The 20 byte HOTP secret these tests provision managers with. */
internal val TEST_SECRET: ByteArray = ByteArray(20) { index -> (index + 1).toByte() }

/**
 * A reversible stand-in for the Android Keystore.
 *
 * [failure] makes [decrypt] throw, which is how a real device behaves after the
 * user changes their lock screen credential and the key is invalidated.
 */
internal class FakeSecretSealer(
    var failure: SealedSecretUnrecoverableException? = null
) : SecretSealer {

    override fun encrypt(data: ByteArray): String =
        data.joinToString("") { byte -> "%02x".format(byte) }

    override fun decrypt(encryptedBase64: String): ByteArray {
        failure?.let { throw it }
        // A fresh array every call, deliberately. Callers zero the plaintext
        // when they are finished with it, and handing back a shared array would
        // mean the second read of the same secret came back as zeroes, which is
        // a bug this fake must not hide.
        return encryptedBase64.chunked(2)
            .map { pair -> pair.toInt(16).toByte() }
            .toByteArray()
    }
}

/** A clock the test moves by hand. */
internal class FakeTimeProvider(
    var uptime: Long = 1_000L,
    var epoch: Long = 1_700_000_000_000L
) : TimeProvider {

    override fun uptimeMillis(): Long = uptime

    override fun epochMillis(): Long = epoch

    fun advance(millis: Long) {
        uptime += millis
        epoch += millis
    }
}

/**
 * Inserts a user.
 *
 * The PIN columns are non-null and irrelevant here, so they get obvious
 * placeholders rather than real hashes: nothing in these tests goes near
 * PinAuthenticator.
 */
internal fun KaupDatabase.insertUser(
    id: String,
    role: Role,
    sealedSecret: String? = null,
    counter: Long = 0L
): UserEntity {
    val user = UserEntity(
        id = id,
        name = "user-$id",
        role = role,
        pinHash = "unused",
        pinSalt = "unused",
        pinIterations = 1,
        hotpSecretEncrypted = sealedSecret,
        hotpCounter = counter
    )
    userDao().insertUser(user)
    return user
}
