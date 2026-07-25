package com.vaultledger.journey

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import com.vaultledger.domain.model.Transaction
import com.vaultledger.domain.model.TransactionType
import com.vaultledger.domain.repository.TransactionRepository
import com.vaultledger.feature.transactions.TransactionFormScreen
import com.vaultledger.feature.transactions.TransactionFormViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import java.util.UUID

class AddTransactionJourneyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var transactionRepo: FakeTransactionRepository

    @Before
    fun setUp() {
        transactionRepo = FakeTransactionRepository()
    }

    @Test
    fun addTransactionForm_showsFields() {
        val viewModel = TransactionFormViewModel(
            savedStateHandle = SavedStateHandle(mapOf("vaultId" to "test-vault")),
            repository = transactionRepo,
        )

        composeTestRule.setContent {
            TransactionFormScreen(
                vaultId = "test-vault",
                transactionId = null,
                onNavigateBack = {},
                viewModel = viewModel,
            )
        }

        composeTestRule.onNodeWithText("Add Transaction").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save").assertIsDisplayed()
    }

    @Test
    fun addTransactionForm_fillsFieldsAndSaves() {
        val viewModel = TransactionFormViewModel(
            savedStateHandle = SavedStateHandle(mapOf("vaultId" to "test-vault")),
            repository = transactionRepo,
        )

        composeTestRule.setContent {
            TransactionFormScreen(
                vaultId = "test-vault",
                transactionId = null,
                onNavigateBack = {},
                viewModel = viewModel,
            )
        }

        composeTestRule.onNodeWithText("Amount").performTextInput("50.00")
        composeTestRule.onNodeWithText("Inflow").performClick()
        composeTestRule.onNodeWithText("Description").performTextInput("Test deposit")
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.waitForIdle()
        assertTrue(viewModel.state.value.saveSuccess)
    }
}

class FakeTransactionRepository : TransactionRepository {

    private val transactions = mutableMapOf<String, Transaction>()
    private val versionFlow = MutableStateFlow(0)

    override fun getTransactionsByVaultId(vaultId: String): Flow<List<Transaction>> {
        return versionFlow.map {
            transactions.values
                .filter { it.vaultId == vaultId }
                .sortedByDescending { it.createdAt }
        }
    }

    override suspend fun getTransactionById(id: String): Transaction? = transactions[id]

    override suspend fun createTransaction(
        vaultId: String,
        type: TransactionType,
        amount: Long,
        description: String,
    ): Transaction {
        val txn = Transaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            type = type,
            amount = amount,
            description = description,
            createdAt = System.currentTimeMillis(),
        )
        transactions[txn.id] = txn
        versionFlow.value++
        return txn
    }

    override suspend fun updateTransaction(transaction: Transaction) {
        transactions[transaction.id] = transaction
        versionFlow.value++
    }

    override suspend fun deleteTransaction(id: String) {
        transactions.remove(id)
        versionFlow.value++
    }

    override fun getVaultBalance(vaultId: String): Flow<Long> {
        return versionFlow.map {
            transactions.values
                .filter { it.vaultId == vaultId }
                .sumOf { txn ->
                    when (txn.type) {
                        TransactionType.INFLOW -> txn.amount
                        TransactionType.OUTFLOW -> -txn.amount
                    }
                }
        }
    }
}
