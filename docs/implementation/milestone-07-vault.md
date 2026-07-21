# Milestone 7 — Vault Feature

**Complexity**: 2 / 5
**Tasks**: T7.1, T7.2, T7.3, T7.4

## Description
Vault CRUD scoped to workspace: list screen with ViewModel, create dialog with optional description and color, delete with confirmation, navigation wiring.

## Summary of Tasks
| Task | Title | Files |
|------|-------|-------|
| T7.1 | `VaultListViewModel` (includes unit tests) | `:feature:vault/` |
| T7.2 | `VaultListScreen` (cards + delete) | `:feature:vault/` |
| T7.3 | `CreateVaultDialog` | `:feature:vault/` |
| T7.4 | Wire navigation → transaction list | `:app/navigation/` |

## Completion Criteria
- [ ] Can create vault with name + optional fields
- [ ] Vault list scoped to workspace
- [ ] Each vault card shows name, balance, color
- [ ] Delete with confirmation
- [ ] ViewModel unit tests pass
