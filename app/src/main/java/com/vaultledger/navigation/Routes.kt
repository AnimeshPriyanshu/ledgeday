package com.vaultledger.navigation

object Routes {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val WORKSPACES = "workspaces"
    const val CREATE_WORKSPACE = "workspaces/create"
    const val VAULT_LIST = "workspaces/{workspaceId}/vaults"
    const val CREATE_VAULT = "workspaces/{workspaceId}/vaults/create"
    const val VAULT_DETAIL = "vaults/{vaultId}"
    const val TRANSACTION_FORM = "vaults/{vaultId}/transaction?transactionId={transactionId}"
    const val SETTINGS = "settings"
    const val SETTINGS_INVITE = "settings/invite"

    // Argument names
    const val ARG_WORKSPACE_ID = "workspaceId"
    const val ARG_VAULT_ID = "vaultId"
    const val ARG_TRANSACTION_ID = "transactionId"

    // Navigation builder functions
    fun vaultList(workspaceId: String): String = "workspaces/$workspaceId/vaults"
    fun createVault(workspaceId: String): String = "workspaces/$workspaceId/vaults/create"
    fun vaultDetail(vaultId: String): String = "vaults/$vaultId"
    fun transactionForm(vaultId: String, transactionId: String? = null): String {
        return if (transactionId != null) {
            "vaults/$vaultId/transaction?transactionId=$transactionId"
        } else {
            "vaults/$vaultId/transaction"
        }
    }
}
