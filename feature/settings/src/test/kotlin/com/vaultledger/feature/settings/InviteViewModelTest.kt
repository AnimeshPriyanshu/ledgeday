package com.vaultledger.feature.settings

import app.cash.turbine.test
import com.vaultledger.domain.usecase.GenerateInviteUseCase
import com.vaultledger.domain.usecase.RevokeInviteUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InviteViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeInviteRepository
    private lateinit var generateUseCase: GenerateInviteUseCase
    private lateinit var revokeUseCase: RevokeInviteUseCase

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeInviteRepository()
        generateUseCase = GenerateInviteUseCase(repository)
        revokeUseCase = RevokeInviteUseCase(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle`() = runTest(testDispatcher) {
        val vm = InviteViewModel(generateUseCase, revokeUseCase)
        assertInstanceOf(InviteUiState.Idle::class.java, vm.state.value)
    }



    @Test
    fun `generate transitions to Generating then Generated`() = runTest(testDispatcher) {
        val vm = InviteViewModel(generateUseCase, revokeUseCase)

        vm.state.test {
            assertInstanceOf(InviteUiState.Idle::class.java, awaitItem())
            vm.generateInvite()
            assertInstanceOf(InviteUiState.Generating::class.java, awaitItem())
            val generated = awaitItem()
            assertInstanceOf(InviteUiState.Generated::class.java, generated)
            cancel()
        }
    }

    @Test
    fun `generate transitions to Error when repository throws`() = runTest(testDispatcher) {
        repository.throwOnCreate = true
        val vm = InviteViewModel(generateUseCase, revokeUseCase)

        vm.state.test {
            assertInstanceOf(InviteUiState.Idle::class.java, awaitItem())
            vm.generateInvite()
            assertInstanceOf(InviteUiState.Generating::class.java, awaitItem())
            val error = awaitItem()
            assertInstanceOf(InviteUiState.Error::class.java, error)
            cancel()
        }
    }

    @Test
    fun `generate transitions to Error with offline message when offline`() = runTest(testDispatcher) {
        repository.simulateOffline = true
        val vm = InviteViewModel(generateUseCase, revokeUseCase)

        vm.state.test {
            assertInstanceOf(InviteUiState.Idle::class.java, awaitItem())
            vm.generateInvite()
            assertInstanceOf(InviteUiState.Generating::class.java, awaitItem())
            val error = awaitItem() as InviteUiState.Error
            assertEquals("Internet connection required to generate an invite.", error.message)
            assertEquals(true, error.isRetryable)
            cancel()
        }
    }

    @Test
    fun `generate transitions to Error with timeout message when timeout`() = runTest(testDispatcher) {
        repository.simulateTimeout = true
        val vm = InviteViewModel(generateUseCase, revokeUseCase)

        vm.state.test {
            assertInstanceOf(InviteUiState.Idle::class.java, awaitItem())
            vm.generateInvite()
            assertInstanceOf(InviteUiState.Generating::class.java, awaitItem())
            val error = awaitItem() as InviteUiState.Error
            assertEquals("Unable to reach the server. Please try again.", error.message)
            assertEquals(true, error.isRetryable)
            cancel()
        }
    }

    @Test
    fun `revoke transitions to Revoked`() = runTest(testDispatcher) {
        val vm = InviteViewModel(generateUseCase, revokeUseCase)

        vm.generateInvite()
        advanceUntilIdle()

        val state = vm.state.value
        val code = (state as InviteUiState.Generated).invite.code

        vm.state.test {
            assertInstanceOf(InviteUiState.Generated::class.java, awaitItem())
            vm.revokeInvite(code)
            advanceUntilIdle()
            assertInstanceOf(InviteUiState.Revoked::class.java, awaitItem())
            cancel()
        }
    }

    @Test
    fun `reset returns to Idle after Generated`() = runTest(testDispatcher) {
        val vm = InviteViewModel(generateUseCase, revokeUseCase)

        vm.generateInvite()
        advanceUntilIdle()

        vm.reset()
        assertInstanceOf(InviteUiState.Idle::class.java, vm.state.value)
    }
}
