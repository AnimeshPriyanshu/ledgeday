# Milestone 3 — Data Layer

**Complexity**: 2 / 5
**Tasks**: T3.1, T3.2, T3.3

## Description
Create Room entities that mirror the domain models from M2. Define DAOs with CRUD operations and balance aggregation query. Wire them into the Room database class.

## Summary of Tasks
| Task | Title | Files |
|------|-------|-------|
| T3.1 | Room entities (`WorkspaceEntity`, `VaultEntity`, `TransactionEntity`) | `:core:data/local/entity/` |
| T3.2 | DAOs with CRUD + balance query | `:core:data/local/dao/` |
| T3.3 | `VaultLedgerDatabase` Room database | `:core:data/local/` |

## Completion Criteria
- [ ] All entities compile with Room annotations
- [ ] FK constraints with CASCADE delete defined
- [ ] Balance aggregation query returns correct results
- [ ] DAO tests pass with in-memory Room
