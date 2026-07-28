package com.vaultledger.ui.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultledger.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isLoginMode: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAuthenticated: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private var failedAttempts = 0
    private var firstFailedTimestamp = 0L

    init {
        observeAuthState()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            try {
                authRepository.observeAuthState().collect { user ->
                    _state.update {
                        it.copy(isAuthenticated = user != null)
                    }
                }
            } catch (_: Exception) {
                // Auth state observation errors are handled by the splash screen
            }
        }
    }

    fun onEmailChange(email: String) {
        _state.update {
            it.copy(
                email = email,
                emailError = null,
                error = null,
            )
        }
    }

    fun onPasswordChange(password: String) {
        _state.update {
            it.copy(
                password = password,
                passwordError = null,
                error = null,
            )
        }
    }

    fun toggleMode() {
        _state.update {
            it.copy(
                isLoginMode = !it.isLoginMode,
                error = null,
                emailError = null,
                passwordError = null,
            )
        }
    }

    private fun checkRateLimit(): Boolean {
        val now = System.currentTimeMillis()
        if (now - firstFailedTimestamp > RATE_LIMIT_WINDOW_MS) {
            failedAttempts = 0
            firstFailedTimestamp = now
        }
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            _state.update { it.copy(error = "Too many attempts. Please try again later.") }
            return false
        }
        return true
    }

    private fun recordFailure() {
        if (failedAttempts == 0) firstFailedTimestamp = System.currentTimeMillis()
        failedAttempts++
    }

    fun signIn() {
        val current = _state.value
        if (!validate(current)) return
        if (current.isLoading) return
        if (!checkRateLimit()) return

        _state.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                authRepository.signIn(
                    email = current.email.trim(),
                    password = current.password,
                )
                failedAttempts = 0
                _state.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                recordFailure()
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Sign in failed",
                    )
                }
            }
        }
    }

    fun signUp() {
        val current = _state.value
        if (!validate(current)) return
        if (current.isLoading) return
        if (!checkRateLimit()) return

        _state.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                authRepository.signUp(
                    email = current.email.trim(),
                    password = current.password,
                )
                failedAttempts = 0
                _state.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                recordFailure()
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Registration failed",
                    )
                }
            }
        }
    }

    fun retry() {
        val current = _state.value
        if (current.isLoginMode) {
            signIn()
        } else {
            signUp()
        }
    }

    private fun validate(state: AuthUiState): Boolean {
        var valid = true

        val emailError = when {
            state.email.isBlank() -> {
                valid = false
                "Email is required"
            }
            !state.email.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) -> {
                valid = false
                "Enter a valid email address"
            }
            else -> null
        }

        val passwordError = when {
            state.password.isBlank() -> {
                valid = false
                "Password is required"
            }
            state.password.length < 6 -> {
                valid = false
                "Password must be at least 6 characters"
            }
            else -> null
        }

        _state.update {
            it.copy(
                emailError = emailError,
                passwordError = passwordError,
            )
        }

        return valid
    }

    companion object {
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val RATE_LIMIT_WINDOW_MS = 60_000L
    }
}
