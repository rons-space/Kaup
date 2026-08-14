package app.kaup.feature.auth.ui.hotp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kaup.core.data.auth.HotpCodeIssuer
import app.kaup.core.data.auth.HotpCodeResult
import app.kaup.core.data.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OverrideCodeState(
    val currentCode: String? = null,
    val error: String? = null,
    val isGenerating: Boolean = false
)

/**
 * Shows the manager a fresh override code.
 *
 * All this does now is ask [HotpCodeIssuer] and render the answer. It used to
 * decrypt the secret, generate the code and write the counter itself, taking
 * the counter from the cached session user, which is a snapshot from login that
 * the counter update never refreshed. The result was that every code generated
 * in a session was identical. Keeping the counter and the Keystore in
 * `:core-data` is what makes that class of bug unavailable from here.
 */
@HiltViewModel
class OverrideCodeGenerationViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val hotpCodeIssuer: HotpCodeIssuer
) : ViewModel() {

    private val _uiState = MutableStateFlow(OverrideCodeState())
    val uiState: StateFlow<OverrideCodeState> = _uiState.asStateFlow()

    init {
        generateCode()
    }

    fun generateCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null) }

            val userId = sessionManager.currentUser.value?.id
            if (userId == null) {
                _uiState.update {
                    it.copy(isGenerating = false, error = "No user is signed in.")
                }
                return@launch
            }

            when (val result = hotpCodeIssuer.issue(userId)) {
                is HotpCodeResult.Issued -> _uiState.update {
                    it.copy(isGenerating = false, currentCode = result.code, error = null)
                }

                HotpCodeResult.NotProvisioned -> _uiState.update {
                    it.copy(
                        isGenerating = false,
                        currentCode = null,
                        error = "HOTP secret not configured for this user. " +
                            "Please complete HOTP Provisioning first."
                    )
                }

                is HotpCodeResult.Unavailable -> _uiState.update {
                    it.copy(isGenerating = false, currentCode = null, error = result.reason)
                }
            }
        }
    }
}
