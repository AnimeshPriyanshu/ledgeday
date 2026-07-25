package com.vaultledger.feature.vault

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultledger.domain.model.Vault
import com.vaultledger.ui.common.ConfirmDeleteDialog
import com.vaultledger.ui.common.ContentDescriptions
import com.vaultledger.ui.common.Dimensions
import com.vaultledger.ui.common.EmptyState
import com.vaultledger.ui.common.ErrorState
import com.vaultledger.ui.common.LocalSnackbarHostState
import com.vaultledger.ui.common.ShimmerItemType
import com.vaultledger.ui.common.ShimmerList
import com.vaultledger.ui.common.UiState
import com.vaultledger.ui.common.VaultColors
import com.vaultledger.ui.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
    onVaultClick: (vaultId: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: VaultListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var vaultToDelete by remember { mutableStateOf<Vault?>(null) }
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

    if (showCreateDialog) {
        CreateVaultDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, description, color ->
                viewModel.createVault(name, description, color)
                showCreateDialog = false
            },
        )
    }

    vaultToDelete?.let { vault ->
        ConfirmDeleteDialog(
            title = "Delete Vault",
            message = "Are you sure you want to delete \"${vault.name}\"? This will also delete all transactions in this vault. This action cannot be undone.",
            onConfirm = {
                viewModel.deleteVault(vault.id)
                vaultToDelete = null
            },
            onDismiss = { vaultToDelete = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vaults") },
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
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {                    Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = ContentDescriptions.CreateVault,
                )
            }
        },
    ) { innerPadding ->
            when (val state = uiState) {
                is UiState.Loading -> {
                    ShimmerList(
                        modifier = Modifier.padding(innerPadding),
                        itemType = ShimmerItemType.VAULT,
                    )
                }

                is UiState.Empty -> {
                    EmptyState(
                        title = "No vaults in this workspace",
                        description = "Tap + to add one.",
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is UiState.Success -> {
                    VaultList(
                        vaults = state.data,
                        onVaultClick = onVaultClick,
                        onVaultLongClick = { vaultToDelete = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultList(
    vaults: List<Vault>,
    onVaultClick: (vaultId: String) -> Unit,
    onVaultLongClick: (Vault) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = Dimensions.ListHorizontalPadding),
        contentPadding = PaddingValues(vertical = Dimensions.ListContentPadding),
        verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
    ) {
        items(vaults, key = { it.id }) { vault ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onVaultClick(vault.id) },
                        onLongClick = { onVaultLongClick(vault) },
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimensions.CardPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(VaultColors.fromHex(vault.color)),
                    )
                    Spacer(modifier = Modifier.width(Dimensions.SpacingSmall + 4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = vault.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (vault.description.isNotBlank()) {
                            Text(
                                text = vault.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(Dimensions.SpacingSmall))
                    Text(
                        text = formatBalance(vault.balance),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (vault.balance >= 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        }
    }
}

private fun formatBalance(balance: Long): String = CurrencyFormatter.format(balance)
