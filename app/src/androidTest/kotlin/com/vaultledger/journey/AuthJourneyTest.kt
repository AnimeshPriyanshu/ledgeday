package com.vaultledger.journey

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.vaultledger.domain.model.User
import com.vaultledger.domain.repository.AuthRepository
import com.vaultledger.ui.screen.AuthScreen
import com.vaultledger.ui.screen.AuthViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class AuthJourneyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var authRepo: FakeAuthRepository

    @Before
    fun setUp() {
        authRepo = FakeAuthRepository()
    }

    @Test
    fun authScreen_showsLoginForm() {
        val viewModel = AuthViewModel(authRepo)

        composeTestRule.setContent {
            AuthScreen(
                onNavigateToWorkspaces = {},
                viewModel = viewModel,
            )
        }

        composeTestRule.onNodeWithText("Vault Ledger").assertIsDisplayed()
        composeTestRule.onNodeWithText("Welcome back").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign In").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign Up").assertIsDisplayed()
    }

    @Test
    fun authScreen_showsRegisterFormAfterToggle() {
        val viewModel = AuthViewModel(authRepo)

        composeTestRule.setContent {
            AuthScreen(
                onNavigateToWorkspaces = {},
                viewModel = viewModel,
            )
        }

        composeTestRule.onNodeWithText("Sign Up").performClick()
        composeTestRule.onNodeWithText("Create your account").assertIsDisplayed()
    }

    @Test
    fun authScreen_showsValidationErrors() {
        val viewModel = AuthViewModel(authRepo)

        composeTestRule.setContent {
            AuthScreen(
                onNavigateToWorkspaces = {},
                viewModel = viewModel,
            )
        }

        composeTestRule.onNodeWithText("Sign In").performClick()
        composeTestRule.onNodeWithText("Email is required").assertIsDisplayed()
    }
}

class FakeAuthRepository : AuthRepository {

    private val _currentUser = MutableStateFlow<User?>(null)
    private val registeredUsers = mutableMapOf<String, RegisteredUser>()

    data class RegisteredUser(
        val email: String,
        val password: String,
        val id: String = UUID.randomUUID().toString(),
    )

    override fun observeAuthState(): Flow<User?> = _currentUser

    override suspend fun signIn(email: String, password: String) {
        val found = registeredUsers.values.find { it.email == email }
            ?: throw RuntimeException("Invalid email or password.")
        if (found.password != password) {
            throw RuntimeException("Invalid email or password.")
        }
        _currentUser.value = User(id = found.id, email = found.email)
    }

    override suspend fun signUp(email: String, password: String) {
        if (registeredUsers.values.any { it.email == email }) {
            throw RuntimeException("An account with this email already exists.")
        }
        val user = RegisteredUser(email = email, password = password)
        registeredUsers[user.id] = user
        _currentUser.value = User(id = user.id, email = user.email)
    }

    override suspend fun signOut() {
        _currentUser.value = null
    }
}
