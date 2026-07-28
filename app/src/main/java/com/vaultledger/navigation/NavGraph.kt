package com.vaultledger.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.vaultledger.AppViewModel
import com.vaultledger.feature.settings.AcceptInviteScreen
import com.vaultledger.feature.settings.InviteScreen
import com.vaultledger.feature.settings.SettingsScreen
import com.vaultledger.feature.transactions.TransactionFormScreen
import com.vaultledger.feature.transactions.VaultDetailScreen
import com.vaultledger.feature.vault.VaultListScreen
import com.vaultledger.feature.workspace.WorkspaceListScreen
import com.vaultledger.ui.screen.AuthScreen
import com.vaultledger.ui.screen.SplashScreen

@Composable
fun AppNavGraph(navController: NavHostController) {
    val appViewModel: AppViewModel = hiltViewModel()
    val isAuthenticated by appViewModel.isAuthenticated.collectAsStateWithLifecycle()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    // Auth guard: redirect unauthenticated users from protected routes
    LaunchedEffect(isAuthenticated, currentBackStackEntry) {
        val currentRoute = currentBackStackEntry?.destination?.route
        val isProtectedRoute = currentRoute != Routes.SPLASH &&
            currentRoute != Routes.AUTH &&
            currentRoute != null
        if (isAuthenticated == false && isProtectedRoute) {
            navController.navigate(Routes.AUTH) {
                popUpTo(Routes.WORKSPACES) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

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
                    navController.navigate(Routes.INVITE)
                },
                onNavigateToAcceptInvite = {
                    navController.navigate(Routes.ACCEPT_INVITE)
                },
                onLogout = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.WORKSPACES) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        // Invite Partner
        composable(Routes.INVITE) {
            InviteScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // Accept Invite
        composable(
            route = Routes.ACCEPT_INVITE,
            arguments = listOf(
                navArgument(Routes.ARG_INVITE_CODE) {
                    type = NavType.StringType
                    defaultValue = null
                    nullable = true
                },
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = Routes.DEEP_LINK_ACCEPT_INVITE },
            ),
        ) { backStackEntry ->
            val deepLinkCode = backStackEntry.arguments?.getString(Routes.ARG_INVITE_CODE)
            AcceptInviteScreen(
                initialCode = deepLinkCode,
                onNavigateToWorkspaces = {
                    navController.navigate(Routes.WORKSPACES) {
                        popUpTo(Routes.WORKSPACES) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
