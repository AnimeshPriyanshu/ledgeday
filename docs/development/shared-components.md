# Shared UI Components

## Overview

Reusable UI components live in `:core:ui` under `com.vaultledger.ui.common`.
These components standardise common UI patterns so feature modules don't duplicate them.

## Components

### `LoadingState`

A centered `CircularProgressIndicator`.

```kotlin
@Composable
fun LoadingState(modifier: Modifier = Modifier)
```

**Usage:**
```kotlin
is UiState.Loading -> LoadingState()
```

---

### `EmptyState`

Centered text with muted styling for empty list states.

```kotlin
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
)
```

**Usage:**
```kotlin
is UiState.Empty -> EmptyState("No items found.")
```

---

### `ErrorState`

Displays an error message with a retry button.

```kotlin
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = "Retry",
)
```

**Usage:**
```kotlin
is UiState.Error -> ErrorState(
    message = state.message,
    onRetry = { viewModel.retry() },
)
```

---

### `ConfirmDeleteDialog`

A confirmation dialog with a destructive Delete button (red) and a Cancel button.

```kotlin
@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Delete",
    dismissLabel: String = "Cancel",
)
```

**Usage:**
```kotlin
itemToDelete?.let { item ->
    ConfirmDeleteDialog(
        title = "Delete Item",
        message = "Are you sure? This cannot be undone.",
        onConfirm = { viewModel.delete(item.id); itemToDelete = null },
        onDismiss = { itemToDelete = null },
    )
}
```

---

### `ContentDescriptions`

Shared string constants for accessibility `contentDescription` attributes.

```kotlin
object ContentDescriptions {
    const val Back = "Back"
    const val CreateWorkspace = "Create workspace"
    const val CreateVault = "Create vault"
    const val AddTransaction = "Add transaction"
    const val AppLogo = "Vault Ledger"
}
```

---

### `Dimensions`

Shared constants for spacing, padding, and content limits.

```kotlin
object Dimensions {
    val SpacingXSmall = 4.dp
    val SpacingSmall = 8.dp
    val SpacingMedium = 16.dp
    val SpacingLarge = 24.dp
    val SpacingXLarge = 32.dp
    val CardPadding = 16.dp
    val ListHorizontalPadding = 16.dp
    val ListContentPadding = 8.dp
    val ScreenPadding = 24.dp
    val BalanceVerticalPadding = 20.dp
    val ColorDotSize = 12.dp
    val ColorPickerItemSize = 32.dp
    val MaxNameLength = 100
    val MaxDescriptionLength = 500
}
```

## Pattern for Adding New Components

1. Create a new file in `:core:ui` under `com.vaultledger.ui.common`
2. Keep the composable generic — no feature-specific strings or logic
3. Use MaterialTheme colors and typography (no hardcoded colors)
4. Add a `@Composable` function with default parameter values where practical
5. Add a `@Preview` annotation for the default state
