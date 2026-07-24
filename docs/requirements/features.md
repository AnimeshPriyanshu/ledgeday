# Feature List

## Phase 1 — Core (MVP)

| ID | Feature | Description |
|---|---|---|
| F-01 | Create Vault | User creates a named vault (e.g., "Household", "Freelance") |
| F-02 | Add Transaction | Record a transaction with amount, description, createdAt, and type (inflow/outflow) |
| F-03 | Auto Balance | Each vault displays the running balance calculated from all transactions |
| F-04 | Transaction List | View all transactions in a vault sorted by createdAt |
| F-05 | Edit Transaction | Modify amount, type, description, or createdAt of an existing transaction. `createdBy` and `createdAt` are immutable |
| F-06 | Delete Transaction | Remove a transaction with balance recalculation |
| F-07 | Multiple Vaults | User can create and manage multiple vaults |

## Phase 2 — Partner Sync

| ID | Feature | Description |
|---|---|---|
| F-08 | Workspace Sharing | User can invite a partner to a workspace |
| F-09 | Cloud Sync | Transactions sync between paired devices in near real-time |
| F-10 | Conflict Resolution | Deterministic merge strategy for concurrent edits |
| F-11 | Sync Status Indicator | Visual indicator showing last sync time and connectivity state |

## Phase 3 — Quality

| ID | Feature | Description |
|---|---|---|
| F-12 | Categories | Tag transactions with user-defined categories |
| F-13 | Search & Filter | Search transactions by description, date range, or category |
| F-14 | Export | Export vault data to CSV |
| F-15 | Dark Mode | System-aware theme switching |
| F-16 | Biometric Lock | App-level security with fingerprint / face unlock |
