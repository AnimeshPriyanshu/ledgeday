# Milestone 4 — Repositories & DI Wiring

**Complexity**: 3 / 5
**Tasks**: T4.1, T4.2, T4.3, T4.4, T4.5, T4.6

## Description
Implement repository classes that map Room entities to domain models. Split into basic CRUD (T4.1-T4.3) and balance cache atomicity (T4.4). Wire everything with Hilt modules.

## Summary of Tasks
| Task | Title | Files |
|------|-------|-------|
| T4.1 | `WorkspaceRepositoryImpl` | `:core:data/repository/` |
| T4.2 | `VaultRepositoryImpl` | `:core:data/repository/` |
| T4.3 | `TransactionRepositoryImpl` — CRUD only | `:core:data/repository/` |
| T4.4 | Balance cache atomicity (`@Transaction` wrapping) | Same file as T4.3 |
| T4.5 | Hilt `DatabaseModule` | `:core:data/di/` |
| T4.6 | Hilt `RepositoryModule` | `:core:data/di/` |

## Completion Criteria
- [ ] All repository unit tests pass with in-memory Room
- [ ] Balance cache verified: insert 3, update 1, delete 1, balance always matches
- [ ] Rollback test: DAO exception leaves balance unchanged
- [ ] Hilt compiles without binding errors
