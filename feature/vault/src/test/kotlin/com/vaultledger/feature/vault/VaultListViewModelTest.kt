package com.vaultledger.feature.vault

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.vaultledger.domain.model.Vault
import com.vaultledger.ui.common.UiState
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VaultListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val workspaceId = "test-workspace-id"
    private lateinit var repository: FakeVaultRepository
    private lateinit var savedStateHandle: SavedStateHandle

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeVaultRepository()
        savedStateHandle = SavedStateHandle(mapOf("workspaceId" to workspaceId))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init emits Loading then Empty when no vaults`() = runTest(testDispatcher) {
        val vm = VaultListViewModel(savedStateHandle, repository)
        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            assertEquals(UiState.Empty, awaitItem())
            cancel()
        }
    }

    @Test
    fun `init emits Loading then Success when vaults exist`() = runTest(testDispatcher) {
        repository.createVault(workspaceId, "Main", "", "#006D77")
        val vm = VaultListViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            val success = awaitItem()
            assertInstanceOf(UiState.Success::class.java, success)
            val data = (success as UiState.Success<*>).data as List<*>
            assertEquals(1, data.size)
            assertEquals("Main", (data[0] as Vault).name)
            cancel()
        }
    }

    @Test
    fun `vaults scoped to workspace`() = runTest(testDispatcher) {
        repository.createVault("other-workspace", "Other Vault", "", "#006D77")
        repository.createVault(workspaceId, "My Vault", "", "#006D77")
        val vm = VaultListViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            val success = awaitItem()
            assertInstanceOf(UiState.Success::class.java, success)
            val data = (success as UiState.Success<*>).data as List<*>
            assertEquals(1, data.size)
            assertEquals("My Vault", (data[0] as Vault).name)
            cancel()
        }
    }

    @Test
    fun `createVault adds vault to list`() = runTest(testDispatcher) {
        val vm = VaultListViewModel(savedStateHandle, repository)

        vm.uiState.test {
            skipItems(2)

            vm.createVault("New Vault", "My description", "#83C5BE")
            advanceUntilIdle()

            val state = awaitItem()
            assertInstanceOf(UiState.Success::class.java, state)
            val data = (state as UiState.Success<*>).data as List<*>
            assertEquals(1, data.size)
            val vault = data[0] as Vault
            assertEquals("New Vault", vault.name)
            assertEquals("My description", vault.description)
            assertEquals("#83C5BE", vault.color)
            cancel()
        }
    }

    @Test
    fun `createVault failure sets Error state`() = runTest(testDispatcher) {
        repository.throwOnCreate = true
        val vm = VaultListViewModel(savedStateHandle, repository)

        vm.uiState.test {
            skipItems(2)

            vm.createVault("Fail", "", "#006D77")
            advanceUntilIdle()

            val error = awaitItem()
            assertInstanceOf(UiState.Error::class.java, error)
            assertTrue((error as UiState.Error).message.contains("Failed to create"))
            cancel()
        }
    }

    @Test
    fun `deleteVault removes vault from list`() = runTest(testDispatcher) {
        val created = repository.createVault(workspaceId, "To Delete", "", "#006D77")
        val vm = VaultListViewModel(savedStateHandle, repository)

        vm.uiState.test {
            skipItems(2)

            vm.deleteVault(created.id)
            advanceUntilIdle()

            assertEquals(UiState.Empty, awaitItem())
            cancel()
        }
    }

    @Test
    fun `deleteVault failure sets Error state`() = runTest(testDispatcher) {
        repository.createVault(workspaceId, "To Delete", "", "#006D77")
        repository.throwOnDelete = true
        val vm = VaultListViewModel(savedStateHandle, repository)

        vm.uiState.test {
            skipItems(2)

            vm.deleteVault("non-existent")
            advanceUntilIdle()

            val error = awaitItem()
            assertInstanceOf(UiState.Error::class.java, error)
            assertTrue((error as UiState.Error).message.contains("Failed to delete"))
            cancel()
        }
    }

    @Test
    fun `init emits Loading then Error when repository throws`() = runTest(testDispatcher) {
        repository.throwOnGetAll = true
        val vm = VaultListViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            val error = awaitItem()
            assertInstanceOf(UiState.Error::class.java, error)
            val errorState = error as UiState.Error
            assertTrue(errorState.message.contains("Failed to load"))
            cancel()
        }
    }

    @Test
    fun `retry after error transitions to Loading then Success`() = runTest(testDispatcher) {
        repository.throwOnGetAll = true
        val vm = VaultListViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            val error = awaitItem()
            assertInstanceOf(UiState.Error::class.java, error)

            repository.throwOnGetAll = false
            vm.retry()

            assertEquals(UiState.Loading, awaitItem())
            assertEquals(UiState.Empty, awaitItem())
            cancel()
        }
    }

    @Test
    fun `retry after error with data transitions to Loading then Success`() = runTest(testDispatcher) {
        repository.createVault(workspaceId, "Existing", "", "#006D77")
        repository.throwOnGetAll = true
        val vm = VaultListViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            val error = awaitItem()
            assertInstanceOf(UiState.Error::class.java, error)

            repository.throwOnGetAll = false
            vm.retry()

            assertEquals(UiState.Loading, awaitItem())
            val success = awaitItem()
            assertInstanceOf(UiState.Success::class.java, success)
            val data = ((success as UiState.Success<*>).data as List<*>)
            assertEquals(1, data.size)
            assertEquals("Existing", (data[0] as Vault).name)
            cancel()
        }
    }

    @Test
    fun `Loading emitted before retry when currently in Error`() = runTest(testDispatcher) {
        repository.throwOnGetAll = true
        val vm = VaultListViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            assertInstanceOf(UiState.Error::class.java, awaitItem())

            repository.throwOnGetAll = false
            vm.retry()

            assertEquals(UiState.Loading, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple retry calls do not cause duplicate emissions`() = runTest(testDispatcher) {
        repository.throwOnGetAll = true
        val vm = VaultListViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            assertInstanceOf(UiState.Error::class.java, awaitItem())

            repository.throwOnGetAll = false
            vm.retry()
            vm.retry()
            vm.retry()

            assertEquals(UiState.Loading, awaitItem())
            assertEquals(UiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reactive Flow updates when vault added externally`() = runTest(testDispatcher) {
        val vm = VaultListViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            assertEquals(UiState.Empty, awaitItem())

            repository.createVault(workspaceId, "External", "", "#E29578")
            advanceUntilIdle()

            val success = awaitItem()
            assertInstanceOf(UiState.Success::class.java, success)
            val data = ((success as UiState.Success<*>).data as List<*>)
            assertEquals(1, data.size)
            assertEquals("External", (data[0] as Vault).name)
            cancel()
        }
    }
}
