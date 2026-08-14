package app.kaup.android.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.kaup.core.ui.auth.LocalPermissions
import app.kaup.shared.domain.auth.AuthorizationDecision
import app.kaup.shared.domain.auth.AuthorizationPolicy
import app.kaup.shared.domain.models.auth.Permission

/**
 * The entry point to manager authorization.
 *
 * It exists because the provisioning and override code screens had been built
 * and were not reachable from anywhere: no route pointed at either of them, so
 * the feature could not be used or even seen. Settings was a placeholder.
 *
 * Provisioning is shown to everyone rather than hidden behind `USERS_EDIT`,
 * unlike the usual rule of hiding restricted controls. A manager who cannot
 * provision needs to be told that an owner can approve it, and a hidden row
 * would leave them concluding the feature does not exist. The row itself leads
 * to the approval overlay, and the write is refused by the ViewModel regardless
 * of what this screen decides to draw.
 */
@Composable
fun SecuritySettingsScreen(
    onProvisionHotp: () -> Unit,
    onGenerateOverrideCode: () -> Unit,
    viewModel: SecuritySettingsViewModel = hiltViewModel()
) {
    val permissions = LocalPermissions.current
    val elevationEnabled by viewModel.elevationTokensEnabled.collectAsState()

    val canConfigureStore = AuthorizationPolicy.evaluate(
        permission = Permission.SETTINGS_HOUSEKEEPING,
        sessionPermissions = permissions
    ) is AuthorizationDecision.GrantedBySession

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Manager authorization",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(16.dp))

        SettingsRow(
            title = "Set up manager authorization",
            subtitle = "Generate this account's override secret and share it with " +
                "staff devices. Needs owner approval.",
            onClick = onProvisionHotp
        )
        HorizontalDivider()
        SettingsRow(
            title = "Generate an override code",
            subtitle = "Read the code to a staff member who needs approval.",
            onClick = onGenerateOverrideCode
        )

        if (canConfigureStore) {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Allow time-limited approvals",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "When off, a manager's code authorises only the one " +
                            "action it was asked for. Turning this off also cancels " +
                            "any time-limited approval already in progress.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = elevationEnabled,
                    onCheckedChange = viewModel::setElevationTokensEnabled
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
