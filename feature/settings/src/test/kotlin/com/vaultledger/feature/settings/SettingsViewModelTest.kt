package com.vaultledger.feature.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

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
    fun `signOut clears auth state`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(repository)

        vm.signOut()
        advanceUntilIdle()

        val authState = repository.observeAuthState().first()
        assertNull(authState)
    }

    @Test
    fun `signOut does not throw when repository errors`() = runTest(testDispatcher) {
        repository.throwOnSignOut = true
        val vm = SettingsViewModel(repository)

        vm.signOut()
        advanceUntilIdle()

        // Should not throw — sign out errors are non-blocking
        // Simply verifying the operation completes without exception
    }
}
