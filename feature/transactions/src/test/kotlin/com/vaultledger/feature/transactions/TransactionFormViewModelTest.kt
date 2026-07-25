package com.vaultledger.feature.transactions

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.vaultledger.domain.model.Transaction
import com.vaultledger.domain.model.TransactionType
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionFormViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val vaultId = "test-vault-id"
    private lateinit var repository: FakeTransactionRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeTransactionRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Add mode ──

    @Test
    fun `add mode initial state has empty fields`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("vaultId" to vaultId))
        val vm = TransactionFormViewModel(handle, repository)

        val state = vm.state.value
        assertEquals("", state.amount)
        assertNull(state.type)
        assertEquals("", state.description)
        assertFalse(state.isLoading)
        assertFalse(state.isSaving)
        assertNull(state.loadError)
        assertNull(state.saveError)
        assertFalse(state.saveSuccess)
        assertNotNull(state.createdAt) // should have a default date
    }

    @Test
    fun `add mode save creates transaction and sets saveSuccess`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("vaultId" to vaultId))
        val vm = TransactionFormViewModel(handle, repository)

        vm.onAmountChange("150.00")
        vm.onTypeChange(TransactionType.INFLOW)
        vm.onDescriptionChange("Test income")
        vm.save()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.saveSuccess)
        assertFalse(state.isSaving)
        assertNull(state.saveError)
    }

    @Test
    fun `add mode save fails gracefully on repository error`() = runTest(testDispatcher) {
        repository.throwOnCreate = true
        val handle = SavedStateHandle(mapOf("vaultId" to vaultId))
        val vm = TransactionFormViewModel(handle, repository)

        vm.onAmountChange("50.00")
        vm.onTypeChange(TransactionType.OUTFLOW)
        vm.onDescriptionChange("Test expense")
        vm.save()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.saveSuccess)
        assertFalse(state.isSaving)
        assertNotNull(state.saveError)
        assertTrue(state.saveError!!.contains("Failed to create"))
    }

    // ── Edit mode ──

    @Test
    fun `edit mode loads transaction and pre-populates fields`() = runTest(testDispatcher) {
        val txn = repository.createTransaction(vaultId, TransactionType.INFLOW, 10000L, "Salary")

        val handle = SavedStateHandle(
            mapOf("vaultId" to vaultId, "transactionId" to txn.id),
        )
        val vm = TransactionFormViewModel(handle, repository)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("100.00", state.amount)
        assertEquals(TransactionType.INFLOW, state.type)
        assertEquals("Salary", state.description)
        assertEquals(txn.createdAt, state.createdAt)
        assertFalse(state.isLoading)
        assertNull(state.loadError)
    }

    @Test
    fun `edit mode shows loading state while fetching`() = runTest(testDispatcher) {
        val txn = repository.createTransaction(vaultId, TransactionType.OUTFLOW, 2500L, "Coffee")
        val handle = SavedStateHandle(
            mapOf("vaultId" to vaultId, "transactionId" to txn.id),
        )

        // Capture initial state before dispatcher advances
        val vm = TransactionFormViewModel(handle, repository)
        val initialState = vm.state.value
        assertTrue(initialState.isLoading)

        advanceUntilIdle()
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `edit mode load error sets loadError`() = runTest(testDispatcher) {
        repository.throwOnGetById = true
        val handle = SavedStateHandle(
            mapOf("vaultId" to vaultId, "transactionId" to "non-existent"),
        )
        val vm = TransactionFormViewModel(handle, repository)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertNotNull(state.loadError)
        assertTrue(state.loadError!!.contains("Failed to load"))
    }

    @Test
    fun `edit mode retry after load error loads successfully`() = runTest(testDispatcher) {
        val txn = repository.createTransaction(vaultId, TransactionType.INFLOW, 50000L, "Bonus")

        repository.throwOnGetById = true
        val handle = SavedStateHandle(
            mapOf("vaultId" to vaultId, "transactionId" to txn.id),
        )
        val vm = TransactionFormViewModel(handle, repository)
        advanceUntilIdle()

        assertNotNull(vm.state.value.loadError)

        repository.throwOnGetById = false
        vm.retry()
        advanceUntilIdle()

        val state = vm.state.value
        assertNull(state.loadError)
        assertFalse(state.isLoading)
        assertEquals("500.00", state.amount)
        assertEquals(TransactionType.INFLOW, state.type)
        assertEquals("Bonus", state.description)
    }

    @Test
    fun `edit mode save updates existing transaction`() = runTest(testDispatcher) {
        val txn = repository.createTransaction(vaultId, TransactionType.INFLOW, 10000L, "Original")
        val handle = SavedStateHandle(
            mapOf("vaultId" to vaultId, "transactionId" to txn.id),
        )
        val vm = TransactionFormViewModel(handle, repository)
        advanceUntilIdle()

        vm.onAmountChange("200.00")
        vm.onDescriptionChange("Updated")
        vm.save()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.saveSuccess)

        // Verify repository was updated
        val updated = repository.getTransactionById(txn.id)
        assertNotNull(updated)
        assertEquals(20000L, updated!!.amount)
        assertEquals("Updated", updated.description)
    }

    @Test
    fun `edit mode save fails gracefully on update error`() = runTest(testDispatcher) {
        val txn = repository.createTransaction(vaultId, TransactionType.OUTFLOW, 3000L, "Fail")
        repository.throwOnUpdate = true
        val handle = SavedStateHandle(
            mapOf("vaultId" to vaultId, "transactionId" to txn.id),
        )
        val vm = TransactionFormViewModel(handle, repository)
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.saveSuccess)
        assertNotNull(state.saveError)
        assertTrue(state.saveError!!.contains("Failed to update"))
    }

    // ── Validation ──

    @Test
    fun `save with empty amount shows amount error`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("vaultId" to vaultId))
        val vm = TransactionFormViewModel(handle, repository)

        vm.onTypeChange(TransactionType.INFLOW)
        vm.onDescriptionChange("Test")
        vm.save()

        val state = vm.state.value
        assertNotNull(state.amountError)
        assertFalse(state.saveSuccess)
    }

    @Test
    fun `save with zero amount shows amount error`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("vaultId" to vaultId))
        val vm = TransactionFormViewModel(handle, repository)

        vm.onAmountChange("0.00")
        vm.onTypeChange(TransactionType.INFLOW)
        vm.onDescriptionChange("Test")
        vm.save()

        val state = vm.state.value
        assertNotNull(state.amountError)
        assertTrue(state.amountError!!.contains("greater than zero"))
    }

    @Test
    fun `save without type shows type error`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("vaultId" to vaultId))
        val vm = TransactionFormViewModel(handle, repository)

        vm.onAmountChange("100.00")
        vm.onDescriptionChange("Test")
        vm.save()

        val state = vm.state.value
        assertNotNull(state.typeError)
    }

    @Test
    fun `save with empty description shows description error`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("vaultId" to vaultId))
        val vm = TransactionFormViewModel(handle, repository)

        vm.onAmountChange("100.00")
        vm.onTypeChange(TransactionType.INFLOW)
        vm.save()

        val state = vm.state.value
        assertNotNull(state.descriptionError)
        assertTrue(state.descriptionError!!.contains("required"))
    }

    @Test
    fun `save with future date shows date error`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("vaultId" to vaultId))
        val vm = TransactionFormViewModel(handle, repository)

        vm.onAmountChange("100.00")
        vm.onTypeChange(TransactionType.INFLOW)
        vm.onDescriptionChange("Test")
        // Set date far in the future
        vm.onDateChange(System.currentTimeMillis() + 86400000L * 30)
        vm.save()

        val state = vm.state.value
        assertNotNull(state.dateError)
    }

    @Test
    fun `errors clear when fields are corrected`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("vaultId" to vaultId))
        val vm = TransactionFormViewModel(handle, repository)

        // Trigger validation errors
        vm.save()
        assertNotNull(vm.state.value.amountError)

        // Fix amount
        vm.onAmountChange("50.00")
        assertNull(vm.state.value.amountError)
    }

    @Test
    fun `description error clears on edit`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("vaultId" to vaultId))
        val vm = TransactionFormViewModel(handle, repository)

        // Trigger with empty description
        vm.onAmountChange("100.00")
        vm.onTypeChange(TransactionType.INFLOW)
        vm.save()
        assertNotNull(vm.state.value.descriptionError)

        // Fix description
        vm.onDescriptionChange("Groceries")
        assertNull(vm.state.value.descriptionError)
    }

    // ── Duplicate save prevention ──

    @Test
    fun `duplicate save does not create multiple transactions`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("vaultId" to vaultId))
        val vm = TransactionFormViewModel(handle, repository)

        vm.onAmountChange("75.00")
        vm.onTypeChange(TransactionType.OUTFLOW)
        vm.onDescriptionChange("Test")

        vm.save()
        vm.save() // second call should be ignored
        advanceUntilIdle()

        // Only one transaction should exist — verify via first() on the flow
        val txnList = repository.getTransactionsByVaultId(vaultId).first()
        assertEquals(1, txnList.size)
    }

    // ── Amount validation edge cases ──

    @Test
    fun `amount field accepts valid decimal format`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("vaultId" to vaultId))
        val vm = TransactionFormViewModel(handle, repository)

        vm.onAmountChange("1234.56")
        assertEquals("1234.56", vm.state.value.amount)

        vm.onAmountChange("0.01")
        assertEquals("0.01", vm.state.value.amount)
    }

    @Test
    fun `amount field rejects more than two decimal places`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("vaultId" to vaultId))
        val vm = TransactionFormViewModel(handle, repository)

        // The regex rejects input with >2 decimal places entirely
        vm.onAmountChange("100.123")
        assertEquals("", vm.state.value.amount)
    }

    @Test
    fun `type error clears when type is selected`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("vaultId" to vaultId))
        val vm = TransactionFormViewModel(handle, repository)

        vm.onAmountChange("100.00")
        vm.onDescriptionChange("Test")
        vm.save()
        assertNotNull(vm.state.value.typeError)

        vm.onTypeChange(TransactionType.INFLOW)
        assertNull(vm.state.value.typeError)
    }

    @Test
    fun `whitespace-only description triggers validation error`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("vaultId" to vaultId))
        val vm = TransactionFormViewModel(handle, repository)

        vm.onAmountChange("100.00")
        vm.onTypeChange(TransactionType.INFLOW)
        vm.onDescriptionChange("   ")
        vm.save()

        val state = vm.state.value
        assertNotNull(state.descriptionError)
        assertTrue(state.descriptionError!!.contains("required"))
    }

    @Test
    fun `amount with leading zeros parses correctly`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("vaultId" to vaultId))
        val vm = TransactionFormViewModel(handle, repository)

        vm.onAmountChange("00100.00")
        vm.onTypeChange(TransactionType.INFLOW)
        vm.onDescriptionChange("Leading zeros")
        vm.save()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.saveSuccess)

        val txnList = repository.getTransactionsByVaultId(vaultId).first()
        assertEquals(10000L, txnList[0].amount) // 00100.00 -> 10000 cents
    }

    @Test
    fun `edit without changes still saves successfully`() = runTest(testDispatcher) {
        val txn = repository.createTransaction(vaultId, TransactionType.INFLOW, 10000L, "No change")
        val handle = SavedStateHandle(
            mapOf("vaultId" to vaultId, "transactionId" to txn.id),
        )
        val vm = TransactionFormViewModel(handle, repository)
        advanceUntilIdle()

        // Save without changing any fields
        vm.save()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.saveSuccess)

        // Transaction should still exist with same values
        val updated = repository.getTransactionById(txn.id)
        assertNotNull(updated)
        assertEquals(10000L, updated!!.amount)
        assertEquals("No change", updated.description)
    }

    @Test
    fun `date at minimum boundary passes validation`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("vaultId" to vaultId))
        val vm = TransactionFormViewModel(handle, repository)

        // MIN_DATE_MILLIS = 946684800000L (2000-01-01) should be valid
        vm.onAmountChange("50.00")
        vm.onTypeChange(TransactionType.OUTFLOW)
        vm.onDescriptionChange("Old expense")
        vm.onDateChange(946684800000L)
        vm.save()
        advanceUntilIdle()

        val state = vm.state.value
        assertNull(state.dateError)
        assertTrue(state.saveSuccess)
    }

    @Test
    fun `description is trimmed on save`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("vaultId" to vaultId))
        val vm = TransactionFormViewModel(handle, repository)

        vm.onAmountChange("75.00")
        vm.onTypeChange(TransactionType.INFLOW)
        vm.onDescriptionChange("  Spaced out  ")
        vm.save()
        advanceUntilIdle()

        val txnList = repository.getTransactionsByVaultId(vaultId).first()
        assertEquals("Spaced out", txnList[0].description)
    }

    @Test
    fun `date error clears when valid date is set`() = runTest(testDispatcher) {
        val handle = SavedStateHandle(mapOf("vaultId" to vaultId))
        val vm = TransactionFormViewModel(handle, repository)

        vm.onAmountChange("100.00")
        vm.onTypeChange(TransactionType.INFLOW)
        vm.onDescriptionChange("Test")
        vm.onDateChange(500000000000L) // 1985-11-05, before 2000
        vm.save()
        assertNotNull(vm.state.value.dateError)

        // Set a valid date
        vm.onDateChange(System.currentTimeMillis())
        assertNull(vm.state.value.dateError)
    }
}
