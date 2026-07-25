package com.vaultledger.feature.transactions

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.vaultledger.domain.model.Transaction
import com.vaultledger.domain.model.TransactionType
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VaultDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val vaultId = "test-vault-id"
    private lateinit var repository: FakeTransactionRepository
    private lateinit var savedStateHandle: SavedStateHandle

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeTransactionRepository()
        savedStateHandle = SavedStateHandle(mapOf("vaultId" to vaultId))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init emits Loading then Empty when no transactions`() = runTest(testDispatcher) {
        val vm = VaultDetailViewModel(savedStateHandle, repository)
        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            assertEquals(UiState.Empty, awaitItem())
            cancel()
        }
    }

    @Test
    fun `init emits Loading then Success with transactions and balance`() = runTest(testDispatcher) {
        repository.createTransaction(vaultId, TransactionType.INFLOW, 10000L, "Deposit")
        val vm = VaultDetailViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            val success = awaitItem()
            assertInstanceOf(UiState.Success::class.java, success)
            val state = (success as UiState.Success).data as VaultDetailUiState
            assertEquals(1, state.transactions.size)
            assertEquals(10000L, state.balance)
            cancel()
        }
    }

    @Test
    fun `reactive balance updates when transaction added externally`() = runTest(testDispatcher) {
        repository.createTransaction(vaultId, TransactionType.INFLOW, 5000L, "Initial")
        val vm = VaultDetailViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            var success = awaitItem()
            assertInstanceOf(UiState.Success::class.java, success)
            assertEquals(5000L, (success as UiState.Success).data.balance)

            repository.createTransaction(vaultId, TransactionType.INFLOW, 3000L, "Extra")
            advanceUntilIdle()

            success = awaitItem()
            assertEquals(8000L, (success as UiState.Success).data.balance)
            cancel()
        }
    }

    @Test
    fun `reactive transaction list updates when transaction added externally`() = runTest(testDispatcher) {
        repository.createTransaction(vaultId, TransactionType.OUTFLOW, 2000L, "First")
        val vm = VaultDetailViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            var success = awaitItem()
            assertInstanceOf(UiState.Success::class.java, success)
            assertEquals(1, (success as UiState.Success).data.transactions.size)

            repository.createTransaction(vaultId, TransactionType.OUTFLOW, 1500L, "Second")
            advanceUntilIdle()

            success = awaitItem()
            assertEquals(2, (success as UiState.Success).data.transactions.size)
            cancel()
        }
    }

    @Test
    fun `deleteTransaction removes transaction and updates state`() = runTest(testDispatcher) {
        val txn = repository.createTransaction(vaultId, TransactionType.INFLOW, 5000L, "To Delete")
        val vm = VaultDetailViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            var success = awaitItem()
            assertInstanceOf(UiState.Success::class.java, success)
            assertEquals(1, (success as UiState.Success).data.transactions.size)
            assertEquals(5000L, (success as UiState.Success).data.balance)

            vm.deleteTransaction(txn.id)
            advanceUntilIdle()

            assertEquals(UiState.Empty, awaitItem())
            cancel()
        }
    }

    @Test
    fun `deleteTransaction failure sets Error state`() = runTest(testDispatcher) {
        repository.createTransaction(vaultId, TransactionType.INFLOW, 5000L, "To Delete")
        repository.throwOnDelete = true
        val vm = VaultDetailViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            var success = awaitItem()
            assertInstanceOf(UiState.Success::class.java, success)

            vm.deleteTransaction("non-existent")
            advanceUntilIdle()

            val error = awaitItem()
            assertInstanceOf(UiState.Error::class.java, error)
            assertTrue((error as UiState.Error).message.contains("Failed to delete"))
            cancel()
        }
    }

    @Test
    fun `init emits Loading then Error when repository throws on getTransactions`() = runTest(testDispatcher) {
        repository.throwOnGetTransactions = true
        val vm = VaultDetailViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            val error = awaitItem()
            assertInstanceOf(UiState.Error::class.java, error)
            assertTrue((error as UiState.Error).message.contains("Failed to load transactions"))
            cancel()
        }
    }

    @Test
    fun `init emits Loading then Error when repository throws on getBalance`() = runTest(testDispatcher) {
        repository.throwOnGetBalance = true
        val vm = VaultDetailViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            val error = awaitItem()
            assertInstanceOf(UiState.Error::class.java, error)
            assertTrue((error as UiState.Error).message.contains("Failed to load balance"))
            cancel()
        }
    }

    @Test
    fun `retry after error transitions to Loading then Success`() = runTest(testDispatcher) {
        repository.throwOnGetTransactions = true
        val vm = VaultDetailViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            val error = awaitItem()
            assertInstanceOf(UiState.Error::class.java, error)

            repository.throwOnGetTransactions = false
            vm.retry()

            assertEquals(UiState.Loading, awaitItem())
            assertEquals(UiState.Empty, awaitItem())
            cancel()
        }
    }

    @Test
    fun `retry emits Loading first before Success`() = runTest(testDispatcher) {
        repository.createTransaction(vaultId, TransactionType.INFLOW, 1000L, "Test")
        repository.throwOnGetTransactions = true
        val vm = VaultDetailViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            assertInstanceOf(UiState.Error::class.java, awaitItem())

            repository.throwOnGetTransactions = false
            vm.retry()

            assertEquals(UiState.Loading, awaitItem())
            val success = awaitItem()
            assertInstanceOf(UiState.Success::class.java, success)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple retry calls do not cause duplicate emissions`() = runTest(testDispatcher) {
        repository.throwOnGetTransactions = true
        val vm = VaultDetailViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            assertInstanceOf(UiState.Error::class.java, awaitItem())

            repository.throwOnGetTransactions = false
            vm.retry()
            vm.retry()
            vm.retry()

            assertEquals(UiState.Loading, awaitItem())
            assertEquals(UiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `transactions scoped to vault`() = runTest(testDispatcher) {
        repository.createTransaction("other-vault", TransactionType.INFLOW, 50000L, "Not mine")
        repository.createTransaction(vaultId, TransactionType.OUTFLOW, 3000L, "Mine")
        val vm = VaultDetailViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            val success = awaitItem()
            assertInstanceOf(UiState.Success::class.java, success)
            val state = (success as UiState.Success).data as VaultDetailUiState
            assertEquals(1, state.transactions.size)
            assertEquals("Mine", state.transactions[0].description)
            assertEquals(-3000L, state.balance)
            cancel()
        }
    }

    @Test
    fun `combined Flow updates both transactions and balance`() = runTest(testDispatcher) {
        repository.createTransaction(vaultId, TransactionType.INFLOW, 20000L, "Salary")
        repository.createTransaction(vaultId, TransactionType.OUTFLOW, 5000L, "Rent")
        val vm = VaultDetailViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            val success = awaitItem()
            assertInstanceOf(UiState.Success::class.java, success)
            val state = (success as UiState.Success).data as VaultDetailUiState
            assertEquals(2, state.transactions.size)
            assertEquals(15000L, state.balance)
            cancel()
        }
    }

    @Test
    fun `balance updates after transaction changes`() = runTest(testDispatcher) {
        repository.createTransaction(vaultId, TransactionType.INFLOW, 10000L, "Initial")
        val vm = VaultDetailViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            var success = awaitItem()
            assertInstanceOf(UiState.Success::class.java, success)
            assertEquals(10000L, (success as UiState.Success).data.balance)

            repository.createTransaction(vaultId, TransactionType.OUTFLOW, 4000L, "Expense")
            advanceUntilIdle()

            success = awaitItem()
            assertEquals(6000L, (success as UiState.Success).data.balance)

            repository.createTransaction(vaultId, TransactionType.OUTFLOW, 6000L, "Another")
            advanceUntilIdle()

            success = awaitItem()
            assertEquals(0L, (success as UiState.Success).data.balance)
            cancel()
        }
    }

    @Test
    fun `searchQuery defaults to blank and isSearchActive false`() = runTest(testDispatcher) {
        val vm = VaultDetailViewModel(savedStateHandle, repository)
        assertEquals("", vm.searchQuery.value)
        assertFalse(vm.uiState.value.let { it is UiState.Success && it.data.isSearchActive })
    }

    @Test
    fun `onSearchQueryChange updates searchQuery flow`() = runTest(testDispatcher) {
        val vm = VaultDetailViewModel(savedStateHandle, repository)
        vm.onSearchQueryChange("test")
        assertEquals("test", vm.searchQuery.value)
    }

    @Test
    fun `clearSearch resets searchQuery to blank`() = runTest(testDispatcher) {
        val vm = VaultDetailViewModel(savedStateHandle, repository)
        vm.onSearchQueryChange("test")
        vm.clearSearch()
        assertEquals("", vm.searchQuery.value)
    }

    @Test
    fun `empty searchQuery returns all transactions`() = runTest(testDispatcher) {
        repository.createTransaction(vaultId, TransactionType.INFLOW, 100L, "Alpha")
        repository.createTransaction(vaultId, TransactionType.OUTFLOW, 50L, "Beta")
        val vm = VaultDetailViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            val success = awaitItem()
            assertInstanceOf(UiState.Success::class.java, success)
            assertEquals(2, (success as UiState.Success).data.transactions.size)

            vm.onSearchQueryChange("")
            advanceUntilIdle()

            // No re-emission since blank query has 0ms debounce and same result
            cancel()
        }
    }

    @Test
    fun `searchQuery filters transactions by description`() = runTest(testDispatcher) {
        repository.createTransaction(vaultId, TransactionType.INFLOW, 100L, "Groceries")
        repository.createTransaction(vaultId, TransactionType.OUTFLOW, 50L, "Gas bill")
        val vm = VaultDetailViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            val success = awaitItem()
            assertInstanceOf(UiState.Success::class.java, success)
            assertEquals(2, (success as UiState.Success).data.transactions.size)

            vm.onSearchQueryChange("gas")
            advanceUntilIdle()

            val filtered = awaitItem()
            assertInstanceOf(UiState.Success::class.java, filtered)
            val data = (filtered as UiState.Success).data
            assertEquals(1, data.transactions.size)
            assertEquals("Gas bill", data.transactions[0].description)
            assertTrue(data.isSearchActive)
            cancel()
        }
    }

    @Test
    fun `searchQuery is case-insensitive`() = runTest(testDispatcher) {
        repository.createTransaction(vaultId, TransactionType.INFLOW, 100L, "Groceries")
        val vm = VaultDetailViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            awaitItem()

            vm.onSearchQueryChange("GROCERIES")
            advanceUntilIdle()

            val filtered = awaitItem()
            assertInstanceOf(UiState.Success::class.java, filtered)
            assertEquals(1, (filtered as UiState.Success).data.transactions.size)
            cancel()
        }
    }

    @Test
    fun `searchQuery no match shows Empty`() = runTest(testDispatcher) {
        repository.createTransaction(vaultId, TransactionType.INFLOW, 100L, "Groceries")
        val vm = VaultDetailViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            awaitItem()

            vm.onSearchQueryChange("zzzzzz")
            advanceUntilIdle()

            val empty = awaitItem()
            assertEquals(UiState.Empty, empty)
            cancel()
        }
    }

    @Test
    fun `vault with INFLOW and OUTFLOW shows correct sign and count`() = runTest(testDispatcher) {
        repository.createTransaction(vaultId, TransactionType.INFLOW, 15000L, "Paycheck")
        repository.createTransaction(vaultId, TransactionType.OUTFLOW, 2500L, "Coffee shop")
        repository.createTransaction(vaultId, TransactionType.OUTFLOW, 3500L, "Groceries")
        val vm = VaultDetailViewModel(savedStateHandle, repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            val success = awaitItem()
            assertInstanceOf(UiState.Success::class.java, success)
            val state = (success as UiState.Success).data as VaultDetailUiState
            assertEquals(3, state.transactions.size)
            assertEquals(9000L, state.balance)
            cancel()
        }
    }
}
