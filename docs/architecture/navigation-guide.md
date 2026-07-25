# Navigation Guide

## Overview

Vault Ledger uses Jetpack Compose Navigation with a single `NavHost` in `AppNavGraph`.
Navigation arguments are extracted via `SavedStateHandle` in ViewModels.

## Route Definitions

**Location:** `:app/src/main/java/com/vaultledger/navigation/Routes.kt`

```kotlin
object Routes {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val WORKSPACES = "workspaces"
    const val VAULT_LIST = "workspaces/{workspaceId}/vaults"
    const val VAULT_DETAIL = "vaults/{vaultId}"
    const val TRANSACTION_FORM = "vaults/{vaultId}/transaction?transactionId={transactionId}"
    const val SETTINGS = "settings"
    const val SETTINGS_INVITE = "settings/invite"
}
```

## Argument Conventions

| Argument | Type | Required | Source |
|---|---|---|---|
| `workspaceId` | `String` | Yes | `SavedStateHandle` in `VaultListViewModel` |
| `vaultId` | `String` | Yes | `SavedStateHandle` in `VaultDetailViewModel`, `TransactionFormViewModel` |
| `transactionId` | `String?` | No (optional) | `SavedStateHandle` in `TransactionFormViewModel` |

## Navigation Builder Functions

Use the helper functions in `Routes.kt` for type-safe navigation:

```kotlin
navController.navigate(Routes.vaultList(workspaceId))
navController.navigate(Routes.vaultDetail(vaultId))
navController.navigate(Routes.transactionForm(vaultId))
navController.navigate(Routes.transactionForm(vaultId, transactionId))
```

## Adding a New Feature

1. Add a route constant and (if needed) builder function in `Routes.kt`
2. Register the route in `NavGraph.kt` with typed `navArgument` declarations
3. Create the screen composable in the feature module
4. Create the ViewModel with `SavedStateHandle` to extract navigation args
5. Wire navigation callbacks in `NavGraph.kt`

## Argument Extraction Pattern

ViewModels extract required string arguments from `SavedStateHandle`:

```kotlin
@HiltViewModel
class FeatureViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FeatureRepository,
) : ViewModel() {
    private val argId: String = requireNotNull(savedStateHandle[ARG_ID]) {
        "Missing argument"
    }
}
```

## Back Navigation

- Use `navController.popBackStack()` for standard back navigation
- Clear back stack when navigating to top-level destinations (auth, workspace list)
- Use `popUpTo` with `inclusive = true` when navigating away from splash/auth

## Bottom Navigation

The `AppScaffold` contains a `NavigationBar` visible on workspace-internal screens.
Add new items to the `bottomNavItems` list in `AppScaffold.kt` to include them.
