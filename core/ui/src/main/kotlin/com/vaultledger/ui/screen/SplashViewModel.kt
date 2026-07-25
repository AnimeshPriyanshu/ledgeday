package com.vaultledger.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultledger.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SplashState {
    data object Loading : SplashState
    data object Authenticated : SplashState
    data object Unauthenticated : SplashState
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SplashState>(SplashState.Loading)
    val state: StateFlow<SplashState> = _state.asStateFlow()

    init {
        observeAuthState()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            try {
                authRepository.observeAuthState().collect { user ->
                    _state.value = if (user != null) {
                        SplashState.Authenticated
                    } else {
                        SplashState.Unauthenticated
                    }
                }
            } catch (e: Exception) {
                _state.value = SplashState.Unauthenticated
            }
        }
    }
}
