package com.vaultledger.ui.screen

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeAuthRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeAuthRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init has login mode as default`() = runTest(testDispatcher) {
        val vm = AuthViewModel(repository)

        assertTrue(vm.state.value.isLoginMode)
        assertEquals("", vm.state.value.email)
        assertEquals("", vm.state.value.password)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `onEmailChange updates email and clears errors`() = runTest(testDispatcher) {
        val vm = AuthViewModel(repository)

        vm.onEmailChange("test@example.com")

        assertEquals("test@example.com", vm.state.value.email)
        assertNull(vm.state.value.emailError)
    }

    @Test
    fun `onPasswordChange updates password and clears errors`() = runTest(testDispatcher) {
        val vm = AuthViewModel(repository)

        vm.onPasswordChange("password123")

        assertEquals("password123", vm.state.value.password)
        assertNull(vm.state.value.passwordError)
    }

    @Test
    fun `toggleMode switches between login and register`() = runTest(testDispatcher) {
        val vm = AuthViewModel(repository)

        assertTrue(vm.state.value.isLoginMode)

        vm.toggleMode()
        assertFalse(vm.state.value.isLoginMode)

        vm.toggleMode()
        assertTrue(vm.state.value.isLoginMode)
    }

    @Test
    fun `toggleMode clears errors`() = runTest(testDispatcher) {
        val vm = AuthViewModel(repository)

        vm.onEmailChange("")
        vm.signIn()
        advanceUntilIdle()
        assertTrue(vm.state.value.emailError != null)

        vm.toggleMode()
        assertNull(vm.state.value.emailError)
    }

    @Test
    fun `signIn succeeds with valid credentials`() = runTest(testDispatcher) {
        repository.signUp("test@example.com", "password123")
        advanceUntilIdle()
        repository.signOut()
        advanceUntilIdle()

        val vm = AuthViewModel(repository)

        vm.onEmailChange("test@example.com")
        vm.onPasswordChange("password123")
        vm.signIn()
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `signIn fails with wrong password`() = runTest(testDispatcher) {
        repository.signUp("test@example.com", "password123")
        advanceUntilIdle()
        repository.signOut()
        advanceUntilIdle()

        val vm = AuthViewModel(repository)

        vm.onEmailChange("test@example.com")
        vm.onPasswordChange("wrongpassword")
        vm.signIn()
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.error?.contains("Invalid email or password") == true)
    }

    @Test
    fun `signIn shows validation error for empty email`() = runTest(testDispatcher) {
        val vm = AuthViewModel(repository)

        vm.onPasswordChange("password123")
        vm.signIn()
        advanceUntilIdle()

        assertEquals("Email is required", vm.state.value.emailError)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `signIn shows validation error for short password`() = runTest(testDispatcher) {
        val vm = AuthViewModel(repository)

        vm.onEmailChange("test@example.com")
        vm.onPasswordChange("123")
        vm.signIn()
        advanceUntilIdle()

        assertEquals("Password must be at least 6 characters", vm.state.value.passwordError)
    }

    @Test
    fun `signIn shows validation error for invalid email format`() = runTest(testDispatcher) {
        val vm = AuthViewModel(repository)

        vm.onEmailChange("notanemail")
        vm.onPasswordChange("password123")
        vm.signIn()
        advanceUntilIdle()

        assertEquals("Enter a valid email address", vm.state.value.emailError)
    }

    @Test
    fun `signUp succeeds with new credentials`() = runTest(testDispatcher) {
        val vm = AuthViewModel(repository)

        vm.toggleMode()
        vm.onEmailChange("new@example.com")
        vm.onPasswordChange("password123")
        vm.signUp()
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `signUp fails with duplicate email`() = runTest(testDispatcher) {
        repository.signUp("existing@example.com", "password123")
        advanceUntilIdle()

        val vm = AuthViewModel(repository)

        vm.toggleMode()
        vm.onEmailChange("existing@example.com")
        vm.onPasswordChange("password456")
        vm.signUp()
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertTrue(vm.state.value.error?.contains("already exists") == true)
    }

    @Test
    fun `signUp shows validation error for empty password`() = runTest(testDispatcher) {
        val vm = AuthViewModel(repository)

        vm.toggleMode()
        vm.onEmailChange("test@example.com")
        vm.signUp()
        advanceUntilIdle()

        assertEquals("Password is required", vm.state.value.passwordError)
    }

    @Test
    fun `signIn shows error when repository throws`() = runTest(testDispatcher) {
        repository.throwOnSignIn = true
        repository.signInError = "Network error"

        val vm = AuthViewModel(repository)

        vm.onEmailChange("test@example.com")
        vm.onPasswordChange("password123")
        vm.signIn()
        advanceUntilIdle()

        assertEquals("Network error", vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `signUp shows error when repository throws`() = runTest(testDispatcher) {
        repository.throwOnSignUp = true
        repository.signUpError = "Registration failed"

        val vm = AuthViewModel(repository)

        vm.toggleMode()
        vm.onEmailChange("test@example.com")
        vm.onPasswordChange("password123")
        vm.signUp()
        advanceUntilIdle()

        assertEquals("Registration failed", vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `retry calls signIn when in login mode`() = runTest(testDispatcher) {
        repository.throwOnSignIn = true
        repository.signInError = "Network error"

        val vm = AuthViewModel(repository)
        vm.onEmailChange("test@example.com")
        vm.onPasswordChange("password123")
        vm.signIn()
        advanceUntilIdle()
        assertEquals("Network error", vm.state.value.error)

        repository.throwOnSignIn = false
        repository.signUp("test@example.com", "password123")
        advanceUntilIdle()
        repository.signOut()
        advanceUntilIdle()

        vm.retry()
        advanceUntilIdle()

        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `retry calls signUp when in register mode`() = runTest(testDispatcher) {
        repository.throwOnSignUp = true
        repository.signUpError = "Registration failed"

        val vm = AuthViewModel(repository)
        vm.toggleMode()
        vm.onEmailChange("test@example.com")
        vm.onPasswordChange("password123")
        vm.signUp()
        advanceUntilIdle()
        assertEquals("Registration failed", vm.state.value.error)

        repository.throwOnSignUp = false

        vm.retry()
        advanceUntilIdle()

        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `duplicate signIn call does not create duplicate requests`() = runTest(testDispatcher) {
        repository.throwOnSignIn = true
        repository.signInError = "Network error"

        val vm = AuthViewModel(repository)
        vm.onEmailChange("test@example.com")
        vm.onPasswordChange("password123")

        vm.signIn()
        vm.signIn()
        vm.signIn()
        advanceUntilIdle()

        assertEquals("Network error", vm.state.value.error)
    }

    @Test
    fun `error clears when email changes after failed signIn`() = runTest(testDispatcher) {
        repository.throwOnSignIn = true
        repository.signInError = "Network error"

        val vm = AuthViewModel(repository)
        vm.onEmailChange("test@example.com")
        vm.onPasswordChange("password123")
        vm.signIn()
        advanceUntilIdle()

        assertEquals("Network error", vm.state.value.error)

        vm.onEmailChange("new@example.com")
        assertNull(vm.state.value.error)
    }

    @Test
    fun `error clears when password changes after failed signIn`() = runTest(testDispatcher) {
        repository.throwOnSignIn = true
        repository.signInError = "Network error"

        val vm = AuthViewModel(repository)
        vm.onEmailChange("test@example.com")
        vm.onPasswordChange("password123")
        vm.signIn()
        advanceUntilIdle()

        assertEquals("Network error", vm.state.value.error)

        vm.onPasswordChange("newpassword")
        assertNull(vm.state.value.error)
    }

    @Test
    fun `validation errors disappear after field correction`() = runTest(testDispatcher) {
        val vm = AuthViewModel(repository)

        vm.signIn()
        advanceUntilIdle()
        assertEquals("Email is required", vm.state.value.emailError)

        vm.onEmailChange("test@example.com")
        assertNull(vm.state.value.emailError)
    }

    @Test
    fun `isLoading is true during signIn`() = runTest(testDispatcher) {
        val vm = AuthViewModel(repository)
        vm.onEmailChange("test@example.com")
        vm.onPasswordChange("password123")

        vm.signIn()
        assertTrue(vm.state.value.isLoading)
        advanceUntilIdle()
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `isLoading is true during signUp`() = runTest(testDispatcher) {
        val vm = AuthViewModel(repository)
        vm.toggleMode()
        vm.onEmailChange("new@example.com")
        vm.onPasswordChange("password123")

        vm.signUp()
        assertTrue(vm.state.value.isLoading)
        advanceUntilIdle()
        assertFalse(vm.state.value.isLoading)
    }

}
