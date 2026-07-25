package com.vaultledger.feature.workspace

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultledger.domain.model.Workspace
import com.vaultledger.ui.common.ConfirmDeleteDialog
import com.vaultledger.ui.common.ContentDescriptions
import com.vaultledger.ui.common.Dimensions
import com.vaultledger.ui.common.EmptyState
import com.vaultledger.ui.common.ErrorState
import com.vaultledger.ui.common.LoadingState
import com.vaultledger.ui.common.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceListScreen(
    onWorkspaceClick: (workspaceId: String) -> Unit,
    viewModel: WorkspaceListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var workspaceToDelete by remember { mutableStateOf<Workspace?>(null) }

    if (showCreateDialog) {
        CreateWorkspaceDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                viewModel.createWorkspace(name)
                showCreateDialog = false
            },
        )
    }

    workspaceToDelete?.let { workspace ->
        ConfirmDeleteDialog(
            title = "Delete Workspace",
            message = "Are you sure you want to delete \"${workspace.name}\"? This will also delete all vaults and transactions in this workspace. This action cannot be undone.",
            onConfirm = {
                viewModel.deleteWorkspace(workspace.id)
                workspaceToDelete = null
            },
            onDismiss = { workspaceToDelete = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workspaces") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = ContentDescriptions.CreateWorkspace,
                )
            }
        },        ) { innerPadding ->
            when (val state = uiState) {
                is UiState.Loading -> {
                    LoadingState(modifier = Modifier.padding(innerPadding))
                }

                is UiState.Empty -> {
                    EmptyState(
                        message = "No workspaces yet.\nCreate one to get started.",
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is UiState.Success -> {
                    WorkspaceList(
                        workspaces = state.data,
                        onWorkspaceClick = onWorkspaceClick,
                        onWorkspaceLongClick = { workspaceToDelete = it },
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
private fun WorkspaceList(
    workspaces: List<Workspace>,
    onWorkspaceClick: (workspaceId: String) -> Unit,
    onWorkspaceLongClick: (Workspace) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = Dimensions.ListHorizontalPadding),
        contentPadding = PaddingValues(vertical = Dimensions.ListContentPadding),
        verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingSmall),
    ) {
        items(workspaces, key = { it.id }) { workspace ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onWorkspaceClick(workspace.id) },
                        onLongClick = { onWorkspaceLongClick(workspace) },
                    ),
            ) {
                Column(                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Dimensions.CardPadding),
                ) {
                    Text(
                        text = workspace.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (workspace.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(Dimensions.SpacingXSmall))
                        Text(
                            text = workspace.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}