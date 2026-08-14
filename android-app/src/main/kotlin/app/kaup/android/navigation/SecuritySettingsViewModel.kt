package app.kaup.android.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kaup.core.data.auth.SessionManager
import app.kaup.core.data.preferences.StorePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    private val storePreferences: StorePreferences,
    private val sessionManager: SessionManager
) : ViewModel() {

    val elevationTokensEnabled: StateFlow<Boolean> = storePreferences.elevationTokensEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true
        )

    fun setElevationTokensEnabled(enabled: Boolean) {
        viewModelScope.launch {
            storePreferences.setElevationTokensEnabled(enabled)
            if (!enabled) {
                // Switching the feature off has to take effect now, not
                // whenever the outstanding token happens to lapse. The
                // redemption check in ElevationTokenPolicy already refuses it,
                // and this drops it so the UI stops claiming it is live.
                sessionManager.clearElevation()
            }
        }
    }
}
