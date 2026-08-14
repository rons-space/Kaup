package app.kaup.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardOptions
import app.kaup.core.ui.R
import app.kaup.shared.domain.auth.OverrideScope

/** A manager who could approve the pending action, for the picker. */
data class ApproverOption(val id: String, val name: String)

/**
 * Asks a manager to authorise a restricted action.
 *
 * This used to hand any non-empty string to `onApprove`, which meant the
 * overlay approved whatever was typed into it. It is now purely presentational
 * and cannot approve anything by itself: it collects an approver, a code and a
 * scope, and hands them to a caller that validates them against the Keystore
 * and the audit log. `:core-ui` has no access to `:core-data`, which is what
 * keeps that division honest rather than merely intended.
 *
 * The approver is chosen before the code is entered. A code is an HMAC over one
 * manager's secret and counter, so the validating side has to know whose secret
 * to check it against; guessing by trying everyone would defeat the per-manager
 * throttle and make the audit row ambiguous. See ADR-021.
 */
@Composable
fun ManagerApprovalOverlay(
    actionName: String,
    approvers: List<ApproverOption>,
    selectedApproverId: String?,
    onApproverSelected: (String) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    scope: OverrideScope,
    onScopeChange: (OverrideScope) -> Unit,
    elevationTokensEnabled: Boolean,
    elevationWindowMinutes: Int,
    errorMessage: String?,
    isSubmitting: Boolean,
    onApprove: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.manager_approval_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.manager_approval_action, actionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (approvers.isEmpty()) {
                    // Nothing to type a code against. Offering the field anyway
                    // would let someone burn attempts on a validation that
                    // cannot succeed.
                    Text(
                        text = stringResource(R.string.manager_approval_no_approvers),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                } else {
                    ApproverPicker(approvers, selectedApproverId, onApproverSelected)

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = code,
                        onValueChange = { entered ->
                            // Digits only, capped at the code length, so a
                            // stray character cannot be mistaken for a wrong
                            // code and charged against the manager's allowance.
                            onCodeChange(entered.filter { it.isDigit() }.take(CODE_LENGTH))
                        },
                        label = { Text(stringResource(R.string.manager_approval_code_label)) },
                        supportingText = { Text(stringResource(R.string.manager_approval_code_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        isError = errorMessage != null,
                        enabled = !isSubmitting,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (elevationTokensEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        ScopePicker(scope, onScopeChange, elevationWindowMinutes)
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onApprove,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isSubmitting &&
                        selectedApproverId != null &&
                        code.length == CODE_LENGTH
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.height(24.dp))
                    } else {
                        Text(stringResource(R.string.manager_approval_approve))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.manager_approval_cancel))
                }
            }
        }
    }
}

@Composable
private fun ApproverPicker(
    approvers: List<ApproverOption>,
    selectedApproverId: String?,
    onApproverSelected: (String) -> Unit
) {
    Text(
        text = stringResource(R.string.manager_approval_choose_approver),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        approvers.forEach { approver ->
            FilterChip(
                selected = approver.id == selectedApproverId,
                onClick = { onApproverSelected(approver.id) },
                label = { Text(approver.name) }
            )
        }
    }
}

@Composable
private fun ScopePicker(
    scope: OverrideScope,
    onScopeChange: (OverrideScope) -> Unit,
    elevationWindowMinutes: Int
) {
    Text(
        text = stringResource(R.string.manager_approval_scope_title),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.fillMaxWidth()
    )
    Column(Modifier.selectableGroup().fillMaxWidth()) {
        ScopeOption(
            selected = scope == OverrideScope.SPECIFIC_ACTION,
            onSelect = { onScopeChange(OverrideScope.SPECIFIC_ACTION) },
            title = stringResource(R.string.manager_approval_scope_specific),
            detail = stringResource(R.string.manager_approval_scope_specific_detail),
            detailIsWarning = false
        )
        ScopeOption(
            selected = scope == OverrideScope.ELEVATION_TOKEN,
            onSelect = { onScopeChange(OverrideScope.ELEVATION_TOKEN) },
            title = stringResource(
                R.string.manager_approval_scope_elevation,
                elevationWindowMinutes
            ),
            // ADR-005 requires the trade-off to be stated before the manager
            // picks this, not discovered afterwards.
            detail = stringResource(R.string.manager_approval_scope_elevation_warning),
            detailIsWarning = true
        )
    }
}

@Composable
private fun ScopeOption(
    selected: Boolean,
    onSelect: () -> Unit,
    title: String,
    detail: String,
    detailIsWarning: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (detailIsWarning) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

private const val CODE_LENGTH = 6
