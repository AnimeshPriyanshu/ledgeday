package com.vaultledger.feature.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultledger.domain.model.Transaction
import com.vaultledger.ui.common.ConfirmDeleteDialog
import com.vaultledger.ui.common.ContentDescriptions
import com.vaultledger.ui.common.EmptyState
import com.vaultledger.ui.common.ErrorState
import com.vaultledger.ui.common.LocalSnackbarHostState
import com.vaultledger.ui.common.ShimmerItemType
import com.vaultledger.ui.common.ShimmerList
import com.vaultledger.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultDetailScreen(
    onAddTransactionClick: () -> Unit,
    onTransactionClick: (transactionId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: VaultDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    val snackbarHostState = LocalSnackbarHostState.current
    val errorMessage = (uiState as? UiState.Error)?.message

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short,
            )
        }
    }

    transactionToDelete?.let { tx ->
        ConfirmDeleteDialog(
            title = "Delete Transaction",
            message = "Are you sure you want to delete this transaction? This action cannot be undone.",
            onConfirm = {
                viewModel.deleteTransaction(tx.id)
                transactionToDelete = null
            },
            onDismiss = { transactionToDelete = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = ContentDescriptions.Back,
                    )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransactionClick,
                containerColor = MaterialTheme.colorScheme.primary,
            ) {                    Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = ContentDescriptions.AddTransaction,
                )
            }
        },        ) { innerPadding ->
            when (val state = uiState) {
                is UiState.Loading -> {
                    ShimmerList(
                        modifier = Modifier.padding(innerPadding),
                        itemType = ShimmerItemType.TRANSACTION,
                    )
                }

                is UiState.Empty -> {
                    EmptyState(
                        title = "No transactions yet",
                        description = "Tap + to record your first.",
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is UiState.Success -> {
                    Column(modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)) {
                        BalanceHeader(balance = state.data.balance)
                        TransactionList(
                            transactions = state.data.transactions,
                            onTransactionClick = onTransactionClick,
                            onTransactionLongClick = { transactionToDelete = it },
                            onAddTransactionClick = onAddTransactionClick,
                        )
                    }
                }

                is UiState.Error -> {
                    ErrorState(
                        message = state.message,
                        onRetry = { viewModel.retry() },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
    }
}
