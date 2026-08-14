package app.kaup.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class HOTPGeneratorTest {

    // Test Vector Secret from RFC 4226: "12345678901234567890"
    private val rfcSecret = "12345678901234567890".encodeToByteArray()

    @Test
    fun `generates exact RFC 4226 test vectors`() {
        // RFC 4226 Appendix D expected values
        val expectedCodes = listOf(
            "755224", "287082", "359152", "969429",
            "338314", "254676", "287922", "162583",
            "399871", "520489"
        )

        for (i in expectedCodes.indices) {
            val code = HOTPGenerator.generateCode(rfcSecret, i.toLong(), digits = 6)
            assertEquals(expectedCodes[i], code, "Mismatch at counter $i")
        }
    }

    @Test
    fun `validates exact code on current counter`() {
        val input = "755224" // Counter 0
        val matchedCounter = HOTPGenerator.validateCode(rfcSecret, 0L, input, lookAheadWindow = 5)
        assertEquals(0L, matchedCounter)
    }

    @Test
    fun `validates drifted code within window`() {
        // Let's say device is at counter 0, but manager generated code for counter 4 ("338314")
        val input = "338314"
        val matchedCounter = HOTPGenerator.validateCode(rfcSecret, 0L, input, lookAheadWindow = 5)
        assertEquals(4L, matchedCounter)
    }

    @Test
    fun `rejects drifted code outside window`() {
        // Device at counter 0, code is for counter 9 ("520489")
        val input = "520489"
        val matchedCounter = HOTPGenerator.validateCode(rfcSecret, 0L, input, lookAheadWindow = 5)
        assertNull(matchedCounter)
    }

    @Test
    fun `rejects consumed code`() {
        // If a code was consumed, the counter has moved forward.
        // Device is at counter 5, input is code for counter 4 ("338314")
        val input = "338314"
        val matchedCounter = HOTPGenerator.validateCode(rfcSecret, 5L, input, lookAheadWindow = 5)
        assertNull(matchedCounter)
    }

    @Test
    fun `default look-ahead window is the ten ADR-005 requires`() {
        assertEquals(10, HOTPGenerator.DEFAULT_LOOK_AHEAD_WINDOW)
    }

    @Test
    fun `default window accepts the drift the old window of five rejected`() {
        // Counter 9 ("520489") is outside a window of 5 and inside the default.
        assertEquals(9L, HOTPGenerator.validateCode(rfcSecret, 0L, "520489"))
    }

    @Test
    fun `window is inclusive at both ends`() {
        // currentCounter + lookAheadWindow is still a match, one past it is not.
        assertEquals(10L, HOTPGenerator.validateCode(rfcSecret, 0L, code(10), lookAheadWindow = 10))
        assertNull(HOTPGenerator.validateCode(rfcSecret, 0L, code(11), lookAheadWindow = 10))
    }

    @Test
    fun `a window of zero checks only the current counter`() {
        assertEquals(7L, HOTPGenerator.validateCode(rfcSecret, 7L, code(7), lookAheadWindow = 0))
        assertNull(HOTPGenerator.validateCode(rfcSecret, 7L, code(8), lookAheadWindow = 0))
    }

    @Test
    fun `codes of the wrong length are rejected rather than truncated`() {
        assertNull(HOTPGenerator.validateCode(rfcSecret, 0L, "75522"))
        assertNull(HOTPGenerator.validateCode(rfcSecret, 0L, "7552240"))
        assertNull(HOTPGenerator.validateCode(rfcSecret, 0L, ""))
    }

    @Test
    fun `leading zeroes are preserved rather than dropped`() {
        // A code that truncates to fewer than `digits` digits must still be
        // padded, or every device would disagree about what it is.
        val secret = "leading-zero-probe".encodeToByteArray()
        val padded = generateSequence(0L) { it + 1 }
            .take(2000)
            .map { HOTPGenerator.generateCode(secret, it) }
            .first { it.startsWith("0") }
        assertEquals(6, padded.length)
    }

    @Test
    fun `eight digit codes are the eight low digits of the six digit truncation`() {
        // Same dynamic truncation, wider modulus: the 6 digit code is the 8
        // digit code's last six characters.
        val eight = HOTPGenerator.generateCode(rfcSecret, 0L, digits = 8)
        val six = HOTPGenerator.generateCode(rfcSecret, 0L, digits = 6)
        assertEquals(8, eight.length)
        assertEquals(six, eight.takeLast(6))
    }

    @Test
    fun `rejects digit counts RFC 4226 does not define`() {
        assertFailsWith<IllegalArgumentException> { HOTPGenerator.generateCode(rfcSecret, 0L, digits = 5) }
        assertFailsWith<IllegalArgumentException> { HOTPGenerator.generateCode(rfcSecret, 0L, digits = 9) }
    }

    @Test
    fun `rejects a negative counter and a negative window`() {
        assertFailsWith<IllegalArgumentException> { HOTPGenerator.generateCode(rfcSecret, -1L) }
        assertFailsWith<IllegalArgumentException> { HOTPGenerator.validateCode(rfcSecret, -1L, "755224") }
        assertFailsWith<IllegalArgumentException> {
            HOTPGenerator.validateCode(rfcSecret, 0L, "755224", lookAheadWindow = -1)
        }
    }

    @Test
    fun `rejects an empty secret rather than authorising against one`() {
        assertFailsWith<IllegalArgumentException> { HOTPGenerator.generateCode(ByteArray(0), 0L) }
    }

    private fun code(counter: Long): String = HOTPGenerator.generateCode(rfcSecret, counter)
}
