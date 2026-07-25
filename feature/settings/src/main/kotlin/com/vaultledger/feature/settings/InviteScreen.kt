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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultledger.domain.model.Invite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteScreen(
    onNavigateBack: () -> Unit,
    viewModel: InviteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    var lastCopiedCode by remember { mutableStateOf<String?>(null) }
    var clipboardClearTime by remember { mutableStateOf(0L) }

    LaunchedEffect(lastCopiedCode) {
        lastCopiedCode?.let { code ->
            clipboardManager.setText(AnnotatedString(code))
            snackbarHostState.showSnackbar(
                message = "Code copied to clipboard",
                duration = SnackbarDuration.Short,
            )
            clipboardClearTime = System.nanoTime()
            lastCopiedCode = null
        }
    }

    LaunchedEffect(clipboardClearTime) {
        if (clipboardClearTime > 0L) {
            kotlinx.coroutines.delay(30_000L)
            clipboardManager.setText(AnnotatedString(""))
        }
    }

    LaunchedEffect(state) {
        when (val s = state) {
            is InviteUiState.Error -> {
                snackbarHostState.showSnackbar(
                    message = s.message,
                    duration = SnackbarDuration.Long,
                )
            }
            is InviteUiState.Revoked -> {
                onNavigateBack()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invite Partner") },
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
            when (val s = state) {
                is InviteUiState.Idle, is InviteUiState.Generating -> {
                    Text(
                        text = "Generate an invite code to share with your partner.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    if (state is InviteUiState.Generating) {
                        CircularProgressIndicator()
                    } else {
                        Button(
                            onClick = { viewModel.generateInvite() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Generate Invite Code")
                        }
                    }
                }

                is InviteUiState.Generated -> {
                    InviteCodeDisplay(
                        invite = s.invite,
                        onCopy = { lastCopiedCode = s.invite.code },
                        onRevoke = { viewModel.revokeInvite(s.invite.code) },
                    )
                }

                is InviteUiState.Error -> {
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    if (s.isRetryable) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.generateInvite() }) {
                            Text("Retry")
                        }
                    }
                }

                is InviteUiState.Revoked -> { /* handled by LaunchedEffect */ }
            }
        }
    }
}

@Composable
private fun InviteCodeDisplay(
    invite: Invite,
    onCopy: () -> Unit,
    onRevoke: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Share this code with your partner",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = invite.code,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Expires in 24 hours",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onCopy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Copy Code")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onRevoke,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Revoke")
        }
    }
}
