package com.vaultledger.feature.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultledger.domain.model.Transaction
import com.vaultledger.domain.repository.TransactionRepository
import com.vaultledger.ui.common.UiOperation
import com.vaultledger.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class VaultDetailUiState(
    val transactions: List<Transaction>,
    val balance: Long,
)

@HiltViewModel
class VaultDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TransactionRepository,
) : ViewModel() {

    private val vaultId: String = requireNotNull(savedStateHandle[ARG_VAULT_ID]) {
        "Missing vaultId navigation argument"
    }

    private val operation = UiOperation<VaultDetailUiState>(viewModelScope)
    val uiState: StateFlow<UiState<VaultDetailUiState>> = operation.state

    init {
        observeTransactions()
    }

    private fun observeTransactions() {
        operation.observe(
            provide = {
                combine(
                    repository.getTransactionsByVaultId(vaultId),
                    repository.getVaultBalance(vaultId),
                ) { transactions, balance ->
                    VaultDetailUiState(transactions, balance)
                }
            },
            map = { state ->
                if (state.transactions.isEmpty()) UiState.Empty else UiState.Success(state)
            },
        )
    }

    fun deleteTransaction(id: String) {
        operation.launch {
            repository.deleteTransaction(id)
        }
    }

    fun retry() = operation.retry()

    companion object {
        private const val ARG_VAULT_ID = "vaultId"
    }
}
