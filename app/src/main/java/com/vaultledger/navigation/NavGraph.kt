package com.vaultledger.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.vaultledger.feature.settings.SettingsScreen
import com.vaultledger.feature.transactions.TransactionFormScreen
import com.vaultledger.feature.transactions.VaultDetailScreen
import com.vaultledger.feature.vault.VaultListScreen
import com.vaultledger.feature.workspace.WorkspaceListScreen
import com.vaultledger.ui.screen.AuthScreen
import com.vaultledger.ui.screen.SplashScreen

@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
    ) {
        // Splash
        composable(Routes.SPLASH) {
            SplashScreen(
                onNavigateToAuth = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToWorkspaces = {
                    navController.navigate(Routes.WORKSPACES) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        // Auth
        composable(Routes.AUTH) {
            AuthScreen(
                onNavigateToWorkspaces = {
                    navController.navigate(Routes.WORKSPACES) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                },
            )
        }

        // Workspace List
        composable(Routes.WORKSPACES) {
            WorkspaceListScreen(
                onWorkspaceClick = { workspaceId ->
                    navController.navigate(Routes.vaultList(workspaceId))
                },
            )
        }

        // Vault List
        composable(
            route = Routes.VAULT_LIST,
            arguments = listOf(
                navArgument(Routes.ARG_WORKSPACE_ID) { type = NavType.StringType },
            ),
        ) {
            VaultListScreen(
                onVaultClick = { vaultId ->
                    navController.navigate(Routes.vaultDetail(vaultId))
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // Vault Detail (Transaction List)
        composable(
            route = Routes.VAULT_DETAIL,
            arguments = listOf(
                navArgument(Routes.ARG_VAULT_ID) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val vaultId = backStackEntry.arguments?.getString(Routes.ARG_VAULT_ID) ?: return@composable
            VaultDetailScreen(
                onAddTransactionClick = {
                    navController.navigate(Routes.transactionForm(vaultId))
                },
                onTransactionClick = { transactionId ->
                    navController.navigate(Routes.transactionForm(vaultId, transactionId))
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // Transaction Form (Add / Edit)
        composable(
            route = Routes.TRANSACTION_FORM,
            arguments = listOf(
                navArgument(Routes.ARG_VAULT_ID) { type = NavType.StringType },
                navArgument(Routes.ARG_TRANSACTION_ID) {
                    type = NavType.StringType
                    defaultValue = null
                    nullable = true
                },
            ),
        ) { backStackEntry ->
            val vaultId = backStackEntry.arguments?.getString(Routes.ARG_VAULT_ID) ?: return@composable
            val transactionId = backStackEntry.arguments?.getString(Routes.ARG_TRANSACTION_ID)
            TransactionFormScreen(
                vaultId = vaultId,
                transactionId = transactionId,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // Settings
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateToInvite = {
                    navController.navigate(Routes.SETTINGS_INVITE)
                },
                onLogout = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        // Settings - Invite Partner
        composable(Routes.SETTINGS_INVITE) {
            SettingsScreen(
                onNavigateToInvite = {},
                onLogout = {},
            )
        }
    }
}
