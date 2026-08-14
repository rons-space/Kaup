package app.kaup.feature.auth.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kaup.core.data.auth.PinAuthenticator
import app.kaup.core.data.dao.LocationDao
import app.kaup.core.data.dao.UserDao
import app.kaup.core.data.entities.LocationEntity
import app.kaup.core.data.entities.UserEntity
import app.kaup.core.data.preferences.StorePreferences
import app.kaup.shared.domain.auth.PinPolicy
import app.kaup.shared.domain.models.auth.Role
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val currentStep: Int = 1, // 1: Store, 2: Owner, 3: Success
    val storeName: String = "",
    val currency: String = "USD",
    val ownerName: String = "",
    val ownerPin: String = "",
    val isCompleting: Boolean = false,
    val isSuccess: Boolean = false
) {
    val isStep1Valid: Boolean
        get() = storeName.isNotBlank() && currency.isNotBlank()

    // PinPolicy is the single source of truth shared with the lock screen. A
    // length this screen accepts but the lock screen cannot enter locks the
    // owner out of their own store.
    val isStep2Valid: Boolean
        get() = ownerName.isNotBlank() && PinPolicy.isValidNewPin(ownerPin)
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userDao: UserDao,
    private val locationDao: LocationDao,
    private val pinAuthenticator: PinAuthenticator,
    private val storePreferences: StorePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun updateStoreName(name: String) {
        _uiState.update { it.copy(storeName = name) }
    }

    fun updateCurrency(currency: String) {
        _uiState.update { it.copy(currency = currency) }
    }

    fun updateOwnerName(name: String) {
        _uiState.update { it.copy(ownerName = name) }
    }

    fun updateOwnerPin(pin: String) {
        if (pin.all { it.isDigit() } && pin.length <= PinPolicy.NEW_PIN_LENGTH) {
            _uiState.update { it.copy(ownerPin = pin) }
        }
    }

    fun nextStep() {
        val currentState = _uiState.value
        if (currentState.currentStep == 1 && currentState.isStep1Valid) {
            _uiState.update { it.copy(currentStep = 2) }
        } else if (currentState.currentStep == 2 && currentState.isStep2Valid) {
            completeOnboarding()
        }
    }

    fun previousStep() {
        val currentState = _uiState.value
        if (currentState.currentStep > 1) {
            _uiState.update { it.copy(currentStep = currentState.currentStep - 1) }
        }
    }

    private fun completeOnboarding() {
        _uiState.update { it.copy(isCompleting = true) }
        viewModelScope.launch {
            val state = _uiState.value
            
            // 1. Save global settings
            storePreferences.saveStoreSetup(
                name = state.storeName,
                currency = state.currency
            )
            
            // 2. Create the first OWNER user.
            // Shift to IO to avoid blocking the main thread, and because
            // deriving the PIN hash is deliberately expensive.
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val credential = pinAuthenticator.newCredential(state.ownerPin)
                val owner = UserEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    name = state.ownerName,
                    role = Role.OWNER,
                    // Seeded by the database callback before anything else can
                    // be written, so this is present on a fresh install.
                    locationId = locationDao.getDefaultLocationOnce()?.id
                        ?: LocationEntity.DEFAULT_ID,
                    pinHash = credential.hash,
                    pinSalt = credential.salt,
                    pinIterations = credential.iterations
                )
                userDao.insertUser(owner)
            }
            
            // 3. Mark as success
            _uiState.update { it.copy(isCompleting = false, isSuccess = true, currentStep = 3) }
        }
    }
}
