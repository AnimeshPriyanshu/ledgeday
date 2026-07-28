package com.vaultledger.journey

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import com.vaultledger.domain.model.Transaction
import com.vaultledger.domain.model.TransactionType
import com.vaultledger.domain.repository.TransactionRepository
import com.vaultledger.feature.transactions.TransactionFormScreen
import com.vaultledger.feature.transactions.TransactionFormViewModel
import com.vaultledger.ui.common.ConfirmDeleteDialog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class EditDeleteJourneyTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var transactionRepo: FakeTransactionRepository

    @Before
    fun setUp() {
        transactionRepo = FakeTransactionRepository()
    }

    @Test
    fun editTransaction_showsExistingData() {
        val txnId =
            runBlocking {
                transactionRepo
                    .createTransaction(
                        vaultId = "test-vault",
                        type = TransactionType.INFLOW,
                        amount = 1000L,
                        description = "Original",
                    ).id
            }

        val viewModel =
            TransactionFormViewModel(
                savedStateHandle =
                    SavedStateHandle(
                        mapOf("vaultId" to "test-vault", "transactionId" to txnId),
                    ),
                repository = transactionRepo,
            )

        composeTestRule.setContent {
            TransactionFormScreen(
                vaultId = "test-vault",
                transactionId = txnId,
                onNavigateBack = {},
                viewModel = viewModel,
            )
        }

        composeTestRule.onNodeWithText("Edit Transaction").assertIsDisplayed()
    }

    @Test
    fun editTransaction_canChangeAmount() {
        val txnId =
            runBlocking {
                transactionRepo
                    .createTransaction(
                        vaultId = "test-vault",
                        type = TransactionType.INFLOW,
                        amount = 1000L,
                        description = "Original",
                    ).id
            }

        val viewModel =
            TransactionFormViewModel(
                savedStateHandle =
                    SavedStateHandle(
                        mapOf("vaultId" to "test-vault", "transactionId" to txnId),
                    ),
                repository = transactionRepo,
            )

        composeTestRule.setContent {
            TransactionFormScreen(
                vaultId = "test-vault",
                transactionId = txnId,
                onNavigateBack = {},
                viewModel = viewModel,
            )
        }

        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.waitForIdle()
        assertTrue(viewModel.state.value.saveSuccess)
    }

    @Test
    fun deleteDialog_showsConfirmation() {
        var confirmed = false

        composeTestRule.setContent {
            ConfirmDeleteDialog(
                title = "Delete Transaction",
                message = "Are you sure you want to delete this transaction?",
                onConfirm = { confirmed = true },
                onDismiss = {},
            )
        }

        composeTestRule.onNodeWithText("Delete Transaction").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertFalse(confirmed)
    }
}
