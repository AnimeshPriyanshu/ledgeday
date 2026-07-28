package com.vaultledger.navigation

object Routes {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val WORKSPACES = "workspaces"
    const val VAULT_LIST = "workspaces/{workspaceId}/vaults"
    const val VAULT_DETAIL = "vaults/{vaultId}"
    const val TRANSACTION_FORM = "vaults/{vaultId}/transaction?transactionId={transactionId}"
    const val SETTINGS = "settings"
    const val INVITE = "settings/invite"
    const val ACCEPT_INVITE = "settings/accept-invite?code={inviteCode}"

    // Argument names
    const val ARG_WORKSPACE_ID = "workspaceId"
    const val ARG_VAULT_ID = "vaultId"
    const val ARG_TRANSACTION_ID = "transactionId"
    const val ARG_INVITE_CODE = "inviteCode"
    const val DEEP_LINK_ACCEPT_INVITE = "https://vaultledger.com/invite/{inviteCode}"

    // Navigation builder functions
    fun vaultList(workspaceId: String): String = "workspaces/$workspaceId/vaults"
    fun vaultDetail(vaultId: String): String = "vaults/$vaultId"
    fun transactionForm(vaultId: String, transactionId: String? = null): String {
        return if (transactionId != null) {
            "vaults/$vaultId/transaction?transactionId=$transactionId"
        } else {
            "vaults/$vaultId/transaction"
        }
    }
}
