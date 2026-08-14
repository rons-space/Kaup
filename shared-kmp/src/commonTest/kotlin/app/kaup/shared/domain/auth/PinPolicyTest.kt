package app.kaup.shared.domain.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the lockout bug this policy exists to prevent: onboarding accepted 4 to
 * 6 digits while the lock screen only ever read 4, so a 5 or 6 digit PIN could
 * be created and then never entered again.
 *
 * The invariant is that every PIN onboarding accepts must be submittable on the
 * entry screen.
 */
class PinPolicyTest {

    @Test
    fun `every acceptable new pin can be submitted on the entry screen`() {
        val newPin = "1".repeat(PinPolicy.NEW_PIN_LENGTH)
        assertTrue(PinPolicy.isValidNewPin(newPin))
        assertTrue(PinPolicy.isSubmittable(newPin))
    }

    @Test
    fun `a six digit pin can be typed in full`() {
        var pin = ""
        repeat(PinPolicy.NEW_PIN_LENGTH) {
            assertTrue(PinPolicy.canAppend(pin), "digit ${pin.length + 1} was rejected")
            pin += "7"
        }
        assertFalse(PinPolicy.canAppend(pin))
        assertTrue(PinPolicy.isSubmittable(pin))
    }

    @Test
    fun `new pins shorter than the policy are rejected at creation`() {
        assertFalse(PinPolicy.isValidNewPin("1234"))
        assertFalse(PinPolicy.isValidNewPin("12345"))
    }

    @Test
    fun `a legacy four digit pin can still be entered`() {
        assertTrue(PinPolicy.isSubmittable("1234"))
    }

    @Test
    fun `non digits and short or long input are rejected`() {
        assertFalse(PinPolicy.isSubmittable("123"))
        assertFalse(PinPolicy.isSubmittable("1".repeat(PinPolicy.MAX_ENTRY_LENGTH + 1)))
        assertFalse(PinPolicy.isSubmittable("12a4"))
        assertFalse(PinPolicy.isValidNewPin("12345a"))
    }
}
