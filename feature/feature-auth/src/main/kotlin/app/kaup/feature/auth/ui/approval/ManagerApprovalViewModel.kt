package app.kaup.feature.auth.ui.approval

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kaup.core.data.auth.OverrideAuthorizer
import app.kaup.core.data.auth.OverrideResult
import app.kaup.core.data.auth.SessionManager
import app.kaup.core.data.preferences.StorePreferences
import app.kaup.core.ui.components.ApproverOption
import app.kaup.shared.domain.auth.OverrideScope
import app.kaup.shared.domain.models.auth.Permission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/** What the approval overlay needs to draw itself, plus the outcome. */
data class ManagerApprovalState(
    val approvers: List<ApproverOption> = emptyList(),
    val selectedApproverId: String? = null,
    val code: String = "",
    val scope: OverrideScope = OverrideScope.SPECIFIC_ACTION,
    val elevationTokensEnabled: Boolean = false,
    val elevationWindowMinutes: Int = 5,
    val errorMessage: String? = null,
    val isSubmitting: Boolean = false,
    val isLoading: Boolean = true,
    /**
     * The `override_log` row id, set once the action is cleared to proceed.
     *
     * The caller hands this to the operation being unlocked, which re-checks it
     * against the database. A boolean here would be a claim; an id is something
     * the data layer can verify.
     */
    val approvedLogId: String? = null
)

/**
 * Drives the manager approval overlay.
 *
 * The overlay itself lives in `:core-ui` and cannot reach the database. This is
 * the piece that can: it lists the managers who could actually approve the
 * request, submits the code to [OverrideAuthorizer], and turns the result into
 * something a cashier can act on.
 *
 * It never decides anything itself. Whether the code was right, whether the
 * approver had the authority to delegate, whether the attempt was throttled and
 * whether the audit row was written are all settled in `:core-data`, and this
 * class only renders the answer.
 */
@HiltViewModel
class ManagerApprovalViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val overrideAuthorizer: OverrideAuthorizer,
    private val storePreferences: StorePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManagerApprovalState())
    val uiState: StateFlow<ManagerApprovalState> = _uiState.asStateFlow()

    private var permission: Permission? = null
    private var transactionId: String? = null

    /** Called by the host when the overlay is raised for [permission]. */
    fun start(permission: Permission, transactionId: String? = null) {
        this.permission = permission
        this.transactionId = transactionId

        viewModelScope.launch {
            val elevationEnabled = storePreferences.elevationTokensEnabled.first()
            val windowMs = storePreferences.elevationWindowMs.first()
            val approvers = overrideAuthorizer.approversFor(permission)
                .map { ApproverOption(id = it.id, name = it.name) }

            _uiState.update {
                it.copy(
                    approvers = approvers,
                    // Preselect when there is no choice to make, which is the
                    // common single-manager store.
                    selectedApproverId = approvers.singleOrNull()?.id,
                    elevationTokensEnabled = elevationEnabled,
                    elevationWindowMinutes = (windowMs / 60_000.0).roundToInt().coerceAtLeast(1),
                    scope = OverrideScope.SPECIFIC_ACTION,
                    isLoading = false
                )
            }
        }
    }

    fun onApproverSelected(id: String) {
        _uiState.update { it.copy(selectedApproverId = id, errorMessage = null) }
    }

    fun onCodeChange(code: String) {
        _uiState.update { it.copy(code = code, errorMessage = null) }
    }

    fun onScopeChange(scope: OverrideScope) {
        _uiState.update { it.copy(scope = scope) }
    }

    fun submit() {
        val permission = permission ?: return
        val state = _uiState.value
        val approverId = state.selectedApproverId ?: return
        val requesterId = sessionManager.currentUser.value?.id ?: return
        if (state.isSubmitting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }

            val result = overrideAuthorizer.authorize(
                approverUserId = approverId,
                requestedByUserId = requesterId,
                permission = permission,
                code = state.code,
                scope = state.scope,
                transactionId = transactionId,
                elevationTokensEnabled = state.elevationTokensEnabled,
                elevationWindowMillis = state.elevationWindowMinutes * 60_000L
            )

            when (result) {
                is OverrideResult.Granted -> {
                    result.token?.let(sessionManager::grantElevation)
                    _uiState.update {
                        it.copy(isSubmitting = false, approvedLogId = result.logId, code = "")
                    }
                }

                is OverrideResult.Rejected -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        code = "",
                        errorMessage = if (result.attemptsBeforeLockout > 0) {
                            "That code is not valid. ${result.attemptsBeforeLockout} " +
                                "attempt(s) left before approvals are locked."
                        } else {
                            "That code is not valid. The next wrong code locks approvals."
                        }
                    )
                }

                is OverrideResult.LockedOut -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        code = "",
                        errorMessage = "Too many wrong codes. Try again in " +
                            "${describeWait(result.remainingMillis)}."
                    )
                }

                OverrideResult.NotProvisioned -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = "That manager has not set up authorization on this device."
                    )
                }

                is OverrideResult.ApproverNotPermitted -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = "That manager is not allowed to approve this action."
                    )
                }

                OverrideResult.ElevationDisabled -> _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        scope = OverrideScope.SPECIFIC_ACTION,
                        elevationTokensEnabled = false,
                        errorMessage = "Time-limited approvals are switched off for this store."
                    )
                }

                is OverrideResult.Unavailable -> _uiState.update {
                    it.copy(isSubmitting = false, code = "", errorMessage = result.reason)
                }
            }
        }
    }

    /**
     * Reads the lockout back as a rounded wait.
     *
     * Rounding up matters: telling someone to wait "0 minutes" when 40 seconds
     * remain sends them straight back to a refusal.
     */
    private fun describeWait(millis: Long): String {
        val seconds = (millis + 999) / 1000
        if (seconds < 90) return "$seconds seconds"
        return "${(seconds + 59) / 60} minutes"
    }

}
