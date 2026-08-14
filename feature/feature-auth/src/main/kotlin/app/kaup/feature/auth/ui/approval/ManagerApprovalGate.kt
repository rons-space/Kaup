package app.kaup.feature.auth.ui.approval

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import app.kaup.core.ui.components.ManagerApprovalOverlay
import app.kaup.shared.domain.models.auth.Permission

/**
 * Raises the manager approval overlay for [permission] and reports the grant.
 *
 * [onApproved] receives the id of the `override_log` row that was written. That
 * id is the point of the whole exercise: the operation being unlocked passes it
 * back to `OverrideAuthorizer.verifyGrant`, so the approval is a capability the
 * data layer can check rather than a boolean this composable remembered.
 */
@Composable
fun ManagerApprovalGate(
    permission: Permission,
    actionName: String,
    onApproved: (logId: String) -> Unit,
    onDismiss: () -> Unit,
    transactionId: String? = null,
    viewModel: ManagerApprovalViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(permission, transactionId) {
        viewModel.start(permission, transactionId)
    }

    LaunchedEffect(state.approvedLogId) {
        state.approvedLogId?.let(onApproved)
    }

    if (state.isLoading) return

    ManagerApprovalOverlay(
        actionName = actionName,
        approvers = state.approvers,
        selectedApproverId = state.selectedApproverId,
        onApproverSelected = viewModel::onApproverSelected,
        code = state.code,
        onCodeChange = viewModel::onCodeChange,
        scope = state.scope,
        onScopeChange = viewModel::onScopeChange,
        elevationTokensEnabled = state.elevationTokensEnabled,
        elevationWindowMinutes = state.elevationWindowMinutes,
        errorMessage = state.errorMessage,
        isSubmitting = state.isSubmitting,
        onApprove = viewModel::submit,
        onDismiss = onDismiss
    )
}
