package app.kaup.shared.domain.auth

/**
 * The two override scopes ADR-005 requires a manager to choose between.
 *
 * A caveat that belongs next to the type rather than buried in a document: the
 * scope is **not** carried inside the code. An HOTP code is an HMAC over a
 * counter and nothing else, so any code the manager generates will validate
 * against any action the staff device happens to be asking about. What the
 * scope actually controls is what the validating device does *after* a code
 * checks out, and what it writes to the audit log.
 *
 * That is weaker than it sounds only if the manager is not watching. In the
 * flow ADR-005 describes, the manager is told what they are approving before
 * they generate anything, and the [SPECIFIC_ACTION] audit row names the
 * permission and transaction the code was spent on, so a mismatch is visible
 * afterwards. Binding the scope cryptographically would mean HMACing it
 * alongside the counter, which stops the codes being RFC 4226 HOTP and breaks
 * the authenticator-app provisioning path. See ADR-021.
 */
enum class OverrideScope {
    /**
     * Recommended. The approval is spent immediately on one named permission
     * and one transaction, and nothing is retained afterwards.
     */
    SPECIFIC_ACTION,

    /**
     * The approval is retained as an [ElevationToken] and can be spent on one
     * action of the staff's choosing inside a short window. Convenient when a
     * manager is about to walk away, and correspondingly easier to abuse, which
     * is why the UI warns before issuing one and an admin can disable it.
     */
    ELEVATION_TOKEN
}
