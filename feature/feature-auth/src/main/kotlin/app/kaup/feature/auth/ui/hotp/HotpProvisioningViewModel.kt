package app.kaup.feature.auth.ui.hotp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kaup.core.data.auth.OverrideAuthorizer
import app.kaup.core.data.auth.SessionManager
import app.kaup.core.data.crypto.SecretSealer
import app.kaup.core.data.dao.UserDao
import app.kaup.core.data.preferences.StorePreferences
import app.kaup.core.data.time.TimeProvider
import app.kaup.shared.domain.auth.AuthorizationDecision
import app.kaup.shared.domain.models.auth.Permission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.SecureRandom
import javax.inject.Inject

data class HotpProvisioningState(
    val isGenerating: Boolean = true,
    val otpAuthUri: String? = null,
    val base32Secret: String? = null,
    /**
     * The manual-entry secret starts hidden. The QR code is the intended path
     * and is on screen anyway; the Base32 form is a fallback for a device whose
     * camera will not focus, and there is no reason for it to be readable over
     * a manager's shoulder for the whole time someone is fumbling with a
     * scanner.
     */
    val isSecretRevealed: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Generates and stores a manager's HOTP secret.
 *
 * The secret is the credential that authorises every override, so this screen
 * handles the only plaintext copy of it that will ever exist. Two consequences
 * are handled here and one cannot be:
 *
 * The raw bytes are zeroed in a `finally` block once encrypted, and again if
 * the ViewModel is cleared before that happens, so an abandoned provisioning
 * flow does not leave a usable key in the heap until garbage collection gets
 * around to it.
 *
 * What cannot be fixed here is the Base32 rendering. It is a String, so it is
 * immutable and unzeroable, and it stays in the heap at the JVM's discretion.
 * It is dropped from the UI state the moment provisioning completes to shorten
 * that window, but the only real fix is not to produce it, and manual entry
 * needs it. It is displayed only when explicitly revealed, and the screen sets
 * FLAG_SECURE so neither it nor the QR code can be screenshotted.
 */
@HiltViewModel
class HotpProvisioningViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val secretSealer: SecretSealer,
    private val overrideAuthorizer: OverrideAuthorizer,
    private val userDao: UserDao,
    private val storePreferences: StorePreferences,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(HotpProvisioningState())
    val uiState: StateFlow<HotpProvisioningState> = _uiState.asStateFlow()

    private var rawSecret: ByteArray? = null

    init {
        generateNewSecret()
    }

    private fun generateNewSecret() {
        viewModelScope.launch {
            val storeName = storePreferences.storeName.first()
            val user = sessionManager.currentUser.value ?: return@launch

            val random = SecureRandom()
            val secretBytes = ByteArray(20)
            random.nextBytes(secretBytes)
            rawSecret = secretBytes

            val base32Secret = encodeBase32(secretBytes)
            val uri = "otpauth://hotp/${storeName}:${user.name}?secret=$base32Secret&issuer=${storeName}&counter=0"

            _uiState.update {
                it.copy(
                    isGenerating = false,
                    otpAuthUri = uri,
                    base32Secret = base32Secret
                )
            }
        }
    }

    fun setSecretRevealed(revealed: Boolean) {
        _uiState.update { it.copy(isSecretRevealed = revealed) }
    }

    /**
     * Writes the secret, if the caller is allowed to.
     *
     * The permission check is here rather than only on the route that led here,
     * because a route is a suggestion. Provisioning mints the credential that
     * authorises every override, so a caller reaching this method without
     * USERS_EDIT has to be refused by the method itself.
     *
     * [approvalLogId] is the `override_log` row from a manager approval. It is
     * re-read from the database rather than trusted, so the screen cannot claim
     * an approval it did not obtain, and an approval for some other permission
     * cannot be spent here.
     */
    fun saveAndComplete(approvalLogId: String? = null) {
        val secret = rawSecret ?: return
        val user = sessionManager.currentUser.value ?: return

        viewModelScope.launch {
            if (!isAuthorized(user.id, approvalLogId)) {
                _uiState.update {
                    it.copy(
                        errorMessage = "You are not authorised to set up manager " +
                            "authorization, and no valid manager approval was supplied."
                    )
                }
                return@launch
            }

            try {
                val encrypted = secretSealer.encrypt(secret)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    userDao.updateUserHotp(user.id, encrypted, 0L)
                }
                // Drop the displayed copies as well. They cannot be wiped, but
                // they can stop being referenced by the screen.
                _uiState.update {
                    it.copy(
                        isSaved = true,
                        otpAuthUri = null,
                        base32Secret = null,
                        isSecretRevealed = false
                    )
                }
            } finally {
                wipeSecret()
            }
        }
    }

    private suspend fun isAuthorized(userId: String, approvalLogId: String?): Boolean {
        val decision = sessionManager.authorize(
            permission = REQUIRED_PERMISSION,
            nowUptimeMillis = timeProvider.uptimeMillis()
        )
        if (decision !is AuthorizationDecision.RequiresManagerApproval) {
            // Granted by the session, or by an elevation token. A token is
            // single use, so spend it here rather than leaving it live.
            if (decision is AuthorizationDecision.GrantedByElevation) {
                sessionManager.spendElevation()
            }
            return true
        }
        return approvalLogId != null && overrideAuthorizer.verifyGrant(
            logId = approvalLogId,
            permission = REQUIRED_PERMISSION,
            requestedByUserId = userId
        )
    }

    override fun onCleared() {
        // Covers the manager who generates a secret and then backs out. Without
        // this the bytes sit in the heap for as long as the process lives.
        wipeSecret()
        super.onCleared()
    }

    private fun wipeSecret() {
        rawSecret?.fill(0)
        rawSecret = null
    }

    companion object {
        /**
         * Provisioning a manager's HOTP secret is user administration: it
         * creates the credential that approves everything else, and doing it
         * again invalidates the old one. Only OWNER holds USERS_EDIT by
         * default, so a manager needs an owner's approval to get here.
         */
        val REQUIRED_PERMISSION: Permission = Permission.USERS_EDIT
    }

    // Simple Base32 encoder for the OTP URI
    private fun encodeBase32(bytes: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var i = 0
        var index = 0
        var digit = 0
        val result = StringBuilder((bytes.size + 7) * 8 / 5)

        while (i < bytes.size) {
            val curr = bytes[i].toInt() and 0xFF
            if (index > 3) {
                val next = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
                digit = (curr and (0xFF shr index)) shl (index - 3)
                digit = digit or (next shr (11 - index))
                i++
            } else {
                digit = (curr shr (3 - index)) and 0x1F
            }
            index = (index + 5) % 8
            if (index == 0) i++
            result.append(alphabet[digit])
        }
        return result.toString()
    }
}
