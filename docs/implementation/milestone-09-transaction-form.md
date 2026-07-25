# Milestone 9 — Transaction Form

**Status**: ✅ Complete
**Complexity**: 3 / 5
**Tasks**: T9.1 — T9.5

## Add/Edit Workflow

### Add Mode
1. User taps `+` FAB on `VaultDetailScreen`
2. Navigation calls `Routes.transactionForm(vaultId)` — no `transactionId`
3. `TransactionFormViewModel` detects `transactionId == null` → empty form
4. User fills in: amount, type, description, date
5. User taps "Save" in the TopAppBar
6. ViewModel validates all fields:
   - If invalid → inline errors shown, save blocked
   - If valid → `repository.createTransaction()` called
7. On success → `saveSuccess = true` → screen navigates back
8. `VaultDetailScreen` reactively updates via Room Flow (no manual refresh)

### Edit Mode
1. User taps a `TransactionRow` on `VaultDetailScreen`
2. Navigation calls `Routes.transactionForm(vaultId, transactionId)` — includes ID
3. `TransactionFormViewModel` detects `transactionId != null` → `loadTransaction()` in `init`
4. Loading state shown (inside Scaffold — back button remains visible)
5. Existing transaction data loaded and pre-populated into all fields
6. User modifies fields and taps "Save"
7. ViewModel validates → `repository.updateTransaction()` called
8. On success → navigates back, list reflects changes

## TransactionFormUiState

```kotlin
data class TransactionFormUiState(
    val amount: String = "",         // Raw user input (e.g. "150.00")
    val type: TransactionType? = null, // null until user selects
    val description: String = "",     // Raw user input
    val createdAt: Long = ...,        // Epoch millis, defaults to now
    val amountError: String? = null,
    val descriptionError: String? = null,
    val typeError: String? = null,
    val dateError: String? = null,
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,   // Only true during edit mode load
    val loadError: String? = null,    // Error loading existing transaction
    val saveError: String? = null,    // Error saving transaction
    val saveSuccess: Boolean = false, // Signal to navigate back
)
```

## Validation Rules

All validation runs on `save()` click. Errors clear immediately when the user corrects the field.

| Field | Rule | Error Message |
|-------|------|---------------|
| Amount | Required, must be > 0 cents | "Amount is required" / "Amount must be greater than zero" |
| Type | Must be INFLOW or OUTFLOW | "Select inflow or outflow" |
| Description | Required, max 500 chars | "Description is required" / "Maximum 500 characters" |
| Date | ≥ 2000-01-01, ≤ now + 1 day | "Date must be after 2000-01-01" / "Date cannot be in the future" |

## Save Behaviour

- Amount is parsed from dollar string to cents: `"150.50"` → `15050L`
- Description is trimmed of whitespace before saving
- Save button is disabled while:
  - `isSaving == true` (in-flight request)
  - `isLoading == true` (edit mode loading)
  - Any validation errors exist
- Duplicate save prevention: `isSaving` flag checked synchronously before proceeding
- On repository error: `saveError` is set, user can retry by tapping Save again

## Navigation Arguments

| Argument | Type | Required | Mode |
|----------|------|----------|------|
| `vaultId` | `String` | Yes | Both add and edit |
| `transactionId` | `String?` | No (null = add) | Add if null, Edit if present |

Passed via `SavedStateHandle` in `TransactionFormViewModel`. Never pass full objects — only IDs.

## Amount Format

Input accepts the regex pattern `^\d*\.?\d{0,2}$`:
- Empty string allowed (for clearing field)
- Digits with optional single decimal point
- Maximum 2 decimal places
- Input with >2 decimal places is rejected entirely (not truncated)
