package com.vaultledger.data.remote

object FirestoreConstants {
    const val COLLECTION_INVITES = "invites"
    const val COLLECTION_WORKSPACES = "workspaces"
    const val COLLECTION_VAULTS = "vaults"
    const val COLLECTION_TRANSACTIONS = "transactions"

    const val FIELD_CODE = "code"
    const val FIELD_CREATOR_ID = "creatorId"
    const val FIELD_CREATOR_EMAIL = "creatorEmail"
    const val FIELD_CREATED_AT = "createdAt"
    const val FIELD_EXPIRES_AT = "expiresAt"
    const val FIELD_STATUS = "status"
    const val FIELD_ACCEPTED_BY = "acceptedBy"
    const val FIELD_NAME = "name"
    const val FIELD_DESCRIPTION = "description"
    const val FIELD_MEMBER_IDS = "memberIds"
    const val FIELD_CREATED_BY = "createdBy"
    const val FIELD_VAULT_ID = "vaultId"
    const val FIELD_TYPE = "type"
    const val FIELD_AMOUNT = "amount"
    const val FIELD_DELETED = "deleted"
    const val FIELD_UPDATED_AT = "updatedAt"
    const val FIELD_COLOR = "color"
    const val FIELD_BALANCE = "balance"
    const val FIELD_INVITE_CODE = "inviteCode"

    const val DEFAULT_SHARED_WORKSPACE_NAME = "Shared Workspace"
    const val DEFAULT_VAULT_COLOR = "#006D77"

    const val INVITE_CODE_LENGTH = 8
    const val INVITE_EXPIRY_MS = 24L * 60 * 60 * 1000
}
