# Milestone 9 — Transaction Form

**Complexity**: 3 / 5
**Tasks**: T9.1, T9.2, T9.3, T9.4, T9.5

## Description
Add/edit transaction form with full validation. Navigation guard for unsaved changes (moved from old M10). Delete is NOT part of this milestone — it's handled in M8.

## Summary of Tasks
| Task | Title | Files |
|------|-------|-------|
| T9.1 | `TransactionFormViewModel` (add/edit modes, validation, includes unit tests) | `:feature:transactions/` |
| T9.2 | `TransactionFormScreen` layout (4 fields + date picker) | `:feature:transactions/` |
| T9.3 | Form validation (real-time inline errors) | Same files as T9.1/T9.2 |
| T9.4 | Wire add flow (save → navigate back → list updates) | `:app/navigation/` |
| T9.5 | Wire edit flow (tap row → pre-populate → save) | `:app/navigation/`, `VaultDetailScreen` |

## Completion Criteria
- [ ] Add transaction: form validates, saves, list updates
- [ ] Edit transaction: pre-populates, updates in place
- [ ] Validation blocks: zero amount, empty description, bad date, missing type
- [ ] Inline error messages clear when user fixes field
- [ ] ViewModel unit tests pass
