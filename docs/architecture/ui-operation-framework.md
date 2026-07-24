# UI Operation Framework

## Overview

The UI Operation Framework provides a reusable pattern for managing asynchronous UI state
(loading, success, error, empty) and retry across all ViewModels in the application.

It eliminates duplicated `try/catch` blocks, Flow collection boilerplate, and inconsistent
error handling by centralising the common lifecycle into a single class: `UiOperation`.

## Core Class: `UiOperation<T>`

**Location:** `core/ui/src/main/kotlin/com/vaultledger/ui/common/UiOperation.kt`

`UiOperation<T>` manages a `StateFlow<UiState<T>>` that represents the state of a single
asynchronous data source. ViewModels create an instance, pass their coroutine scope, and
delegate state management to it.

### API

```kotlin
class UiOperation<T>(scope: CoroutineScope) {

    // Observable UI state
    val state: StateFlow<UiState<T>>

    // Observe a reactive Flow source (e.g. Room DAO)
    fun observe(
        provide: suspend () -> Flow<T>,
        map: (T) -> UiState<T> = { UiState.Success(it) },
    )

    // Execute a one-shot suspend operation (e.g. create, delete)
    fun launch(block: suspend () -> Unit)

    // Retry the last observe() call
    fun retry()
}
```

### State Transitions

```
     ┌──────────┐
     │  Loading  │ ◄──── initial / retry
     └────┬─────┘
          │
     ┌────▼─────┐
     │   Empty   │ ◄──── map returns Empty (e.g. list is empty)
     └──────────┘

     ┌──────────┐
     │  Loading  │
     └────┬─────┘
          │
     ┌────▼──────┐
     │  Success  │ ◄──── map returns Success(data)
     └───────────┘

     ┌──────────┐
     │  Loading  │
     └────┬─────┘
          │
     ┌────▼──────┐     retry()     ┌──────────┐
     │   Error   │ ──────────────► │  Loading  │
     └───────────┘                 └──────────┘
```

### Retry Lifecycle

1. `retry()` cancels the previous observe coroutine via `Job.cancel()`.
2. It launches a **new** coroutine that sets `state` to `Loading`.
3. The new coroutine calls the `provide` function to obtain a fresh `Flow`.
4. The Flow is mapped, caught, and collected as before.
5. Because the previous `Job` is cancelled before the new one starts, **no duplicate
   collectors** can exist.

Important: `retry()` only works for operations started via `observe()`. One-shot
`launch()` operations cannot be retried — they are fire-and-forget.

## How Future ViewModels Should Use It

### Pattern for a Flow-observed list

```kotlin
@HiltViewModel
class VaultListViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
) : ViewModel() {

    private val operation = UiOperation<List<Vault>>(viewModelScope)
    val uiState: StateFlow<UiState<List<Vault>>> = operation.state

    init {
        loadVaults(workspaceId)
    }

    private fun loadVaults(workspaceId: String) {
        operation.observe(
            provide = { vaultRepository.getVaultsByWorkspaceId(workspaceId) },
            map = { vaults ->
                if (vaults.isEmpty()) UiState.Empty else UiState.Success(vaults)
            },
        )
    }

    fun createVault(name: String, description: String) {
        operation.launch {
            vaultRepository.createVault(workspaceId, name, description)
        }
    }

    fun retry() = operation.retry()
}
```

### Screen wiring

```kotlin
val uiState by viewModel.uiState.collectAsState()

when (val state = uiState) {
    is UiState.Loading -> LoadingIndicator()
    is UiState.Empty   -> EmptyState()
    is UiState.Success -> ContentList(state.data)
    is UiState.Error   -> ErrorState(viewModel::retry)
}
```

### Multiple operations in one ViewModel

If a ViewModel needs to manage several independent data sources (e.g. a list and a
detail), create multiple `UiOperation` instances:

```kotlin
private val listOperation = UiOperation<List<Item>>(viewModelScope)
private val detailOperation = UiOperation<ItemDetail>(viewModelScope)
```

## Design Decisions

### Composition over inheritance

`UiOperation` is a standalone class that ViewModels compose, not a base class they
inherit. This avoids the fragile-base-class problem and lets ViewModels pick and choose
which operations they need.

### No repository changes required

The framework lives entirely in the UI layer (`core:ui`). Repository interfaces and
implementations are untouched. This preserves Clean Architecture boundaries.

### Error messages

Error messages come from the exception itself (`e.message`), falling back to a generic
string. Feature-specific messages are handled by the exception type thrown by the
repository, not by the framework.

### Why not a base ViewModel class?

A base class would:
- Force all ViewModels into a single hierarchy.
- Make it harder to add non-standard behaviour.
- Require base-class constructor changes for future needs.

Composition with `UiOperation` avoids all of these.

## When Retry Should Be Used

- **Database errors** — Room failures (corruption, migration, I/O) are the primary case.
  These are catastrophic and non-recoverable without retrying the query.
- **Network errors** — A future sync feature can use the same `UiOperation` pattern by
  providing a network-backed Flow.
- **One-shot operations** — `launch()` does not support retry because the operation may
  have side effects. These errors are surfaced in the UI but must be retried manually
  by the user re-invoking the action.

## Framework Boundaries

| Layer | What it owns |
|---|---|
| `UiOperation` (core:ui) | Loading/success/error state machine, retry lifecycle, Flow observation |
| ViewModel (feature) | Feature-specific mapping (empty check, data transformation), repository calls |
| Screen (feature) | Rendering each UiState branch, calling retry() on user action |
## Migration Guide

### Existing ViewModel

```kotlin
// Before
private val _uiState = MutableStateFlow<UiState<List<Widget>>>(UiState.Loading)
val uiState: StateFlow<UiState<List<Widget>>> = _uiState

init {
    viewModelScope.launch {
        repository.getWidgets()
            .map { ... }
            .catch { _uiState.value = UiState.Error(...) }
            .collect { _uiState.value = it }
    }
}

fun createWidget(name: String) {
    viewModelScope.launch {
        try { repository.createWidget(name) }
        catch (e: Exception) { _uiState.value = UiState.Error(...) }
    }
}
```

```kotlin
// After
private val operation = UiOperation<List<Widget>>(viewModelScope)
val uiState: StateFlow<UiState<List<Widget>>> = operation.state

init {
    operation.observe(
        provide = { repository.getWidgets() },
        map = { widgets ->
            if (widgets.isEmpty()) UiState.Empty else UiState.Success(widgets)
        },
    )
}

fun createWidget(name: String) {
    operation.launch { repository.createWidget(name) }
}
```

## Test Patterns

### Testing retry

```kotlin
@Test
fun `retry after error reloads data`() = runTest(testDispatcher) {
    repository.throwOnGetAll = true
    val vm = MyViewModel(repository)

    vm.uiState.test {
        assertEquals(UiState.Loading, awaitItem())
        assertInstanceOf(UiState.Error::class.java, awaitItem())

        repository.throwOnGetAll = false
        vm.retry()

        assertEquals(UiState.Loading, awaitItem())
        assertEquals(UiState.Empty, awaitItem())
        cancelAndIgnoreRemainingEvents()
    }
}
```

### Testing multiple retries

```kotlin
@Test
fun `multiple retry calls produce clean state`() = runTest(testDispatcher) {
    repository.throwOnGetAll = true
    val vm = MyViewModel(repository)

    vm.uiState.test {
        assertEquals(UiState.Loading, awaitItem())
        assertInstanceOf(UiState.Error::class.java, awaitItem())

        repository.throwOnGetAll = false
        vm.retry()
        vm.retry()
        vm.retry()

        assertEquals(UiState.Loading, awaitItem())
        assertEquals(UiState.Success::class.java, awaitItem())
        cancelAndIgnoreRemainingEvents()
    }
}
```

## Related Files

| File | Purpose |
|---|---|
| `core/ui/.../common/UiOperation.kt` | Framework implementation |
| `core/ui/.../common/UiState.kt` | Sealed interface for UI states |
| `feature/workspace/.../WorkspaceListViewModel.kt` | First adopter (reference implementation) |
| `feature/workspace/.../WorkspaceListViewModelTest.kt` | Tests for observe, launch, retry |
