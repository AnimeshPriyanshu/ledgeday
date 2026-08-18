# Vault Ledger

An **offline-first Android app** for partners to track shared finances. Every vault holds a list of transactions with an automatically calculated balance. Two partners share a workspace and see each other's changes in near real time through cloud sync.

Vault Ledger is intentionally **not** accounting or inventory software — it is a lightweight personal business ledger.

> Full project documentation lives in [`docs/`](docs/index.md): requirements, architecture, ADRs, data model, sync protocol, UX, and implementation milestones.

---

## Features

### Phase 1 — Core (MVP)
- **Create Vault** — named vaults (e.g., "Household", "Freelance")
- **Add Transaction** — amount, description, date, type (inflow/outflow)
- **Auto Balance** — running balance per vault, recalculated on every change
- **Transaction List** — sorted by date, with edit and delete
- **Multiple Vaults** — organize money into separate ledgers

### Phase 2 — Partner Sync
- **Workspace Sharing** — invite a partner to a workspace
- **Cloud Sync** — transactions sync between paired devices in near real time
- **Conflict Resolution** — deterministic merge strategy for concurrent edits
- **Sync Status Indicator** — last sync time and connectivity state

### Phase 3 — Quality
Categories, search & filter, CSV export, dark mode, and biometric lock (roadmap).

---

## Tech Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose (Material 3), Navigation Compose |
| Architecture | Clean Architecture, single-activity, UDF with `StateFlow` |
| Local data | Room (single source of truth), Kotlinx Serialization |
| Cloud | Firebase Auth, Cloud Firestore (rules + indexes committed) |
| Sync | WorkManager background sync, Firestore snapshot listeners |
| DI | Hilt |
| Tooling | Kotlin 2.x, KSP, ktlint, detekt, JUnit 5 / Turbine / Robolectric |

## Project Structure

Multi-module Clean Architecture:

```
app/                          # Application entry point, Hilt wiring, Firebase config
core/
  domain/                     # Entities, use cases, business rules
  data/                       # Room DB, repositories, Firestore + sync
  ui/                         # Shared composables, theming, common UI
feature/
  workspace/                  # Workspace list & management
  vault/                      # Vault list & management
  transactions/               # Transaction list, form, balance header
  settings/                   # Profile, invites, account
```

## Getting Started

### Prerequisites
- Android Studio (or JDK 17+ and Android SDK 34)
- A Firebase project (Auth + Firestore enabled)

### Setup
1. Clone the repository.
2. Add your Firebase config:
   - `app/google-services.json` — download from the Firebase console.
3. (Optional) Point the CLI at your project:
   ```bash
   firebase use <project-id>
   ```
4. Build and run:
   ```bash
   ./gradlew :app:assembleDebug
   ```

### Firebase
- Firestore rules and indexes are versioned in this repo (`firestore.rules`, `firestore.indexes.json`).
- Deploy them with:
  ```bash
  firebase deploy --only firestore
  ```

## Quality Gates

```bash
./gradlew testDebugUnitTest        # Unit tests (JUnit 5 + Turbine)
./gradlew ktlintCheck              # Kotlin lint
./gradlew detekt                   # Static analysis
```

## License

Private project.
