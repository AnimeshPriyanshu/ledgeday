# Formatting Conventions

## Currency Formatting

**Single source of truth:** `:core:ui` → `CurrencyFormatter.format(cents: Long): String`

- Amounts are stored as `Long` representing cents (1/100 of the base currency unit)
- Output format: `"$1,234.56"` (USD hardcoded)
- Negative amounts are prefixed with `-`: `"-$500.00"`
- Always use `CurrencyFormatter` for any balance or amount display
- Do not reimplement formatting logic in feature modules

### Usage

```kotlin
import com.vaultledger.ui.util.CurrencyFormatter

Text(text = CurrencyFormatter.format(15000L)) // "$150.00"
Text(text = CurrencyFormatter.format(-500L))   // "-$5.00"
```

### Transaction amounts

For transaction rows that show inflow/outflow, prefix with `+` or `-`:

```kotlin
val prefix = if (type == TransactionType.INFLOW) "+" else "-"
"$prefix${CurrencyFormatter.format(amount)}" // "+$150.00" or "-$50.00"
```

## Date Formatting

**Single source of truth:** `:core:ui` → `DateFormatter.format(epochMillis: Long): String`

- Timestamps are stored as `Long` (epoch milliseconds, UTC)
- Output format: `"Jul 15, 2026"` (device locale-aware)
- Always use `DateFormatter` for date display in transaction rows, vault items, etc.

### Date grouping in lists

Transaction lists group items by date using a full-date header format:
`"EEEE, MMMM d, yyyy"` (e.g. `"Wednesday, July 15, 2026"`)

This is defined locally in `TransactionList.kt` because it serves a different purpose
(date section headers) than standard date display.

## Balance Display

- Use `BalanceHeader` component in `:feature:transactions`
- Color is theme-aware: primary (positive), error (negative), onSurfaceVariant (zero)
- Renders via `CurrencyFormatter` internally

## Shared Dimensions

**Location:** `:core:ui` → `Dimensions`

| Constant | Value | Usage |
|---|---|---|
| `SpacingXSmall` | 4.dp | Tight spacing between elements |
| `SpacingSmall` | 8.dp | Default vertical/horizontal spacing |
| `SpacingMedium` | 16.dp | Section gaps, dialog spacing |
| `SpacingLarge` | 24.dp | Screen edge padding |
| `SpacingXLarge` | 32.dp | Large section gaps |
| `CardPadding` | 16.dp | Inner padding for Card composables |
| `ListHorizontalPadding` | 16.dp | Horizontal padding for LazyColumn |
| `ListContentPadding` | 8.dp | Vertical padding for LazyColumn content |

Prefer these constants over raw `.dp` values to maintain visual consistency.
