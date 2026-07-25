package com.vaultledger.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember

val LocalSnackbarHostState = compositionLocalOf { SnackbarHostState() }

@Composable
fun SnackbarProvider(
    content: @Composable () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    CompositionLocalProvider(
        LocalSnackbarHostState provides snackbarHostState,
        content = content,
    )
}
