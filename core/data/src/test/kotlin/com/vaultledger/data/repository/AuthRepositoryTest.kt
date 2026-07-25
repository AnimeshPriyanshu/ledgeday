package com.vaultledger.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {

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
    fun `observeAuthState emits null when not signed in`() = runTest(testDispatcher) {
        val user = repository.observeAuthState().first()
        assertNull(user)
    }

    @Test
    fun `observeAuthState emits user after signUp`() = runTest(testDispatcher) {
        repository.signUp("test@example.com", "password123")
        advanceUntilIdle()

        val user = repository.observeAuthState().first()
        assertEquals("test@example.com", user?.email)
    }

    @Test
    fun `signIn succeeds with valid credentials`() = runTest(testDispatcher) {
        repository.signUp("test@example.com", "password123")
        advanceUntilIdle()
        repository.signOut()
        advanceUntilIdle()

        repository.signIn("test@example.com", "password123")
        advanceUntilIdle()

        val user = repository.observeAuthState().first()
        assertEquals("test@example.com", user?.email)
    }

    @Test
    fun `signIn fails with wrong password`() = runTest(testDispatcher) {
        repository.signUp("test@example.com", "password123")
        advanceUntilIdle()
        repository.signOut()
        advanceUntilIdle()

        try {
            repository.signIn("test@example.com", "wrongpassword")
            advanceUntilIdle()
            throw AssertionError("Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertEquals("Invalid email or password.", e.message)
        }
    }

    @Test
    fun `signIn fails with non-existent email`() = runTest(testDispatcher) {
        try {
            repository.signIn("nonexistent@example.com", "password123")
            advanceUntilIdle()
            throw AssertionError("Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertEquals("Invalid email or password.", e.message)
        }
    }

    @Test
    fun `signUp succeeds with new email`() = runTest(testDispatcher) {
        repository.signUp("new@example.com", "password123")
        advanceUntilIdle()

        val user = repository.observeAuthState().first()
        assertEquals("new@example.com", user?.email)
    }

    @Test
    fun `signUp fails with duplicate email`() = runTest(testDispatcher) {
        repository.signUp("test@example.com", "password123")
        advanceUntilIdle()

        try {
            repository.signUp("test@example.com", "password456")
            advanceUntilIdle()
            throw AssertionError("Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertEquals("An account with this email already exists.", e.message)
        }
    }

    @Test
    fun `signOut clears current user`() = runTest(testDispatcher) {
        repository.signUp("test@example.com", "password123")
        advanceUntilIdle()

        repository.signOut()
        advanceUntilIdle()

        val user = repository.observeAuthState().first()
        assertNull(user)
    }

    @Test
    fun `signIn emits error when repository throws`() = runTest(testDispatcher) {
        repository.throwOnSignIn = true
        repository.signInError = "Network error"

        try {
            repository.signIn("test@example.com", "password123")
            advanceUntilIdle()
            throw AssertionError("Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertEquals("Network error", e.message)
        }
    }

    @Test
    fun `signUp emits error when repository throws`() = runTest(testDispatcher) {
        repository.throwOnSignUp = true
        repository.signUpError = "Registration failed"

        try {
            repository.signUp("test@example.com", "password123")
            advanceUntilIdle()
            throw AssertionError("Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertEquals("Registration failed", e.message)
        }
    }

    @Test
    fun `observeAuthState reflects state after multiple signIns`() = runTest(testDispatcher) {
        repository.signUp("user1@example.com", "pass1")
        advanceUntilIdle()
        repository.signOut()
        advanceUntilIdle()
        repository.signUp("user2@example.com", "pass2")
        advanceUntilIdle()

        val user = repository.observeAuthState().first()
        assertEquals("user2@example.com", user?.email)
    }

    @Test
    fun `signIn after signOut restores auth state`() = runTest(testDispatcher) {
        repository.signUp("test@example.com", "password123")
        advanceUntilIdle()
        repository.signOut()
        advanceUntilIdle()
        repository.signIn("test@example.com", "password123")
        advanceUntilIdle()

        val user = repository.observeAuthState().first()
        assertEquals("test@example.com", user?.email)
    }
}
