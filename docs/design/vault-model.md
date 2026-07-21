# Vault Model

## Core Concepts

- A **Vault** is a named container for transactions
- A **Workspace** groups one or more vaults shared between two partners
- The **balance** of a vault is always derived, never stored as a user-editable field

## Balance Calculation

```
Balance = SUM(inflow amounts) - SUM(outflow amounts)
```

- Amounts are always stored as positive Long values. The `type` field (INFLOW/OUTFLOW) determines whether the amount adds to or subtracts from the balance.
- Balance is computed from all transactions in the vault
- Room can calculate this via a `@Query` with aggregation
- A cached balance field exists on the Vault entity for display performance
- Recalculation triggers: transaction insert, update, or deletion

## Vault Lifecycle

```
Created ─→ Active ─→ Archived (soft delete)
               │
               └──→ Deleted (hard delete after grace period)
```

- Archiving hides the vault from the default list but preserves data
- Deletion is soft initially; hard cleanup happens after 30 days
