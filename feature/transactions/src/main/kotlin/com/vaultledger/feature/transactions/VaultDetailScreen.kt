package com.vaultledger.feature.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultledger.domain.model.Transaction
import com.vaultledger.ui.common.ConfirmDeleteDialog
import com.vaultledger.ui.common.ContentDescriptions
import com.vaultledger.ui.common.Dimensions
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
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
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

    LaunchedEffect(uiState) {
        if (isRefreshing && uiState !is UiState.Loading) {
            isRefreshing = false
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
            Column {
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
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    placeholder = { Text("Search transactions") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = viewModel::clearSearch) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = ContentDescriptions.ClearSearch,
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = Dimensions.SearchBarVerticalPadding),
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddTransactionClick,
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = ContentDescriptions.AddTransaction,
                )
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                viewModel.retry()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val state = uiState) {
                is UiState.Loading -> {
                    ShimmerList(
                        itemType = ShimmerItemType.TRANSACTION,
                    )
                }

                is UiState.Empty -> {
                    val hasSearch = searchQuery.isNotBlank()
                    if (hasSearch) {
                        EmptyState(
                            title = "No matching transactions",
                            description = "Try a different search term.",
                        )
                    } else {
                        EmptyState(
                            title = "No transactions yet",
                            description = "Tap + to record your first.",
                        )
                    }
                }

                is UiState.Success -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        BalanceHeader(balance = state.data.balance)
                        if (searchQuery.isNotBlank() && state.data.transactions.isEmpty()) {
                            EmptyState(
                                title = "No matching transactions",
                                description = "Try a different search term.",
                            )
                        } else {
                            TransactionList(
                                transactions = state.data.transactions,
                                onTransactionClick = onTransactionClick,
                                onTransactionLongClick = { transactionToDelete = it },
                                onAddTransactionClick = onAddTransactionClick,
                            )
                        }
                    }
                }

                is UiState.Error -> {
                    ErrorState(
                        message = state.message,
                        onRetry = { viewModel.retry() },
                    )
                }
            }
        }
    }
}
