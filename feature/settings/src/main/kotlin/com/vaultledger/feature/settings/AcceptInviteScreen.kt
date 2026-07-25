package com.vaultledger.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcceptInviteScreen(
    onNavigateToWorkspaces: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: AcceptInviteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var codeInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state) {
        when (val s = state) {
            is AcceptInviteUiState.Error -> {
                snackbarHostState.showSnackbar(
                    message = s.message,
                    duration = SnackbarDuration.Long,
                )
                viewModel.reset()
            }
            is AcceptInviteUiState.Success -> {
                onNavigateToWorkspaces()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accept Invite") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Enter the invite code from your partner.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = codeInput,
                onValueChange = { codeInput = it.uppercase().take(8) },
                label = { Text("Invite Code") },
                placeholder = { Text("e.g. ABC12345") },
                supportingText = {
                    if (codeInput.isNotEmpty() && codeInput.length < 8) {
                        Text(
                            text = "Code must be 8 characters",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = state !is AcceptInviteUiState.Accepting,
                isError = codeInput.isNotEmpty() && codeInput.length < 8,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.acceptInvite(codeInput) },
                modifier = Modifier.fillMaxWidth(),
                enabled = codeInput.length == 8 && state !is AcceptInviteUiState.Accepting,
            ) {
                if (state is AcceptInviteUiState.Accepting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Accept Invite")
                }
            }
        }
    }
}
