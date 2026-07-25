package com.vaultledger.feature.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultledger.domain.model.Transaction
import com.vaultledger.domain.repository.TransactionRepository
import com.vaultledger.ui.common.UiOperation
import com.vaultledger.ui.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

data class VaultDetailUiState(
    val transactions: List<Transaction>,
    val balance: Long,
    val isSearchActive: Boolean = false,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class VaultDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TransactionRepository,
) : ViewModel() {

    private val vaultId: String = requireNotNull(savedStateHandle[ARG_VAULT_ID]) {
        "Missing vaultId navigation argument"
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val operation = UiOperation<VaultDetailUiState>(viewModelScope)
    val uiState: StateFlow<UiState<VaultDetailUiState>> = operation.state

    init {
        observeTransactions()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    private fun observeTransactions() {
        operation.observe(
            provide = {
                _searchQuery
                    .debounce { query -> if (query.isBlank()) 0L else 300L }
                    .flatMapLatest { query ->
                        val trimmed = query.trim()
                        val transactionsFlow = if (trimmed.isEmpty()) {
                            repository.getTransactionsByVaultId(vaultId)
                        } else {
                            repository.searchTransactions(vaultId, trimmed)
                        }
                        combine(transactionsFlow, repository.getVaultBalance(vaultId)) { transactions, balance ->
                            VaultDetailUiState(
                                transactions = transactions,
                                balance = balance,
                                isSearchActive = query.isNotBlank(),
                            )
                        }
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
