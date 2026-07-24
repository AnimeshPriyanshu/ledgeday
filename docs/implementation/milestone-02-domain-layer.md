# Milestone 2 — Domain Layer

**Complexity**: 2 / 5
**Tasks**: T2.1, T2.2, T2.3, T2.4

## Description
Define domain models, repository contracts, use cases, and shared infrastructure. Domain layer is pure Kotlin — no Android dependencies. Room entities in M3 will mirror these domain models.

## Summary of Tasks
| Task | Title | Files |
|------|-------|-------|
| T2.1 | Domain models (`Workspace`, `Vault`, `Transaction`, `TransactionType`) | `:core:domain/model/` |
| T2.2 | Repository interfaces | `:core:domain/repository/` |
| T2.3 | Use cases (`CalculateBalanceUseCase`) | `:core:domain/usecase/` |
| T2.4 | Shared infrastructure (`UiState`, `CurrencyFormatter`, `DateFormatter`, `UuidGenerator`) | `:core:ui/common/`, `:core:ui/util/` |

## Completion Criteria
- [ ] All domain models compile without Android imports
- [ ] Repository interfaces use only domain types
- [ ] Balance formula verified by unit test
- [ ] Shared utilities available to all feature modules
