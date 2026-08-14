package app.kaup.android.di

import java.io.DataInputStream
import java.io.File
import java.io.IOException

/**
 * Recognises an unencrypted SQLite file left behind by a pre-#159 install.
 *
 * Adopting SQLCipher did not change the database filename, so an existing
 * `0.1-alpha` install already has a plaintext file sitting at exactly the path
 * the encrypted database now wants. SQLCipher encrypts the whole file including
 * the header, so handing that file to `SupportOpenHelperFactory` fails inside
 * the native open with "file is not a database" rather than surfacing as a
 * schema mismatch. Room's destructive fallback cannot help: it reads
 * `user_version` out of the database, which requires opening it first. The
 * result on an upgraded device is a crash on every launch with no way out but
 * clearing app data.
 *
 * The detection is a header comparison rather than a "did we write a
 * preference" flag, because a flag records what this code believes and the
 * header records what is actually on disk. Those disagree precisely in the
 * cases that matter: a restored backup, a downgrade, cleared preferences.
 */
internal object PlaintextDatabase {

    /**
     * The 16-byte magic every unencrypted SQLite file starts with, including
     * its trailing NUL.
     *
     * An encrypted database has no plaintext header at all, so its first bytes
     * are ciphertext. A false positive would need those bytes to collide with
     * this exact string, which is a 2^-128 event, and it is worth being clear
     * about the consequence of the reverse: this returns `false` for anything
     * it does not positively recognise, so an unreadable file is left alone
     * rather than deleted on a guess.
     */
    private val SQLITE_HEADER: ByteArray = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /**
     * Whether [file] is an unencrypted SQLite database.
     *
     * `false` for a missing file, a file too short to carry the header, and any
     * read failure. Deleting a database is irreversible, so every ambiguous
     * case has to fall on the side of not deleting.
     */
    fun isPlaintext(file: File): Boolean {
        if (!file.isFile || file.length() < SQLITE_HEADER.size) {
            return false
        }

        val header = ByteArray(SQLITE_HEADER.size)
        return try {
            // readFully, not InputStream.readNBytes: the latter is API 33 and
            // minSdk here is 24, so it would compile and then throw
            // NoSuchMethodError on most of the fleet.
            DataInputStream(file.inputStream()).use { stream ->
                stream.readFully(header)
            }
            header.contentEquals(SQLITE_HEADER)
        } catch (e: IOException) {
            false
        }
    }
}
