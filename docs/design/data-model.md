# Data Model

## Entity-Relationship Overview

```
User (1) ──── (N) Workspace (N) ──── (N) Vault (1) ──── (N) Transaction
```

## Core Entities

### User

| Field | Type | Notes |
|---|---|---|
| id | String | Firebase Auth UID |
| displayName | String | |
| email | String | |

### Workspace

| Field | Type | Notes |
|---|---|---|
| id | String | UUID |
| name | String | |
| createdAt | Long | Epoch millis |
| ownerId | String | User who created it |
| partnerId | String? | Invited partner's User ID |

### Vault

| Field | Type | Notes |
|---|---|---|
| id | String | UUID |
| workspaceId | String | FK to Workspace — CASCADE: deleting the workspace deletes its vaults and transactions |
| name | String | e.g., "Household" |
| description | String? | |
| createdAt | Long | Epoch millis |
| color | Int? | Display color |
| balance | Long | Cached, derived from transactions |

## Validation Rules

| Field | Rule |
|-------|------|
| Transaction.amount | `> 0` and `≤ 999,999,999,999` (max ~$10B in cents) |
| Transaction.type | Required. Must be `INFLOW` or `OUTFLOW` |
| Transaction.description | Required. Max 500 characters |
| Transaction.date | Required. Epoch millis. Must be within reasonable range (not before 2000-01-01, not after now + 1 day) |
| Vault.name | Required. Max 100 characters |
| Vault.description | Optional. Max 500 characters |
| Workspace.name | Required. Max 100 characters |

### Transaction

> Amounts are always stored as positive Long values. The `type` field (INFLOW/OUTFLOW) determines whether the amount adds to or subtracts from the balance. See [Vault Model](vault-model.md) for the balance formula.

| Field | Type | Notes |
|---|---|---|
| id | String | UUID |
| vaultId | String | FK to Vault — CASCADE: deleting the vault deletes its transactions |
| amount | Long | Value in smallest currency unit (cents). Always positive; type determines add/subtract |
| type | Enum | INFLOW / OUTFLOW |
| description | String | |
| category | String? | (Phase 3) User-defined category. Omit from MVP entity; add via migration |
| date | Long | Transaction date (epoch millis) |
| createdAt | Long | Record creation time |
| lastModified | Long | (Phase 2) Last modification time, used for sync. Omit from MVP entity; add via migration |
| createdBy | String | (Phase 2) User ID who created it. Omit from MVP entity; add via migration |
| synced | Boolean | (Phase 2) Pending sync flag. Omit from MVP entity; add via migration |
