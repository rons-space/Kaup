package app.kaup.android.di

import java.io.File
import java.security.SecureRandom
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The upgrade guard from #159, which decides whether an existing database file
 * is the plaintext one a pre-encryption install left behind.
 *
 * A false positive deletes a store's database, so the negative cases carry more
 * weight here than the positive one.
 */
class PlaintextDatabaseTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val sqliteHeader = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    @Test
    fun `recognises an unencrypted SQLite file`() {
        val file = write(sqliteHeader + ByteArray(4_096))

        assertTrue(PlaintextDatabase.isPlaintext(file))
    }

    @Test
    fun `does not recognise an encrypted file`() {
        // SQLCipher encrypts page one as well, so an encrypted database has no
        // readable header at all and its first bytes are indistinguishable from
        // random.
        val encrypted = ByteArray(4_096).also { SecureRandom().nextBytes(it) }

        assertFalse(PlaintextDatabase.isPlaintext(write(encrypted)))
    }

    @Test
    fun `does not recognise a missing file`() {
        val missing = File(temporaryFolder.root, "kaup_database")

        assertFalse(PlaintextDatabase.isPlaintext(missing))
    }

    @Test
    fun `does not recognise an empty file`() {
        assertFalse(PlaintextDatabase.isPlaintext(write(ByteArray(0))))
    }

    @Test
    fun `does not recognise a file shorter than the header`() {
        // A truncated header must not be extended into a match by whatever
        // happens to follow it, and must not read past the end of the file.
        assertFalse(PlaintextDatabase.isPlaintext(write(sqliteHeader.copyOf(8))))
    }

    @Test
    fun `does not recognise a directory`() {
        val directory = temporaryFolder.newFolder("kaup_database")

        assertFalse(PlaintextDatabase.isPlaintext(directory))
    }

    @Test
    fun `requires the trailing NUL of the header`() {
        // "SQLite format 3" with the final byte replaced. Comparing only the
        // printable prefix would let this through.
        val almost = sqliteHeader.copyOf().also { it[it.size - 1] = 'X'.code.toByte() }

        assertFalse(PlaintextDatabase.isPlaintext(write(almost + ByteArray(4_096))))
    }

    private fun write(bytes: ByteArray): File =
        temporaryFolder.newFile().apply { writeBytes(bytes) }
}
