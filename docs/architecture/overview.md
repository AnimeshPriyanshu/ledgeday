# Architecture Overview

## Layered Architecture

Vault Ledger follows a **multi-module Clean Architecture** approach with three layers:

```
┌──────────────────────────────┐
│         Presentation         │  UI layer (Jetpack Compose, ViewModels)
├──────────────────────────────┤
│           Domain             │  Use cases, business logic, domain models
├──────────────────────────────┤
│             Data             │  Repositories, data sources (local + remote)
└──────────────────────────────┘
```

## Module Structure

```
app/                    → Application module, DI, navigation
├── core/
│   ├── domain/         → Shared domain models and interfaces
│   ├── data/           → Shared data layer
│   ├── sync/           → (placeholder) Cloud sync module
│   └── ui/             → Shared UI components, theming
├── feature/
│   ├── vault/          → Vault list feature
│   ├── transactions/   → Transaction list & detail feature
│   ├── settings/       → Settings feature
│   └── workspace/      → Workspace list feature
```

## Key Design Decisions

- **Single Activity** architecture with Compose Navigation
- **Unidirectional data flow** (UDF) via StateFlow
- **Repository pattern** abstracts local vs. remote data sources
- **Offline-first**: local database is source of truth; remote is replica
