package app.kaup.feature.auth.ui.hotp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.kaup.core.ui.auth.LocalPermissions
import app.kaup.feature.auth.ui.approval.ManagerApprovalGate
import app.kaup.shared.domain.auth.AuthorizationDecision
import app.kaup.shared.domain.auth.AuthorizationPolicy

/**
 * The first operation in the app that is actually gated.
 *
 * Provisioning mints the HOTP secret that authorises every other override, and
 * doing it again invalidates the manager's existing one, so it needs
 * `USERS_EDIT`. Only OWNER holds that by default; a manager reaching this
 * screen is offered an owner's approval instead of a refusal, which is the
 * whole point of the override flow.
 *
 * The check here decides which UI to draw. It is not what protects the
 * operation: `HotpProvisioningViewModel.saveAndComplete` runs the same check
 * against the database before it writes anything, and re-reads the approval it
 * is handed. This composable being wrong would be a usability bug, not a
 * security one.
 */
@Composable
fun HotpProvisioningRoute(
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val permissions = LocalPermissions.current
    val decision = AuthorizationPolicy.evaluate(
        permission = HotpProvisioningViewModel.REQUIRED_PERMISSION,
        sessionPermissions = permissions
    )

    // Survives rotation so an approval is not thrown away by turning the tablet.
    var approvalLogId by rememberSaveable { mutableStateOf<String?>(null) }

    val granted = decision !is AuthorizationDecision.RequiresManagerApproval
    if (granted || approvalLogId != null) {
        HotpProvisioningScreen(
            approvalLogId = approvalLogId,
            onComplete = onComplete
        )
    } else {
        ManagerApprovalGate(
            permission = HotpProvisioningViewModel.REQUIRED_PERMISSION,
            actionName = "Set up manager authorization",
            onApproved = { approvalLogId = it },
            onDismiss = onCancel
        )
    }
}
