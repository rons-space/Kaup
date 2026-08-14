package app.kaup.shared.domain.auth

/**
 * The single source of truth for PIN length.
 *
 * Onboarding used to accept 4 to 6 digits while the lock screen only ever read
 * 4 and submitted automatically at the fourth keypress, so an owner who chose a
 * 5 or 6 digit PIN could never enter it again. Both screens now read these
 * constants, and entry is confirmed explicitly rather than triggered by length.
 *
 * New PINs are fixed at [NEW_PIN_LENGTH]. Entry accepts anything from
 * [MIN_ENTRY_LENGTH] so that a shorter PIN created before this policy existed
 * can still be typed and the owner is not locked out a second time.
 */
object PinPolicy {

    /** Digits required when creating or changing a PIN. */
    const val NEW_PIN_LENGTH: Int = 6

    /** Shortest PIN the entry screen accepts, for PINs predating this policy. */
    const val MIN_ENTRY_LENGTH: Int = 4

    /** Longest PIN the entry screen accepts, and the number of dots drawn. */
    const val MAX_ENTRY_LENGTH: Int = NEW_PIN_LENGTH

    /** True when [pin] is acceptable for a new or changed credential. */
    fun isValidNewPin(pin: String): Boolean =
        pin.length == NEW_PIN_LENGTH && pin.all { it.isDigit() }

    /** True when [pin] is long enough to be submitted on the entry screen. */
    fun isSubmittable(pin: String): Boolean =
        pin.length in MIN_ENTRY_LENGTH..MAX_ENTRY_LENGTH && pin.all { it.isDigit() }

    /** True when another digit may be appended to [pin]. */
    fun canAppend(pin: String): Boolean = pin.length < MAX_ENTRY_LENGTH
}
