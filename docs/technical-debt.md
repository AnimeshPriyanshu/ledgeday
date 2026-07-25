# Technical Debt

Last updated: 2026-07-25
Milestone: M9 complete, preparing M10

## HIGH — Must Fix Before Release

| Item | Location | Description |
|------|----------|-------------|
| Firestore security rules | docs/design/ | Not yet defined — data breach risk for production |
| BalanceHeader colors | BalanceHeader.kt | Now uses theme-primaryContainer (teal) instead of original green tint for positive balances. Revisit if green is preferred for financial contexts |
| `SplashScreen` TODO | SplashScreen.kt:29 | `// TODO: Check auth state; navigate accordingly` — requires M10 auth integration |

## MEDIUM — Should Address During Polish (M11)

| Item | Location | Description |
|------|----------|-------------|
| Navigation guard | TransactionFormScreen.kt | Unsaved changes detection not implemented — user can navigate away and lose form input |
| Loading shimmer/skeletons | All list screens | All screens use plain `CircularProgressIndicator` instead of shimmer skeleton placeholders |
| Error snackbars | All screens | Errors show as inline text blocks instead of dismissable snackbars |
| Repository integration tests | `:core:data` tests | No in-memory Room tests for `WorkspaceRepositoryImpl`, `VaultRepositoryImpl`, `TransactionRepositoryImpl` |
| Empty state illustrations | All list screens | All use plain text — no illustrations or icons |
| `vaultCount` / `transactionCount` | Domain models | Fields exist on `Workspace` and `Vault` but are never populated |
| `updatedAt` on Transaction | TransactionFormViewModel.kt | Edit mode save doesn't set `updatedAt` on the Transaction model |
| Transaction list empty state | TransactionList.kt | Empty state is handled by VaultDetailScreen, not the list composable itself |

## LOW — Nice-to-Have Improvements

| Item | Location | Description |
|------|----------|-------------|
| Date field UX | TransactionFormScreen.kt | Date picker uses `OutlinedButton` — could be a read-only `OutlinedTextField` with calendar icon for visual consistency |
| Amount input design | TransactionFormScreen.kt | Input with >2 decimal places is rejected entirely (not truncated) |
| Delete confirmation wording | CreateWorkspaceDialog, CreateVaultDialog, VaultDetailScreen | Cascade warnings are hardcoded strings — could be centralized |
| Detekt analysis | Project root | `detekt` is configured in `gradle/libs.versions.toml` and `detekt-config.yml` but hasn't been run |
| Gradle deprecation warnings | Project root | Build uses deprecated Gradle features incompatible with Gradle 9.0 |
| `CalculateBalanceUseCase` | Removed | Was deleted during M6-M8 cleanup as dead code. The task doc still references it under T2.3 |
