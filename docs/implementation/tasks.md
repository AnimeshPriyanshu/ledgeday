# Development Tasks — Vault Ledger MVP (Optimized)

Each task is 30 minutes to 3 hours.
Tasks within a milestone can be parallelized unless dependencies state otherwise.

---

## M1 — Project Foundation (6 tasks, unchanged)

---

### T1.1: Create settings.gradle.kts with module includes

- **Goal**: Define the multi-module project structure in the root `settings.gradle.kts`
- **Files affected**: `settings.gradle.kts`
- **Dependencies**: None
- **Expected result**: All 9 modules included and resolvable by Gradle
- **Definition of Done**:
  - `settings.gradle.kts` declares modules: `:app`, `:core:domain`, `:core:data`, `:core:ui`, `:core:sync` (placeholder), `:feature:workspace`, `:feature:vault`, `:feature:transactions`, `:feature:settings`
  - `./gradlew projects` lists all modules

---

### T1.2: Create version catalog with all dependencies

- **Goal**: Single-source all dependency versions in `gradle/libs.versions.toml`
- **Files affected**: `gradle/libs.versions.toml`
- **Dependencies**: None
- **Expected result**: Catalog contains Room, Hilt, Compose BOM, Compose Navigation, Firebase Auth, kotlinx-serialization, WorkManager, JUnit 5, Turbine, MockK, ktlint, Detekt
- **Definition of Done**:
  - All libraries listed in version catalog
  - Libraries organized by group (android, compose, di, database, firebase, testing, quality)

---

### T1.3: Create empty module build.gradle.kts files

- **Goal**: Each module has a compilable build configuration with correct dependencies
- **Files affected**: `app/build.gradle.kts`, `core/domain/build.gradle.kts`, `core/data/build.gradle.kts`, `core/ui/build.gradle.kts`, `core/sync/build.gradle.kts`, `feature/workspace/build.gradle.kts`, `feature/vault/build.gradle.kts`, `feature/transactions/build.gradle.kts`, `feature/settings/build.gradle.kts`
- **Dependencies**: T1.1, T1.2
- **Expected result**: Every module compiles independently
- **Definition of Done**:
  - Each module has a `plugins {}` block and `dependencies {}` block
  - Feature modules depend on `:core:domain`, `:core:data`, `:core:ui`
  - `:app` depends on all feature modules, `:core:data`, `:core:ui`, `:core:sync`
  - `./gradlew :app:assembleDebug` succeeds (modules may be empty)

---

### T1.4: Create Application class with Hilt

- **Goal**: Configure `@HiltAndroidApp` as the application entry point
- **Files affected**: `app/src/main/java/com/vaultledger/VaultLedgerApp.kt`
- **Dependencies**: T1.3
- **Expected result**: Hilt annotation processing runs on build
- **Definition of Done**:
  - `VaultLedgerApp` class annotated with `@HiltAndroidApp`
  - AndroidManifest.xml declares `VaultLedgerApp` as `android:name`
  - `./gradlew :app:assembleDebug` succeeds with Hilt processing

---

### T1.5: Create empty MainActivity

- **Goal**: App launches and displays a basic Compose surface
- **Files affected**: `app/src/main/java/com/vaultledger/MainActivity.kt`
- **Dependencies**: T1.4
- **Expected result**: Empty Compose screen renders on emulator
- **Definition of Done**:
  - `MainActivity` annotated with `@AndroidEntryPoint`
  - `setContent` renders `MaterialTheme { }` using default Material3 theme (custom theme comes in M5)
  - App launches on emulator without crash

---

### T1.6: Configure ktlint and Detekt

- **Goal**: Quality gate tools prevent style and complexity issues
- **Files affected**: `build.gradle.kts` (root), `gradle/libs.versions.toml` (ktlint/detekt entries)
- **Dependencies**: T1.3
- **Expected result**: `./gradlew ktlintCheck` and `./gradlew detekt` run without errors
- **Definition of Done**:
  - ktlint plugin applied at root level
  - Detekt plugin applied with baseline config
  - Both tasks pass on the empty project

---

## M2 — Domain Layer (4 tasks, was part of old M2)

> Domain models come FIRST. Room entities in M3 will mirror these domain classes.

---

### T2.1: Define domain models

- **Goal**: Pure Kotlin data classes with no Android dependencies
- **Files affected**:
  - `core/domain/src/main/java/com/vaultledger/domain/model/Workspace.kt`
  - `core/domain/src/main/java/com/vaultledger/domain/model/Vault.kt`
  - `core/domain/src/main/java/com/vaultledger/domain/model/Transaction.kt`
  - `core/domain/src/main/java/com/vaultledger/domain/model/TransactionType.kt`
- **Dependencies**: None
- **Expected result**: Domain models compile without Android imports
- **Definition of Done**:
  - `Workspace`, `Vault`, `Transaction` are data classes
  - `TransactionType` enum: `INFLOW`, `OUTFLOW`
  - `Transaction.amount` is `Long`
  - No import from `androidx.room`, `android.database`, or any Android package

---

### T2.2: Define repository interfaces

- **Goal**: Contracts for data access in the domain layer
- **Files affected**:
  - `core/domain/src/main/java/com/vaultledger/domain/repository/WorkspaceRepository.kt`
  - `core/domain/src/main/java/com/vaultledger/domain/repository/VaultRepository.kt`
  - `core/domain/src/main/java/com/vaultledger/domain/repository/TransactionRepository.kt`
- **Dependencies**: T2.1
- **Expected result**: Repository interfaces use domain models only
- **Definition of Done**:
  - `WorkspaceRepository`: `getAll(): Flow<List<Workspace>>`, `getById(id): Workspace?`, `suspend create(name): Workspace`, `suspend update(Workspace)`, `suspend delete(id)`
  - `VaultRepository`: same pattern scoped to `workspaceId`, plus `updateBalance(id, balance)`
  - `TransactionRepository`: same pattern scoped to `vaultId`, plus `getBalance(vaultId): Flow<Long>`
  - All return types use domain models, not entities

---

### T2.3: Define use cases

- **Goal**: Business logic entry points
- **Files affected**:
  - `core/domain/src/main/java/com/vaultledger/domain/usecase/CalculateBalanceUseCase.kt`
- **Dependencies**: T2.1
- **Expected result**: Use case compiles and produces correct balance
- **Definition of Done**:
  - `CalculateBalanceUseCase`: `operator fun invoke(transactions: List<Transaction>): Long`
  - Balance = `SUM(inflow amounts) - SUM(outflow amounts)` using positive-only amounts
  - Unit test verifies: all INFLOW, all OUTFLOW, mixed, empty list

---

### T2.4: Create shared infrastructure utilities

- **Goal**: Reusable composables and helpers across all features
- **Files affected**:
  - `core/ui/src/main/java/com/vaultledger/ui/common/UiState.kt`
  - `core/ui/src/main/java/com/vaultledger/ui/util/CurrencyFormatter.kt`
  - `core/ui/src/main/java/com/vaultledger/ui/util/DateFormatter.kt`
  - `core/ui/src/main/java/com/vaultledger/ui/util/UuidGenerator.kt`
- **Dependencies**: None
- **Expected result**: Shared utilities available to all feature modules
- **Definition of Done**:
  - `UiState<T>` sealed interface: `Loading | Success(data) | Error(message) | Empty`
  - `CurrencyFormatter.format(cents: Long): String` — outputs "$1,234.56" (USD hardcoded)
  - `DateFormatter.formatDate(epochMillis: Long): String` — outputs "Jul 15, 2026"
  - `UuidGenerator.generate(): String` — wraps `UUID.randomUUID().toString()`
  - All are pure Kotlin (no Android imports) except UiState which is `@Composable`-friendly

---

## M3 — Data Layer (4 tasks, was part of old M2)

> Room entities now mirror the domain models from M2.

---

### T3.1: Define Room entities

- **Goal**: Room entities that mirror domain models
- **Files affected**:
  - `core/data/src/main/java/com/vaultledger/data/local/entity/WorkspaceEntity.kt`
  - `core/data/src/main/java/com/vaultledger/data/local/entity/VaultEntity.kt`
  - `core/data/src/main/java/com/vaultledger/data/local/entity/TransactionEntity.kt`
- **Dependencies**: T2.1 (domain models as reference), T1.3
- **Expected result**: All three entities compile with Room annotations
- **Definition of Done**:
  - Entities match domain models but add Room annotations (`@Entity`, `@PrimaryKey`, `@ForeignKey`)
  - `TransactionEntity.vaultId` — `@ForeignKey(entity = VaultEntity, parent = id, child = vaultId, onDelete = CASCADE)`
  - `VaultEntity.workspaceId` — `@ForeignKey(entity = WorkspaceEntity, parent = id, child = workspaceId, onDelete = CASCADE)`
  - MVP fields only: `category`, `lastModified`, `createdBy`, `synced` excluded

---

### T3.2: Define DAOs

- **Goal**: Data access objects for all entities
- **Files affected**:
  - `core/data/src/main/java/com/vaultledger/data/local/dao/WorkspaceDao.kt`
  - `core/data/src/main/java/com/vaultledger/data/local/dao/VaultDao.kt`
  - `core/data/src/main/java/com/vaultledger/data/local/dao/TransactionDao.kt`
- **Dependencies**: T3.1, T2.4
- **Expected result**: All DAOs compile and pass in-memory tests
- **Definition of Done**:
  - `WorkspaceDao`: `@Insert`, `@Update`, `@Delete`, `@Query getAll(): Flow<List>`, `@Query getById(id)`
  - `VaultDao`: same plus `@Query getByWorkspaceId`, `@Query updateBalance(id, balance)`
  - `TransactionDao`: same plus `@Query getByVaultId`, balance aggregation: `SELECT COALESCE(SUM(CASE WHEN type = 'INFLOW' THEN amount ELSE -amount END), 0) FROM transactions WHERE vaultId = :vaultId`
  - DAO test with in-memory Room verifies each operation

---

### T3.3: Create VaultLedgerDatabase

- **Goal**: Room database class tying all entities and DAOs together
- **Files affected**: `core/data/src/main/java/com/vaultledger/data/local/VaultLedgerDatabase.kt`
- **Dependencies**: T3.1, T3.2
- **Expected result**: Room database compiles with all 3 entities
- **Definition of Done**:
  - `@Database(entities = [WorkspaceEntity, VaultEntity, TransactionEntity], version = 1)`
  - Abstract DAO accessors: `workspaceDao()`, `vaultDao()`, `transactionDao()`
  - `Room.inMemoryDatabaseBuilder` test creates database without migration errors

---

## M4 — Repositories & DI (6 tasks, was old M3)

> Split old T3.3 (TransactionRepositoryImpl) into CRUD + balance cache.

---

### T4.1: Implement WorkspaceRepositoryImpl

- **Goal**: Concrete repository mapping Room entities to domain models
- **Files affected**: `core/data/src/main/java/com/vaultledger/data/repository/WorkspaceRepositoryImpl.kt`
- **Dependencies**: T3.3 (database), T2.2 (repository interface), T2.1 (domain models), T2.4 (UuidGenerator)
- **Expected result**: Workspace CRUD works through repository abstraction
- **Definition of Done**:
  - Implements `WorkspaceRepository`
  - Maps `WorkspaceEntity` ↔ `Workspace` via mapping extension functions
  - `getAll()` returns `Flow.map { toDomain() }`
  - `create()` inserts entity with generated UUID and returns domain model
  - Unit test with in-memory Room verifies all operations

---

### T4.2: Implement VaultRepositoryImpl

- **Goal**: Concrete vault repository
- **Files affected**: `core/data/src/main/java/com/vaultledger/data/repository/VaultRepositoryImpl.kt`
- **Dependencies**: T3.3, T2.2, T2.1, T2.4
- **Expected result**: Vault CRUD scoped to workspace
- **Definition of Done**:
  - Implements `VaultRepository`
  - `getByWorkspaceId()` returns vaults for a specific workspace
  - `create()` generates UUID, inserts with initial balance = 0
  - Unit test verifies CRUD scoped to workspace

---

### T4.3: Implement TransactionRepositoryImpl — CRUD

- **Goal**: Transaction insert, update, delete operations
- **Files affected**: `core/data/src/main/java/com/vaultledger/data/repository/TransactionRepositoryImpl.kt`
- **Dependencies**: T3.3, T2.2, T2.1, T2.4
- **Expected result**: Transaction CRUD works through repository abstraction
- **Definition of Done**:
  - Implements `TransactionRepository`
  - `getByVaultId()` returns transaction list ordered by date descending
  - `insert()` persists a new transaction entity
  - `update()` modifies an existing transaction
  - `delete()` removes a transaction by ID
  - Unit test verifies each operation

---

### T4.4: Add balance cache atomicity (split from old T3.3)

- **Goal**: Vault.balance stays synchronized with transaction data on every mutation
- **Files affected**: `core/data/src/main/java/com/vaultledger/data/repository/TransactionRepositoryImpl.kt`
- **Dependencies**: T4.3
- **Expected result**: Balance cache updates atomically on every insert/update/delete
- **Definition of Done**:
  - `insert()` wraps in `@Transaction`: insert transaction → recalculate balance → `vaultDao.updateBalance()`
  - `update()` wraps in `@Transaction`: update transaction → recalculate balance → update cache
  - `delete()` wraps in `@Transaction`: delete transaction → recalculate balance → update cache
  - `getBalance()` returns cached `VaultEntity.balance` as `Flow<Long>`
  - Test verifies balance consistency after insert 3 → verify → update 1 → verify → delete 1 → verify
  - Test verifies rollback on DAO exception (balance unchanged)

---

### T4.5: Create Database Hilt module

- **Goal**: Provide Room database and DAOs through Hilt
- **Files affected**: `core/data/src/main/java/com/vaultledger/data/di/DatabaseModule.kt`
- **Dependencies**: T3.3, T4.1, T4.2, T4.4
- **Expected result**: Hilt provides singleton database and DAOs
- **Definition of Done**:
  - `@Module @InstallIn(SingletonComponent::class)`
  - `@Provides @Singleton` for `VaultLedgerDatabase` (uses `@ApplicationContext`)
  - `@Provides` for each DAO
  - Build compiles with Hilt

---

### T4.6: Create Repository Hilt module

- **Goal**: Bind repository interfaces to implementations
- **Files affected**: `core/data/src/main/java/com/vaultledger/data/di/RepositoryModule.kt`
- **Dependencies**: T4.5
- **Expected result**: Hilt injects concrete repositories where interfaces are used
- **Definition of Done**:
  - `@Module @InstallIn(SingletonComponent::class)`
  - `@Binds` for each `*Repository` interface → `*Impl`
  - Build compiles with Hilt
  - No duplicate bindings

---

## M5 — Navigation Shell & Theme (4 tasks, was old M4)

---

### T5.1: Define Material3 theme

- **Goal**: App-wide color scheme, typography, and shapes
- **Files affected**:
  - `core/ui/src/main/java/com/vaultledger/ui/theme/Color.kt`
  - `core/ui/src/main/java/com/vaultledger/ui/theme/Type.kt`
  - `core/ui/src/main/java/com/vaultledger/ui/theme/Theme.kt`
- **Dependencies**: T1.5 (MainActivity exists)
- **Expected result**: Custom Material3 theme available
- **Definition of Done**:
  - Light and dark color schemes defined with brand colors
  - Typography scale configured
  - `VaultLedgerTheme` composable wraps `MaterialTheme`
  - Theme applied in `MainActivity` (replacing inline default theme from T1.5)

---

### T5.2: Create NavHost with all routes

- **Goal**: Navigation graph matching the screen map with typed arguments
- **Files affected**: `app/src/main/java/com/vaultledger/navigation/NavGraph.kt`
- **Dependencies**: T5.1
- **Expected result**: All routes navigable with typed arguments
- **Definition of Done**:
  - `NavHost` with `composable()` for each route:
    - `/splash`, `/auth`
    - `/workspaces`, `/workspaces/create`
    - `/workspaces/{workspaceId}/vaults`, `/workspaces/{workspaceId}/vaults/create`
    - `/vaults/{vaultId}`, `/vaults/{vaultId}/transaction?transactionId={transactionId}`
    - `/settings`, `/settings/invite`
  - Route argument types: `workspaceId: String`, `vaultId: String`, `transactionId: String?` (optional, default `null`)
  - `./gradlew :app:assembleDebug` succeeds

---

### T5.3: Create placeholder screens for each route

- **Goal**: Every route has a screen composable that compiles and renders
- **Files affected**: One file per screen in the corresponding feature module:
  - `feature/workspace/.../WorkspaceListScreen.kt`, `CreateWorkspaceScreen.kt`
  - `feature/vault/.../VaultListScreen.kt`, `CreateVaultScreen.kt`
  - `feature/transactions/.../VaultDetailScreen.kt`, `TransactionFormScreen.kt`
  - `feature/settings/.../SettingsScreen.kt`
  - `core/ui/.../SplashScreen.kt`, `AuthScreen.kt`
- **Dependencies**: T5.2
- **Expected result**: All screens show their name as placeholder text
- **Definition of Done**:
  - Each placeholder is a `@Composable` that takes route arguments as parameters
  - Each renders a `Text(screenName)` in a `Scaffold`
  - All routes render placeholder text when navigated to

---

### T5.4: Add Scaffold with bottom navigation bar

- **Goal**: Bottom nav appears on workspace-internal screens
- **Files affected**: `app/src/main/java/com/vaultledger/navigation/AppScaffold.kt`
- **Dependencies**: T5.3
- **Expected result**: Bottom nav with Vaults and Settings tabs
- **Definition of Done**:
  - `Scaffold` with `bottomBar` containing `NavigationBar` and two `NavigationBarItem`s: Vaults, Settings
  - Bottom nav visible only when route is within a workspace (not auth, splash, or outside workspace)
  - Nav items navigate to correct routes
  - Selected state reflects current route

---

## M6 — Workspace Feature (4 tasks, was old M5)

> Merged T5.2 (screen) + T5.5 (delete) into a single screen task. Split delete is unnecessary complexity at the screen level.

---

### T6.1: Create WorkspaceListViewModel

- **Goal**: ViewModel managing workspace list state with loading/empty/error
- **Files affected**: `feature/workspace/src/main/java/com/vaultledger/feature/workspace/WorkspaceListViewModel.kt`
- **Dependencies**: T4.6 (repositories wired), T2.4 (UiState)
- **Expected result**: ViewModel emits correct UiState for all states
- **Definition of Done**:
  - `@HiltViewModel`, injects `WorkspaceRepository`
  - `StateFlow<UiState<List<Workspace>>>` using shared `UiState` sealed interface
  - Collects `repository.getAll()` and maps to UiState
  - `createWorkspace(name)` calls repository
  - `deleteWorkspace(id)` calls repository
  - Unit test verifies: Loading→Success, Loading→Empty, Loading→Error, create, delete

---

### T6.2: Create WorkspaceListScreen (with delete confirmation)

- **Goal**: Display workspaces, empty state, FAB, and delete
- **Files affected**: `feature/workspace/src/main/java/com/vaultledger/feature/workspace/WorkspaceListScreen.kt`
- **Dependencies**: T6.1, T5.4
- **Expected result**: Full workspace list UI including delete
- **Definition of Done**:
  - Collects `UiState` from ViewModel
  - `Loading`: centered spinner
  - `Success`: `LazyColumn` of workspace name cards
  - `Empty`: "No workspaces yet. Create one to get started."
  - `Error`: message with retry button
  - FAB triggers `onCreateWorkspace` callback
  - Long-press card shows delete confirmation dialog with cascade warning
  - Delete button is red/negative styled

---

### T6.3: Create workspace creation dialog

- **Goal**: Dialog to input workspace name with validation
- **Files affected**: `feature/workspace/src/main/java/com/vaultledger/feature/workspace/CreateWorkspaceDialog.kt`
- **Dependencies**: T6.2
- **Expected result**: User can create workspace with validation
- **Definition of Done**:
  - `AlertDialog` with name text field, Cancel and Create buttons
  - Validation: name required, max 100 characters
  - Validation error shown inline below text field
  - Create button disabled until name is valid
  - Calls `viewModel.createWorkspace(name)` on confirm
  - Dialog dismisses on success

---

### T6.4: Wire navigation from workspace list to vault list

- **Goal**: Tapping a workspace navigates to its vault list
- **Files affected**: `app/src/main/java/com/vaultledger/navigation/NavGraph.kt`
- **Dependencies**: T6.2, T5.2
- **Expected result**: Workspace tap → vault list with correct workspaceId
- **Definition of Done**:
  - `NavGraph` passes `navController` to `WorkspaceListScreen`
  - `onWorkspaceClick(id)` calls `navController.navigate("/workspaces/$id/vaults")`
  - Vault list screen receives `workspaceId` argument
  - Back navigation returns to workspace list

---

## M7 — Vault Feature (4 tasks, unchanged from old M6)

---

### T7.1: Create VaultListViewModel

- **Goal**: ViewModel managing vault list scoped to a workspace
- **Files affected**: `feature/vault/src/main/java/com/vaultledger/feature/vault/VaultListViewModel.kt`
- **Dependencies**: T4.6, T2.4
- **Expected result**: ViewModel emits vaults for a given workspaceId
- **Definition of Done**:
  - `@HiltViewModel`, injects `VaultRepository`
  - `StateFlow<UiState<List<Vault>>>`
  - `workspaceId` from `SavedStateHandle`
  - `createVault(name, description?, color?)` calls repository
  - Unit test verifies vaults scoped to workspaceId

---

### T7.2: Create VaultListScreen (with delete confirmation)

- **Goal**: Display vaults with name, balance, color indicator, delete
- **Files affected**: `feature/vault/src/main/java/com/vaultledger/feature/vault/VaultListScreen.kt`
- **Dependencies**: T7.1, T5.4
- **Expected result**: Vault list renders, delete works
- **Definition of Done**:
  - `Success`: `LazyColumn` of vault cards
  - Each card: color dot (left), name, formatted balance (right), optional description
  - FAB triggers `onCreateVault` callback
  - Tap card triggers `onVaultClick(vaultId)`
  - Long-press card shows delete confirmation with cascade warning

---

### T7.3: Create vault creation dialog

- **Goal**: Dialog to input vault name, optional description, optional color
- **Files affected**: `feature/vault/src/main/java/com/vaultledger/feature/vault/CreateVaultDialog.kt`
- **Dependencies**: T7.2
- **Expected result**: User can create vault with name and optional fields
- **Definition of Done**:
  - Dialog fields: name (required, max 100), description (optional, max 500), color (optional, grid of 8 preset colors)
  - Validation: name required
  - Calls `viewModel.createVault(name, description, color)` on confirm

---

### T7.4: Wire navigation from vault list to transaction list

- **Goal**: Tapping a vault navigates to its transaction list
- **Files affected**: `app/src/main/java/com/vaultledger/navigation/NavGraph.kt`
- **Dependencies**: T7.2, T5.2
- **Expected result**: Vault tap → transaction list with correct vaultId
- **Definition of Done**:
  - `onVaultClick(id)` calls `navController.navigate("/vaults/$id")`
  - Transaction list screen receives `vaultId` argument
  - Back navigation returns to vault list

---

## M8 — Transaction List & Balance (6 tasks, was old M7 + delete from old M8)

> Delete transaction (old T8.6) moved here because it modifies VaultDetailScreen, not the TransactionFormScreen.

---

### T8.1: Create VaultDetailViewModel (with delete)

- **Goal**: ViewModel managing transaction list, balance, and delete
- **Files affected**: `feature/transactions/src/main/java/com/vaultledger/feature/transactions/VaultDetailViewModel.kt`
- **Dependencies**: T4.6, T2.4
- **Expected result**: ViewModel emits transactions and balance reactively
- **Definition of Done**:
  - `@HiltViewModel`, injects `TransactionRepository`
  - `StateFlow<VaultDetailUiState>` combining transactions and balance via `combine`
  - `vaultId` from `SavedStateHandle`
  - `deleteTransaction(id)` calls repository
  - Unit test verifies state emissions

---

### T8.2: Create balance header composable

- **Goal**: Reusable balance display component
- **Files affected**: `feature/transactions/src/main/java/com/vaultledger/feature/transactions/BalanceHeader.kt`
- **Dependencies**: T8.1
- **Expected result**: Balance displays formatted amount with accent background
- **Definition of Done**:
  - Displays formatted balance using `CurrencyFormatter`
  - Large text: "Balance: $X,XXX.XX"
  - Background color accent (green for positive, neutral for zero)

---

### T8.3: Create transaction list composable

- **Goal**: Scrollable list of transaction rows
- **Files affected**: `feature/transactions/src/main/java/com/vaultledger/feature/transactions/TransactionList.kt`
- **Dependencies**: T8.2
- **Expected result**: Transaction list renders with date grouping
- **Definition of Done**:
  - `LazyColumn` with date-section headers and transaction rows
  - Empty state: "No transactions yet. Tap + to record your first transaction."
  - FAB triggers `onAddTransaction` callback

---

### T8.4: Create transaction row composable

- **Goal**: Reusable row showing transaction data with color coding
- **Files affected**: `feature/transactions/src/main/java/com/vaultledger/feature/transactions/TransactionRow.kt`
- **Dependencies**: T8.3
- **Expected result**: Transaction row renders correctly for INFLOW and OUTFLOW
- **Definition of Done**:
  - Row shows: date (formatted via `DateFormatter`), description, type badge, formatted amount
  - INFLOW: green text, "+$X,XXX.XX"
  - OUTFLOW: red text, "-$X,XXX.XX"
  - Row is clickable (handler registered in T9.5 for edit)
  - Long-press shows delete confirmation dialog (wired to T8.1)

---

### T8.5: Wire FAB navigation to transaction form

- **Goal**: FAB navigates to transaction form in add mode
- **Files affected**: `app/src/main/java/com/vaultledger/navigation/NavGraph.kt`
- **Dependencies**: T8.4, T5.2
- **Expected result**: FAB tap → transaction form with vaultId
- **Definition of Done**:
  - `onAddTransaction(vaultId)` calls `navController.navigate("/vaults/$vaultId/transaction")`
  - Transaction form receives `vaultId` and no `transactionId` (add mode)

---

## M9 — Transaction Form (5 tasks, was old M8 minus delete)

> Delete is already handled in M8. Navigation guard moved here from old M10.

---

### T9.1: Create TransactionFormViewModel

- **Goal**: ViewModel managing form state for add and edit modes
- **Files affected**: `feature/transactions/src/main/java/com/vaultledger/feature/transactions/TransactionFormViewModel.kt`
- **Dependencies**: T4.6, T2.4
- **Expected result**: ViewModel handles form state, validation, and submission
- **Definition of Done**:
  - `@HiltViewModel`, injects `TransactionRepository`
  - `vaultId` and optional `transactionId` from `SavedStateHandle`
  - If `transactionId` non-null: load existing, pre-populate form (edit mode)
  - If `transactionId` null: empty form (add mode)
  - `StateFlow<TransactionFormState>`: amount, type, description, date, isSaving, errors
  - `validate(): Boolean` checks all rules
  - `save()` calls `repository.insert()` or `repository.update()` based on mode
  - Unit test verifies validation rules and save behavior

---

### T9.2: Create TransactionFormScreen layout

- **Goal**: Form with amount, type toggle, description field, date picker
- **Files affected**: `feature/transactions/src/main/java/com/vaultledger/feature/transactions/TransactionFormScreen.kt`
- **Dependencies**: T9.1
- **Expected result**: Form renders with all input fields
- **Definition of Done**:
  - Amount: `OutlinedTextField` with number keyboard, currency prefix "$"
  - Type: `SegmentedButton` or `Switch` for INFLOW/OUTFLOW
  - Description: `OutlinedTextField` with character counter (500 max)
  - Date: read-only field that opens `DatePickerDialog` on tap
  - Save button in top app bar
  - Back button (with cancel if dirty)

---

### T9.3: Implement form validation

- **Goal**: Real-time validation with inline error messages
- **Files affected**: `TransactionFormViewModel.kt`, `TransactionFormScreen.kt`
- **Dependencies**: T9.2
- **Expected result**: Invalid input shows errors, save blocked
- **Definition of Done**:
  - Amount > 0: "Amount must be greater than zero"
  - Description required: "Description is required"
  - Description max 500: "Maximum 500 characters"
  - Date required: "Date is required"
  - Date range: not before 2000-01-01, not after now + 1 day
  - Type must be selected: "Select inflow or outflow"
  - Save button disabled while form has errors
  - Errors clear when user fixes the field

---

### T9.4: Wire add transaction flow

- **Goal**: Saving a new transaction persists to Room and navigates back
- **Files affected**: `NavGraph.kt`, `TransactionFormScreen.kt`
- **Dependencies**: T9.3
- **Expected result**: New transaction appears in list after save
- **Definition of Done**:
  - Save calls `viewModel.save()`
  - On success: navigate back to vault detail
  - Transaction list reactively updates via Room Flow
  - Balance updates atomically (T4.4 guarantee)

---

### T9.5: Wire edit transaction flow

- **Goal**: Tapping a transaction row opens form pre-populated with its data
- **Files affected**: `NavGraph.kt`, `VaultDetailScreen.kt`, `TransactionFormScreen.kt`
- **Dependencies**: T9.4
- **Expected result**: Edit form shows existing data, save updates in place
- **Definition of Done**:
  - Transaction row tap navigates to `/vaults/{vaultId}/transaction?transactionId={id}`
  - Form pre-populates with existing amount, type, description, date
  - Save calls `repository.update()`
  - On success: navigate back, list reflects changes

---

## M10 — Auth Integration (7 tasks, unchanged from old M9)

---

### T10.1: Configure Firebase in project

- **Goal**: Firebase SDK integrated for Authentication
- **Files affected**: `app/build.gradle.kts`, `app/google-services.json`, `build.gradle.kts` (root)
- **Dependencies**: T1.3
- **Expected result**: Firebase Auth SDK available and initialized
- **Definition of Done**:
  - `google-services.json` placed in `:app` module
  - `com.google.gms.google-services` plugin applied in root and app
  - Firebase Auth dependency added to version catalog
  - `./gradlew :app:assembleDebug` succeeds

---

### T10.2: Create AuthRepository interface and Firebase implementation

- **Goal**: Abstraction over Firebase Auth
- **Files affected**:
  - `core/domain/src/main/java/com/vaultledger/domain/repository/AuthRepository.kt`
  - `core/data/src/main/java/com/vaultledger/data/repository/FirebaseAuthRepository.kt`
- **Dependencies**: T10.1, T2.1 (User domain model)
- **Expected result**: Auth operations work through repository abstraction
- **Definition of Done**:
  - `AuthRepository`: `login(email, password): Result<User>`, `register(email, password): Result<User>`, `logout()`, `observeAuthState(): Flow<User?>`
  - Maps Firebase errors to domain exceptions: `WrongPassword`, `EmailInUse`, `WeakPassword`, `NetworkError`, `TooManyRequests`
  - Hilt module binds `AuthRepository` to `FirebaseAuthRepository`

---

### T10.3: Create SplashViewModel and SplashScreen

- **Goal**: Check auth state on launch, navigate accordingly
- **Files affected**:
  - `core/ui/src/main/java/com/vaultledger/ui/screen/SplashViewModel.kt`
  - `core/ui/src/main/java/com/vaultledger/ui/screen/SplashScreen.kt`
- **Dependencies**: T10.2
- **Expected result**: Splash screen determines navigation destination
- **Definition of Done**:
  - `SplashViewModel` collects `authRepository.observeAuthState()`
  - Emits `SplashDestination.Auth` or `SplashDestination.WorkspaceList`
  - `SplashScreen` shows app logo and loading indicator
  - After auth resolves, navigates to correct destination

---

### T10.4: Create AuthViewModel

- **Goal**: Manage login/register form state, validation, and submission
- **Files affected**: `core/ui/src/main/java/com/vaultledger/ui/screen/AuthViewModel.kt`
- **Dependencies**: T10.2
- **Expected result**: Login and register flows work
- **Definition of Done**:
  - `@HiltViewModel`, injects `AuthRepository`
  - `StateFlow<AuthUiState>`: `Idle | Loading | Success | Error(message)`
  - Form: `email`, `password`, `isLoginMode`
  - Validation: email non-empty, password ≥ 6 chars
  - Error mapping for: wrong password, email in use, weak password, network error

---

### T10.5: Create AuthScreen

- **Goal**: Login/register UI with form fields, toggle, error display
- **Files affected**: `core/ui/src/main/java/com/vaultledger/ui/screen/AuthScreen.kt`
- **Dependencies**: T10.4
- **Expected result**: Users can log in or register
- **Definition of Done**:
  - Email `OutlinedTextField` with email keyboard
  - Password `OutlinedTextField` with visibility toggle
  - "Log In" / "Register" toggle text button
  - Submit button: label changes between "Log In" and "Create Account"
  - Error message (red) below form
  - Loading state: submit button shows spinner

---

### T10.6: Wire auth-aware navigation graph

- **Goal**: Auth state determines initial navigation destination
- **Files affected**: `app/src/main/java/com/vaultledger/navigation/NavGraph.kt`, `MainActivity.kt`
- **Dependencies**: T10.3, T10.5, T5.2
- **Expected result**: Unauthenticated → auth, authenticated → workspace list
- **Definition of Done**:
  - `MainActivity` observes `SplashViewModel.destination`
  - `startDestination` dynamically set based on auth state
  - Logout clears back stack and navigates to auth
  - No back-navigation from auth to workspace list

---

### T10.7: Add logout to Settings screen

- **Goal**: Settings screen with profile info and logout button
- **Files affected**: `feature/settings/src/main/java/com/vaultledger/feature/settings/SettingsScreen.kt`, `feature/settings/src/main/java/com/vaultledger/feature/settings/SettingsViewModel.kt`
- **Dependencies**: T10.6, T5.3 (placeholder screens exist)
- **Expected result**: User can see their email and log out
- **Definition of Done**:
  - `SettingsViewModel` injects `AuthRepository`
  - Displays user email
  - Logout button with confirmation dialog
  - On logout: `authRepository.logout()`, navigate to auth, clear back stack
  - About section with app version

---

## M11 — Polish & Testing (9 tasks, was old M10)

> Fixed: T10.8's circular "M10" dependency is now correctly listed as M6-M10.
> ViewModel tests merged into their respective feature milestones (T6.1, T7.1, T8.1, T9.1, T10.4 all include unit tests).
> UI tests split per journey to stay < 3 hours each.

---

### T11.1: Add empty states to all list screens

- **Goal**: Every list screen shows helpful guidance when empty
- **Files affected**: `WorkspaceListScreen.kt`, `VaultListScreen.kt`, `VaultDetailScreen.kt`
- **Dependencies**: M6, M7, M8 (all screens built)
- **Expected result**: Empty lists are informative
- **Definition of Done**:
  - No workspaces: illustration + "No workspaces yet. Create one to get started."
  - No vaults: "No vaults in this workspace. Tap + to add one."
  - No transactions: "No transactions yet. Tap + to record your first."

---

### T11.2: Add loading shimmer/skeleton states

- **Goal**: Visual feedback during initial data load
- **Files affected**: All screen files
- **Dependencies**: M6, M7, M8
- **Expected result**: Shimmer placeholders render while loading
- **Definition of Done**:
  - Workspace list: 3 shimmer rectangles
  - Vault list: 3 shimmer cards
  - Transaction list: 3 shimmer rows + balance shimmer
  - Uses `placeholder` modifier or custom shimmer composable

---

### T11.3: Add error snackbar handling

- **Goal**: Unexpected errors show as dismissable snackbars
- **Files affected**: All screen files, `AppScaffold.kt`
- **Dependencies**: M6, M7, M8
- **Expected result**: Errors are visible and non-blocking
- **Definition of Done**:
  - `SnackbarHostState` in `AppScaffold`
  - ViewModel `Error` state triggers snackbar
  - Snackbar includes "Dismiss" action

---

### T11.4: Add navigation guard for unsaved form changes

- **Goal**: Navigating away from dirty form shows discard confirmation
- **Files affected**: `TransactionFormScreen.kt`, `NavGraph.kt`
- **Dependencies**: M9
- **Expected result**: Users don't lose form input accidentally
- **Definition of Done**:
  - Form tracks dirty state (field changed from initial)
  - Back navigation from dirty form shows dialog: "Discard changes?"
  - "Discard" navigates back, "Keep editing" stays

---

### T11.5: Write Repository integration tests

- **Goal**: Repository behavior verified with in-memory Room
- **Files affected**: Test files in `:core:data`: `*RepositoryImplTest.kt`
- **Dependencies**: M4 (all repositories exist)
- **Expected result**: All repository operations verified
- **Definition of Done**:
  - `WorkspaceRepositoryImplTest`: CRUD, cascade
  - `VaultRepositoryImplTest`: CRUD scoped to workspace
  - `TransactionRepositoryImplTest`: CRUD, balance cache consistency
  - Balance cache test: insert 3 → verify, update 1 → verify, delete 1 → verify
  - Uses `Room.inMemoryDatabaseBuilder`

---

### T11.6: Write UI test — add transaction journey

- **Goal**: End-to-end add transaction flow works
- **Files affected**: `app/src/androidTest/.../AddTransactionJourneyTest.kt`
- **Dependencies**: M9, M10 (all features complete)
- **Expected result**: Creates workspace → vault → adds inflow → balance increases
- **Definition of Done**:
  - `createComposeRule()` with full navigation graph
  - Test creates workspace, creates vault, adds inflow transaction
  - Verifies balance increased
  - Verifies transaction appears in list

---

### T11.7: Write UI test — edit and delete transaction journey

- **Goal**: Edit and delete flows work end-to-end
- **Files affected**: `app/src/androidTest/.../EditDeleteJourneyTest.kt`
- **Dependencies**: M9, M10
- **Expected result**: Balance recalculates correctly
- **Definition of Done**:
  - Creates transaction, navigates to edit, changes amount
  - Verifies balance updated
  - Deletes transaction, verifies balance recalculated

---

### T11.8: Write UI test — auth journey

- **Goal**: Registration and login flows work
- **Files affected**: `app/src/androidTest/.../AuthJourneyTest.kt`
- **Dependencies**: M10 (auth features complete)
- **Expected result**: User can register and log in
- **Definition of Done**:
  - Test registers new user
  - Verifies navigation to workspace list
  - Test logs out, logs in with same credentials
  - Verifies workspace list appears

---

### T11.9: Manual edge-case testing walkthrough

- **Goal**: Verify all MVP exit criteria
- **Files affected**: None (manual testing)
- **Dependencies**: All M1-M11 tasks
- **Expected result**: All edge cases pass
- **Definition of Done**:
  - Empty vault (balance = $0.00)
  - Single transaction
  - Rapid add → edit → delete sequence
  - Offline launch (airplane mode)
  - Process death and recovery
  - Rapid back-navigation during save
  - [ ] All MVP features (F-01 through F-07) implemented
  - [ ] Crash-free rate > 99.5% over 7 days of dogfooding
  - [ ] No known P0/P1 bugs
  - [ ] 100% domain-layer code covered by unit tests
  - [ ] Balance matches transaction sum: empty vault, single transaction, edits, deletes
  - [ ] App functions fully offline
  - [ ] 0 ktlint blocking violations
  - [ ] 0 Detekt complexity warnings on new code

---

## Summary of Changes from Original

| Issue | Original | Optimized |
|-------|----------|-----------|
| Task count | 52 | 55 |
| Milestones | M1-M10 | M1-M11 |
| M2 order | Entities before domain models | Domain models first, entities mirror them |
| T3.3 (old) | TransactionRepo + balance cache (3+ hrs) | Split into T4.3 (CRUD) + T4.4 (balance cache) |
| T7.2 (old) | Transaction list screen (3+ hrs) | Split into T8.2 (balance header) + T8.3 (list) + T8.4 (row) |
| T8.6 (old) | Delete in Transaction Form milestone | Moved to M8 (delete belongs on list screen, not form) |
| T10.6 (old) | ViewModel tests: all 5 in one task (3+ hrs) | Each ViewModel task includes its own unit test |
| T10.8 (old) | Circular dependency: "depends on M10" | Fixed: T11.6-T11.8 depend on M9, M10 |
| Shared infra | Missing | Added T2.4: UiState, CurrencyFormatter, DateFormatter, UuidGenerator |
| T1.5 DoD | Mentioned theme that didn't exist yet | Reduced to basic MaterialTheme; custom theme moved to T5.1 |
| Hidden deps | T3.1-T3.3 missing domain model deps | All dependencies listed explicitly |
| Hidden deps | T9.7 missing placeholder screen dep | Added T5.3 as dependency |
