# Screen Map

## Navigation Structure

```
Splash
  │
  └── Auth (Login / Register)
        │
        └── Workspace List  ◄── Create Workspace (dialog)
              │
              └── Vault List
                    │
                    ├── Create Vault
                    │
                    ├── Vault Detail (Transaction List)
                    │     │
                    │     └── Add / Edit Transaction
                    │
                    └── Settings
                          ├── Profile
                          ├── Invite Partner
                          └── About
```

## Screen List

| Screen | Route | Purpose |
|---|---|---|
| Splash | `/splash` | App launch, auth check |
| Auth | `/auth` | Login / register with email or Google |
| Workspace List | `/workspaces` | List of user's workspaces |
| Vault List | `/workspaces/{id}/vaults` | Vaults in the selected workspace |
| Vault Detail | `/vaults/{id}` | Transaction list for a vault |
| Create Workspace | *(dialog, no route)* | Create a new workspace via dialog from Workspace List |
| Create Vault | `/workspaces/{id}/vaults/create` | Create a new vault within a workspace |
| Transaction Form | `/vaults/{id}/transaction` | Add / edit a transaction |
| Settings | `/settings` | App settings, profile, invite |
| Invite Partner | `/settings/invite` | Generate or accept invitation |

## Navigation Pattern

- Bottom navigation bar with: Vaults | Settings (when inside a workspace)
- Modal bottom sheet for Add Transaction
- Swipe-to-go-back on detail screens
