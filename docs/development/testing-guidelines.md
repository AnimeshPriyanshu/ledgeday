# Testing Guidelines — Vault Ledger

Single source of truth for all unit testing conventions.

---

## 1. ViewModel Testing Pattern

### Rule: Create the ViewModel inside `runTest`

Always instantiate the ViewModel **inside** the `runTest` lambda, never in `@BeforeEach`.

**Why:** `runTest` drains all pending coroutines on the test dispatcher before executing its lambda body. If the ViewModel is created in `setUp()`, its `init` block launches a coroutine that runs during this drain, consuming the initial `UiState.Loading` emission before Turbine starts collecting. Creating the ViewModel inside `runTest` ensures the initial `Loading` state is observable.

```kotlin
// ✅ Correct
@Test
fun `emits Loading then Empty`() = runTest(testDispatcher) {
    val vm = WorkspaceListViewModel(repository)
    vm.uiState.test {
        assertEquals(UiState.Loading, awaitItem())
        assertEquals(UiState.Empty, awaitItem())
        cancel()
    }
}

// ❌ Wrong — ViewModel created in setUp() → Loading consumed before test collects
@BeforeEach
fun setUp() {
    viewModel = WorkspaceListViewModel(repository) // don't do this
}
```

---

## 2. Coroutine Testing Conventions

### Dispatchers

```kotlin
private val testDispatcher = StandardTestDispatcher()

@BeforeEach
fun setUp() {
    Dispatchers.setMain(testDispatcher)
}

@AfterEach
fun tearDown() {
    Dispatchers.resetMain()
}
```

- Use `StandardTestDispatcher` (not `UnconfinedTestDispatcher`).
- Always call `Dispatchers.setMain(testDispatcher)` before tests and `resetMain()` after.
- `StandardTestDispatcher` queues coroutines and runs them at controlled suspension points, matching production-like async behavior.
- `UnconfinedTestDispatcher` runs all coroutines synchronously, which can mask ordering bugs and should only be used for trivial script-like tests.

### Advancing Time

Use `advanceUntilIdle()` after triggering async actions:

```kotlin
vm.createWorkspace("New")
advanceUntilIdle()
assertEquals(UiState.Success..., awaitItem())
```

**Why:** `advanceUntilIdle()` runs all pending and newly-launched coroutines to completion on the test dispatcher. It is deterministic and fast. Do not use `delay()`, `Thread.sleep()`, or `yield()` for synchronization.

---

## 3. StateFlow / Turbine Conventions

### Always call `cancel()`

Every Turbine `test {}` block must end with `cancel()`:

```kotlin
vm.uiState.test {
    assertEquals(UiState.Loading, awaitItem())
    assertEquals(UiState.Success, awaitItem())
    cancel()  // ← required
}
```

**Why:** Turbine collects indefinitely until cancelled. Without `cancel()`, the test `runTest` scope will time out waiting for the flow to complete.

### Use `skipItems()` for irrelevant initial emissions

| Scenario | Use |
|----------|-----|
| Testing all states from start | `awaitItem()` for each emission |
| Testing only after initialization | `skipItems(1)` to skip `Loading` |
| Testing after initial Success + action | `skipItems(2)` to skip `Loading` + initial `Success` |

### Default timeout

Turbine's default timeout is 1 second. If an emission is expected to take longer, provide an explicit timeout:

```kotlin
awaitItem(timeout = Duration.seconds(3))
```

---

## 4. Fake Repository Conventions

### Always prefer fakes over mocks for ViewModel tests

```kotlin
class FakeWorkspaceRepository : WorkspaceRepository {

    private val _workspaces = MutableStateFlow(mutableMapOf<String, Workspace>())

    override fun getAllWorkspaces(): Flow<List<Workspace>> {
        return _workspaces.map { it.values.sortedBy { w -> w.createdAt } }
    }

    override suspend fun createWorkspace(name: String, description: String): Workspace {
        val workspace = Workspace(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            createdAt = System.currentTimeMillis(),
        )
        _workspaces.value = _workspaces.value.toMutableMap().apply { put(workspace.id, workspace) }
        return workspace
    }

    // ... remaining methods
}
```

**Why:**
- Fakes are deterministic — no mock setup/verification errors.
- Fakes exercise the same Flow-based API the production repository uses.
- Fakes make it trivial to set up specific states (`repository.createWorkspace("X", "")`).

### Rules for fakes

| Rule | Rationale |
|------|-----------|
| Use `MutableStateFlow` for the backing store | Mirrors how Room DAOs emit data reactively |
| Return `Flow` from read methods | Same contract as the real repository |
| Use real `UUID.randomUUID()` | IDs behave naturally |
| Methods must be `suspend` where the interface declares `suspend` | Preserves the async contract |

---

## 5. Assertion Conventions

### Never use Kotlin's built-in `assert()`

```kotlin
assert(...)              // ❌ — only works with -ea JVM flag (disabled by Gradle)
assertTrue(...)          // ✅
assertEquals(...)        // ✅
assertInstanceOf(...)    // ✅
```

### Use the most specific JUnit Jupiter assertion

| Check | Use |
|-------|-----|
| Equality | `assertEquals(expected, actual)` |
| Type check | `assertInstanceOf(KClass::class.java, value)` |
| Boolean condition | `assertTrue(condition)` |
| Boolean false | `assertFalse(condition)` |
| Null | `assertNull(value)` |
| Non-null | `assertNotNull(value)` |
| Exception | `assertThrows<Exception> { ... }` |

**Why specific assertions matter:** They produce better failure messages. `assertEquals(1, data.size)` reports `expected: <1> but was: <2>`, while `assertTrue(data.size == 1)` only reports `expected: <true> but was: <false>`.

### Handle raw types from `assertInstanceOf`

`assertInstanceOf` returns the erased type, so `.data` on `UiState.Success<*>` resolves as `Any?`. Cast explicitly:

```kotlin
assertInstanceOf(UiState.Success::class.java, state)
val data = (state as UiState.Success<*>).data as List<*>
assertEquals(1, data.size)
```

---

## 6. Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Test class | `{ClassUnderTest}Test` | `WorkspaceListViewModelTest` |
| Test method | Backtick sentence describing behavior | `` `init emits Loading then Empty` `` |
| Fake class | `Fake{InterfaceName}` | `FakeWorkspaceRepository` |
| Test data | Descriptive local variables | `created`, `inflow`, `expected` |

### Test method style

Use backtick names with full sentences in the pattern:

```
`{initial condition} emits {expected state} when {trigger}`
`{action} updates state from {before} to {after}`
`{action} does not change state when {condition}`
```

---

## 7. Test Structure

### Standard template

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class SomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeSomeRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeSomeRepository()
        // Do NOT create the ViewModel here
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state tests ──

    @Test
    fun `emits Loading then Empty when repository has no data`() = runTest(testDispatcher) {
        val vm = SomeViewModel(repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            assertEquals(UiState.Empty, awaitItem())
            cancel()
        }
    }

    @Test
    fun `emits Loading then Success when repository has data`() = runTest(testDispatcher) {
        repository.seed(/* ... */)
        val vm = SomeViewModel(repository)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            val success = awaitItem()
            assertInstanceOf(UiState.Success::class.java, success)
            // assert on success.data
            cancel()
        }
    }

    // ── Action tests ──

    @Test
    fun `createWorkspace adds workspace to list`() = runTest(testDispatcher) {
        val vm = SomeViewModel(repository)

        vm.uiState.test {
            skipItems(1)  // skip Loading

            vm.createWorkspace("New")
            advanceUntilIdle()

            val state = awaitItem()
            assertInstanceOf(UiState.Success::class.java, state)
            // assert new data present
            cancel()
        }
    }

    @Test
    fun `deleteWorkspace removes workspace from list`() = runTest(testDispatcher) {
        repository.seed(/* ... */)
        val vm = SomeViewModel(repository)

        vm.uiState.test {
            skipItems(2)  // skip Loading + initial Success

            vm.deleteWorkspace(createdId)
            advanceUntilIdle()

            assertEquals(UiState.Empty, awaitItem())
            cancel()
        }
    }

    // ── Error tests ──

    @Test
    fun `emits Error when repository throws`() = runTest(testDispatcher) {
        val failingRepo = FakeFailingRepository()
        val vm = SomeViewModel(failingRepo)

        vm.uiState.test {
            assertEquals(UiState.Loading, awaitItem())
            val error = awaitItem()
            assertTrue(error is UiState.Error)
            cancel()
        }
    }
}
```

---

## 8. Definition of Done for Tests

A test is complete when:

- [ ] Covers all `UiState` variants the ViewModel can emit (Loading, Success, Empty, Error)
- [ ] Uses the most specific JUnit Jupiter assertion for each check
- [ ] Uses `cancel()` at the end of every Turbine `test {}` block
- [ ] Creates the ViewModel inside `runTest`, not in `setUp()`
- [ ] Uses `Fake*Repository` instead of mocks (unless testing error propagation)
- [ ] No use of Kotlin's `assert()`, `delay()`, `Thread.sleep()`, or `UnconfinedTestDispatcher`
- [ ] Test method name is a backtick sentence describing observable behavior
- [ ] Test is deterministic — same result on every run regardless of timing
- [ ] No production code was modified to make the test pass

---

## 9. Common Pitfalls

### Pitfall 1: Creating ViewModel outside `runTest`

```kotlin
// ❌
@BeforeEach
fun setUp() {
    viewModel = WorkspaceListViewModel(repository)
}

@Test
fun `emits Loading`() = runTest(testDispatcher) {
    viewModel.uiState.test {
        assertEquals(UiState.Loading, awaitItem())  // Fails — got Empty
    }
}
```

**Why it fails:** `runTest` runs pending coroutines before the lambda. The ViewModel's init completes (Loading → Empty) before `test {}` collects.

**Fix:** Create the ViewModel inside `runTest`.

---

### Pitfall 2: Using Kotlin `assert()`

```kotlin
// ❌
assert(awaitItem() is UiState.Loading)

// ✅
assertEquals(UiState.Loading, awaitItem())
```

**Why it fails:** Kotlin's `assert()` only throws when JVM assertions are enabled (`-ea` flag). Gradle does not set this flag by default, so the assertion silently passes.

**Fix:** Always use JUnit Jupiter assertions (`assertEquals`, `assertTrue`, `assertInstanceOf`, etc.).

---

### Pitfall 3: Forgetting `cancel()`

```kotlin
// ❌ — will timeout
vm.uiState.test {
    assertEquals(UiState.Loading, awaitItem())
    // forgot cancel()
}

// ✅
vm.uiState.test {
    assertEquals(UiState.Loading, awaitItem())
    cancel()
}
```

**Why it fails:** Turbine's `test` collects indefinitely. Without `cancel()`, the test scope's coroutine never completes, causing a `TurbineTimeoutCancellationException`.

**Fix:** Always call `cancel()` at the end of every `test {}` block.

---

### Pitfall 4: Using `UnconfinedTestDispatcher` unnecessarily

```kotlin
// ❌
private val testDispatcher = UnconfinedTestDispatcher()

// ✅
private val testDispatcher = StandardTestDispatcher()
```

**Why it's wrong:** `UnconfinedTestDispatcher` runs all coroutines synchronously, eliminating all async behavior. This can make tests pass even when the production code has real ordering bugs. It also makes `advanceUntilIdle()` a no-op, hiding incorrect test structure.

**Fix:** Use `StandardTestDispatcher` and control execution with `advanceUntilIdle()`.

---

### Pitfall 5: Relying on timing or delays

```kotlin
// ❌ — fragile and slow
delay(500)
assertEquals(UiState.Success, awaitItem())

// ✅ — deterministic
advanceUntilIdle()
assertEquals(UiState.Success, awaitItem())
```

**Why it's wrong:** `delay()` introduces real wall-clock waiting, making tests slow and flaky. The exact delay needed varies by machine and load.

**Fix:** Use `advanceUntilIdle()` or `runCurrent()` to progress the test dispatcher deterministically.