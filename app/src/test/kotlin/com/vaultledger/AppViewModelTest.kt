package com.vaultledger

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: FakeAuthRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = FakeAuthRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `isAuthenticated starts as null`() = runTest(testDispatcher) {
        val vm = AppViewModel(authRepository)

        assertNull(vm.isAuthenticated.value)
    }

    @Test
    fun `isAuthenticated becomes true when user signs in`() = runTest(testDispatcher) {
        val vm = AppViewModel(authRepository)

        authRepository.signIn("test@example.com", "password123")
        advanceUntilIdle()

        assertTrue(vm.isAuthenticated.value == true)
    }

    @Test
    fun `isAuthenticated becomes false when user signs out`() = runTest(testDispatcher) {
        authRepository.signIn("test@example.com", "password123")
        advanceUntilIdle()

        val vm = AppViewModel(authRepository)
        advanceUntilIdle()
        assertTrue(vm.isAuthenticated.value == true)

        authRepository.signOut()
        advanceUntilIdle()

        assertTrue(vm.isAuthenticated.value == false)
    }

    @Test
    fun `isAuthenticated stays null when no auth state emission`() = runTest(testDispatcher) {
        val vm = AppViewModel(authRepository)

        assertNull(vm.isAuthenticated.value)
    }
}
