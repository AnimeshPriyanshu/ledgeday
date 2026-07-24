package com.vaultledger.feature.workspace

import app.cash.turbine.test
import com.vaultledger.domain.model.Workspace
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
class WorkspaceListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeWorkspaceRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeWorkspaceRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init emits Loading then Empty when no workspaces`() = runTest(testDispatcher) {
        val vm = WorkspaceListViewModel(repository)
        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            assertEquals(UiState.Empty, awaitItem())
            cancel()
        }
    }

    @Test
    fun `init emits Loading then Success when workspaces exist`() = runTest(testDispatcher) {
        repository.createWorkspace("Test", "")
        val vm = WorkspaceListViewModel(repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            val success = awaitItem()
            assertInstanceOf(UiState.Success::class.java, success)
            val data = (success as UiState.Success<*>).data as List<*>
            assertEquals(1, data.size)
            assertEquals("Test", (data[0] as Workspace).name)
            cancel()
        }
    }

    @Test
    fun `createWorkspace adds workspace to list`() = runTest(testDispatcher) {
        val vm = WorkspaceListViewModel(repository)

        vm.uiState.test {
            skipItems(2)

            vm.createWorkspace("New Workspace")
            advanceUntilIdle()

            val state = awaitItem()
            assertInstanceOf(UiState.Success::class.java, state)
            val data = (state as UiState.Success<*>).data as List<*>
            assertTrue(data.any { (it as? Workspace)?.name == "New Workspace" })
            cancel()
        }
    }

    @Test
    fun `deleteWorkspace removes workspace from list`() = runTest(testDispatcher) {
        val created = repository.createWorkspace("To Delete", "")
        val vm = WorkspaceListViewModel(repository)

        vm.uiState.test {
            skipItems(2)

            vm.deleteWorkspace(created.id)
            advanceUntilIdle()

            assertEquals(UiState.Empty, awaitItem())
            cancel()
        }
    }

    @Test
    fun `init emits Loading then Error when repository throws`() = runTest(testDispatcher) {
        repository.throwOnGetAll = true
        val vm = WorkspaceListViewModel(repository)

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
    fun `uiState transitions Loading to Empty to Success after create`() = runTest(testDispatcher) {
        val vm = WorkspaceListViewModel(repository)
        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            assertEquals(UiState.Empty, awaitItem())

            vm.createWorkspace("First")
            advanceUntilIdle()

            val success = awaitItem()
            assertInstanceOf(UiState.Success::class.java, success)
            val data = (success as UiState.Success<*>).data as List<*>
            assertEquals(1, data.size)
            cancel()
        }
    }

    @Test
    fun `retry after error transitions to Loading then Success`() = runTest(testDispatcher) {
        repository.throwOnGetAll = true
        val vm = WorkspaceListViewModel(repository)

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
        repository.createWorkspace("Existing", "")
        repository.throwOnGetAll = true
        val vm = WorkspaceListViewModel(repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())

            val error = awaitItem()
            assertInstanceOf(UiState.Error::class.java, error)

            repository.throwOnGetAll = false
            vm.retry()

            assertEquals(UiState.Loading, awaitItem())
            val success = awaitItem()
            assertInstanceOf(UiState.Success::class.java, success)
            val data = (success as UiState.Success<*>).data as List<*>
            assertEquals(1, data.size)
            assertEquals("Existing", (data[0] as Workspace).name)
            cancel()
        }
    }

    @Test
    fun `Loading emitted before retry when currently in Error`() = runTest(testDispatcher) {
        repository.throwOnGetAll = true
        val vm = WorkspaceListViewModel(repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())

            val error = awaitItem()
            assertInstanceOf(UiState.Error::class.java, error)

            repository.throwOnGetAll = false
            vm.retry()

            val afterRetry = awaitItem()
            assertEquals(UiState.Loading, afterRetry)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple retry calls do not cause duplicate emissions`() = runTest(testDispatcher) {
        repository.throwOnGetAll = true
        val vm = WorkspaceListViewModel(repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())

            val error = awaitItem()
            assertInstanceOf(UiState.Error::class.java, error)

            repository.throwOnGetAll = false
            vm.retry()
            vm.retry()
            vm.retry()

            assertEquals(UiState.Loading, awaitItem())
            assertEquals(UiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
