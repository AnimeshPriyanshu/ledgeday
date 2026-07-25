package com.vaultledger.feature.settings

import app.cash.turbine.test
import com.vaultledger.domain.model.InviteStatus
import com.vaultledger.domain.usecase.AcceptInviteUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AcceptInviteViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeInviteRepository
    private lateinit var acceptUseCase: AcceptInviteUseCase

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeInviteRepository()
        acceptUseCase = AcceptInviteUseCase(repository)
        // Create a valid invite before each test
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun setupValidInvite(): String {
        val invite = repository.createInvite()
        return invite.code
    }

    @Test
    fun `initial state is Idle`() = runTest(testDispatcher) {
        val vm = AcceptInviteViewModel(acceptUseCase)
        assertInstanceOf(AcceptInviteUiState.Idle::class.java, vm.state.value)
    }

    @Test
    fun `accept transitions to Accepting then Success`() = runTest(testDispatcher) {
        val code = setupValidInvite()
        val vm = AcceptInviteViewModel(acceptUseCase)

        vm.state.test {
            assertInstanceOf(AcceptInviteUiState.Idle::class.java, awaitItem())
            vm.acceptInvite(code)
            assertInstanceOf(AcceptInviteUiState.Accepting::class.java, awaitItem())
            val success = awaitItem()
            assertInstanceOf(AcceptInviteUiState.Success::class.java, success)
            cancel()
        }
    }

    @Test
    fun `accept transitions to Error when repository throws`() = runTest(testDispatcher) {
        val code = setupValidInvite()
        repository.throwOnAccept = true
        val vm = AcceptInviteViewModel(acceptUseCase)

        vm.state.test {
            assertInstanceOf(AcceptInviteUiState.Idle::class.java, awaitItem())
            vm.acceptInvite(code)
            assertInstanceOf(AcceptInviteUiState.Accepting::class.java, awaitItem())
            val error = awaitItem()
            assertInstanceOf(AcceptInviteUiState.Error::class.java, error)
            cancel()
        }
    }

    @Test
    fun `accept with invalid code shows error`() = runTest(testDispatcher) {
        val vm = AcceptInviteViewModel(acceptUseCase)

        vm.state.test {
            assertInstanceOf(AcceptInviteUiState.Idle::class.java, awaitItem())
            vm.acceptInvite("INVALID")
            assertInstanceOf(AcceptInviteUiState.Accepting::class.java, awaitItem())
            val error = awaitItem()
            assertInstanceOf(AcceptInviteUiState.Error::class.java, error)
            cancel()
        }
    }

    @Test
    fun `reset returns to Idle after error`() = runTest(testDispatcher) {
        val vm = AcceptInviteViewModel(acceptUseCase)

        vm.acceptInvite("INVALID")
        vm.reset()
        assertInstanceOf(AcceptInviteUiState.Idle::class.java, vm.state.value)
    }
}
