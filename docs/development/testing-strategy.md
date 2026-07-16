# Testing Strategy

## Test Pyramid

```
        ╱╲
       ╱ E2E ╲         ← Manual + Compose UI tests (few)
      ╱────────╲
     ╱  Integration ╲   ← Repository + Sync tests
    ╱────────────────╲
   ╱    Unit Tests     ╲  ← Domain logic, ViewModels (many)
  ╱──────────────────────╲
```

## Unit Tests

- **What**: Use cases, ViewModels, domain models, balance calculation
- **How**: JUnit 5 + MockK + Turbine (for StateFlow testing)
- **Coverage target**: 90%+ for domain layer

```kotlin
@Test
fun `adding inflow transaction increases balance`() {
    val result = calculateBalance(listOf(inflow(100)))
    assertEquals(100L, result)
}
```

## Integration Tests

- **What**: Repository with fake data sources, sync logic
- **How**: Room in-memory database + fake Firestore

## UI Tests

- **What**: Critical user journeys (add transaction, view balance)
- **How**: Compose UI Test with `createComposeRule()`

## What We Don't Test

- Firebase or Room library internals
- Compose framework internals
- Trivial Composables without logic
