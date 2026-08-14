package app.kaup.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kaup.core.data.auth.PinAuthResult
import app.kaup.core.data.auth.PinAuthenticator
import app.kaup.core.data.auth.SessionManager
import app.kaup.core.data.dao.UserDao
import app.kaup.core.data.entities.UserEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the PIN entry screen is allowed to show about the last attempt. */
sealed interface PinEntryState {
    data object Idle : PinEntryState
    data object Checking : PinEntryState
    data class Incorrect(val attemptsBeforeLockout: Int) : PinEntryState
    data class LockedOut(val remainingMillis: Long) : PinEntryState
}

@HiltViewModel
class LockScreenViewModel @Inject constructor(
    userDao: UserDao,
    private val pinAuthenticator: PinAuthenticator,
    private val sessionManager: SessionManager
) : ViewModel() {

    val users: StateFlow<List<UserEntity>> = userDao.getAllUsers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _pinEntryState = MutableStateFlow<PinEntryState>(PinEntryState.Idle)
    val pinEntryState: StateFlow<PinEntryState> = _pinEntryState.asStateFlow()

    /**
     * Verifies the PIN and starts the session on success.
     *
     * The screen used to compare the typed digits with the stored column
     * itself. Verification lives behind [PinAuthenticator] now, so the hash
     * never reaches the UI layer and no caller can skip the attempt counter.
     */
    fun submitPin(user: UserEntity, pin: String, onSuccess: () -> Unit) {
        if (_pinEntryState.value == PinEntryState.Checking) return
        _pinEntryState.value = PinEntryState.Checking
        viewModelScope.launch {
            when (val result = pinAuthenticator.authenticate(user.id, pin)) {
                is PinAuthResult.Success -> {
                    sessionManager.login(result.user)
                    _pinEntryState.value = PinEntryState.Idle
                    onSuccess()
                }
                is PinAuthResult.Failure -> {
                    _pinEntryState.value = PinEntryState.Incorrect(result.attemptsBeforeLockout)
                }
                is PinAuthResult.LockedOut -> {
                    _pinEntryState.value = PinEntryState.LockedOut(result.remainingMillis)
                }
            }
        }
    }

    fun clearPinEntryState() {
        _pinEntryState.value = PinEntryState.Idle
    }
}
