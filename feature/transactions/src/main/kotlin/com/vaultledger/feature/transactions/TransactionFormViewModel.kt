package com.vaultledger.feature.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultledger.domain.model.Transaction
import com.vaultledger.domain.model.TransactionType
import com.vaultledger.domain.repository.TransactionRepository
import com.vaultledger.ui.common.Dimensions as SharedDimensions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionFormUiState(
    val amount: String = "",
    val type: TransactionType? = null,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val amountError: String? = null,
    val descriptionError: String? = null,
    val typeError: String? = null,
    val dateError: String? = null,
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val saveError: String? = null,
    val saveSuccess: Boolean = false,
)

@HiltViewModel
class TransactionFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TransactionRepository,
) : ViewModel() {

    private val vaultId: String = requireNotNull(savedStateHandle[ARG_VAULT_ID]) {
        "Missing vaultId navigation argument"
    }
    private val transactionId: String? = savedStateHandle[ARG_TRANSACTION_ID]

    private val _state = MutableStateFlow(TransactionFormUiState())
    val state: StateFlow<TransactionFormUiState> = _state.asStateFlow()

    init {
        if (transactionId != null) {
            loadTransaction(transactionId)
        }
    }

    private fun loadTransaction(txnId: String) {
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            try {
                val transaction = repository.getTransactionById(txnId)
                if (transaction != null) {
                    val dollars = transaction.amount / 100
                    val cents = transaction.amount % 100
                    val amountStr = "$dollars.${cents.toString().padStart(2, '0')}"
                    _state.update {
                        it.copy(
                            amount = amountStr,
                            type = transaction.type,
                            description = transaction.description,
                            createdAt = transaction.createdAt,
                            isLoading = false,
                            loadError = null,
                        )
                    }
                } else {
                    _state.update { it.copy(isLoading = false, loadError = "Transaction not found") }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update {
                    it.copy(isLoading = false, loadError = e.message ?: "Failed to load transaction")
                }
            }
        }
    }

    fun retry() {
        val txnId = transactionId ?: return
        loadTransaction(txnId)
    }

    fun onAmountChange(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
            _state.update { it.copy(amount = value, amountError = null) }
        }
    }

    fun onTypeChange(type: TransactionType) {
        _state.update { it.copy(type = type, typeError = null) }
    }

    fun onDescriptionChange(description: String) {
        if (description.length <= SharedDimensions.MaxTransactionDescriptionLength) {
            _state.update { it.copy(description = description, descriptionError = null) }
        }
    }

    fun onDateChange(epochMillis: Long) {
        _state.update { it.copy(createdAt = epochMillis, dateError = null) }
    }

    fun save() {
        val current = _state.value
        if (!validate(current)) return
        if (current.isSaving) return

        _state.update { it.copy(isSaving = true, saveError = null) }

        val amountCents = parseAmountToCents(current.amount)
        val trimmedDescription = current.description.trim()

        viewModelScope.launch {
            try {
                if (transactionId != null) {
                    repository.updateTransaction(
                        Transaction(
                            id = transactionId,
                            vaultId = vaultId,
                            type = current.type!!,
                            amount = amountCents,
                            description = trimmedDescription,
                            createdAt = current.createdAt,
                        ),
                    )
                } else {
                    repository.createTransaction(
                        vaultId = vaultId,
                        type = current.type!!,
                        amount = amountCents,
                        description = trimmedDescription,
                    )
                }
                _state.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update {
                    it.copy(isSaving = false, saveError = e.message ?: "Failed to save transaction")
                }
            }
        }
    }

    private fun parseAmountToCents(amount: String): Long {
        if (amount.isEmpty()) return 0L
        val parts = amount.split(".")
        val dollars = parts[0].toLongOrNull() ?: 0L
        val cents = if (parts.size > 1) {
            parts[1].padEnd(2, '0').take(2).toLongOrNull() ?: 0L
        } else {
            0L
        }
        return dollars * 100 + cents
    }

    private fun validate(state: TransactionFormUiState): Boolean {
        var valid = true
        val errors = mutableListOf<Pair<String, String?>>()

        // Amount validation
        val amountError = when {
            state.amount.isBlank() -> { valid = false; "Amount is required" }
            else -> {
                val parts = state.amount.split(".")
                val dollars = parts[0].toLongOrNull()
                if (dollars == null || dollars > Long.MAX_VALUE / 100) {
                    valid = false; "Amount is too large"
                } else {
                    val cents = parseAmountToCents(state.amount)
                    when {
                        cents <= 0L -> { valid = false; "Amount must be greater than zero" }
                        else -> null
                    }
                }
            }
        }
        errors.add("amount" to amountError)

        // Type validation
        val typeError = if (state.type == null) {
            valid = false; "Select inflow or outflow"
        } else null
        errors.add("type" to typeError)

        // Description validation
        val descriptionError = when {
            state.description.isBlank() -> { valid = false; "Description is required" }
            state.description.length > SharedDimensions.MaxTransactionDescriptionLength ->
                { valid = false; "Maximum ${SharedDimensions.MaxTransactionDescriptionLength} characters" }
            else -> null
        }
        errors.add("description" to descriptionError)

        // Date validation
        val dateError = when {
            state.createdAt < MIN_DATE_MILLIS -> { valid = false; "Date must be after 2000-01-01" }
            state.createdAt > System.currentTimeMillis() + DAY_MILLIS ->
                { valid = false; "Date cannot be in the future" }
            else -> null
        }
        errors.add("date" to dateError)

        _state.update {
            it.copy(
                amountError = errors.find { it.first == "amount" }?.second,
                typeError = errors.find { it.first == "type" }?.second,
                descriptionError = errors.find { it.first == "description" }?.second,
                dateError = errors.find { it.first == "date" }?.second,
            )
        }

        return valid
    }

    companion object {
        private const val ARG_VAULT_ID = "vaultId"
        private const val ARG_TRANSACTION_ID = "transactionId"

        private const val MIN_DATE_MILLIS = 946684800000L // 2000-01-01
        private const val DAY_MILLIS = 86400000L
    }
}
