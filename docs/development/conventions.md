# Coding Conventions

## Kotlin Style

- Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use `ktlint` for formatting enforcement

## Naming

| Element | Convention | Example |
|---|---|---|
| Packages | lowercase, reverse domain | `com.vaultledger.feature.vault` |
| Classes | PascalCase | `VaultRepository` |
| Functions | camelCase | `getTransactions()` |
| Composable | PascalCase | `VaultScreen()` |
| ViewModel | PascalCase + "VM" | `VaultListViewModel` |
| Test classes | PascalCase + "Test" | `VaultRepositoryTest` |

## Architecture Rules

- ViewModels never reference Android framework classes (no `Context`, no `View`)
- Repositories are the single source of truth for data access
- Use cases are single-responsibility; one public function per use case
- Composable functions are stateless unless they own a ViewModel

## File Structure Per Feature

```
feature/vault/
├── VaultListScreen.kt
├── VaultListViewModel.kt
├── VaultDetailScreen.kt
├── VaultDetailViewModel.kt
└── CreateVaultDialog.kt
```

> **Note:** Repositories live in `:core:data`, not in feature modules.
> Domain models live in `:core:domain`, not in feature modules.
