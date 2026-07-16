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
| workspaceId | String | FK to Workspace |
| name | String | e.g., "Household" |
| description | String? | |
| createdAt | Long | Epoch millis |
| color | Int? | Display color |
| balance | Long | Cached, derived from transactions |

### Transaction

| Field | Type | Notes |
|---|---|---|
| id | String | UUID |
| vaultId | String | FK to Vault |
| amount | Long | Value in smallest currency unit (cents) |
| type | Enum | INFLOW / OUTFLOW |
| description | String | |
| category | String? | User-defined category |
| date | Long | Transaction date (epoch millis) |
| createdAt | Long | Record creation time |
| lastModified | Long | Last modification time (used for sync) |
| createdBy | String | User ID who created it |
| synced | Boolean | Pending sync flag |
