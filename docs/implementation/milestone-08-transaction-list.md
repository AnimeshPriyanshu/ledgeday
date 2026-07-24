# Milestone 8 — Transaction List & Balance

**Complexity**: 3 / 5
**Tasks**: T8.1, T8.2, T8.3, T8.4, T8.5

## Description
Transaction list display with balance header, date-grouped rows, delete action. Edit navigation is wired here; the actual form comes in M9. Delete moved here because it lives on the list screen, not the form.

## Summary of Tasks
| Task | Title | Files |
|------|-------|-------|
| T8.1 | `VaultDetailViewModel` (list + balance + delete, includes unit tests) | `:feature:transactions/` |
| T8.2 | `BalanceHeader` composable | `:feature:transactions/` |
| T8.3 | `TransactionList` composable (empty state + FAB) | `:feature:transactions/` |
| T8.4 | `TransactionRow` composable (color-coded, clickable, delete on long-press) | `:feature:transactions/` |
| T8.5 | Wire FAB → transaction form | `:app/navigation/` |

## Completion Criteria
- [ ] Transaction list renders, sorted by createdAt descending
- [ ] Balance displays at top and matches `SUM(inflow) - SUM(outflow)`
- [ ] INFLOW green, OUTFLOW red
- [ ] Delete with confirmation, balance recalculates
- [ ] ViewModel unit tests pass
