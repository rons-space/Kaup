package app.kaup.feature.auth.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.kaup.shared.domain.auth.PinPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onOnboardingComplete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Welcome to Kaup") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Simple Progress Indicator
            LinearProgressIndicator(
                progress = { uiState.currentStep / 3f },
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
            )

            AnimatedContent(
                targetState = uiState.currentStep,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "WizardSteps"
            ) { step ->
                when (step) {
                    1 -> StoreSetupStep(uiState, viewModel)
                    2 -> OwnerSetupStep(uiState, viewModel)
                    3 -> SuccessStep(onOnboardingComplete)
                }
            }
        }
    }
}

@Composable
private fun StoreSetupStep(uiState: OnboardingUiState, viewModel: OnboardingViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Step 1: Store Setup",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Let's start by naming your store and setting your local currency.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.storeName,
            onValueChange = { viewModel.updateStoreName(it) },
            label = { Text("Store Name") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = uiState.currency,
            onValueChange = { viewModel.updateCurrency(it) },
            label = { Text("Currency Code (e.g. USD, EUR, GBP)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { viewModel.nextStep() },
            enabled = uiState.isStep1Valid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Next")
        }
    }
}

@Composable
private fun OwnerSetupStep(uiState: OnboardingUiState, viewModel: OnboardingViewModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Step 2: Owner Account",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Create the first owner account. This PIN will be required to log into the POS.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.ownerName,
            onValueChange = { viewModel.updateOwnerName(it) },
            label = { Text("Your Name") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = uiState.ownerPin,
            onValueChange = { viewModel.updateOwnerPin(it) },
            label = { Text("${PinPolicy.NEW_PIN_LENGTH}-Digit PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.weight(1f))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = { viewModel.previousStep() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }
            Button(
                onClick = { viewModel.nextStep() },
                enabled = uiState.isStep2Valid && !uiState.isCompleting,
                modifier = Modifier.weight(1f)
            ) {
                if (uiState.isCompleting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Complete Setup")
                }
            }
        }
    }
}

@Composable
private fun SuccessStep(onComplete: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Success",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(100.dp).padding(bottom = 24.dp)
        )
        Text(
            text = "You're All Set!",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Your store is ready. Let's ring up some sales.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Go to POS")
        }
    }
}
