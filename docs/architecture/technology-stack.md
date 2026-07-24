# Technology Stack

## Android Layer

| Component | Choice | Rationale |
|---|---|---|
| Language | Kotlin | Industry standard for Android |
| UI | Jetpack Compose | Modern declarative UI toolkit |
| Navigation | Compose Navigation | Type-safe navigation |
| DI | Hilt | Standard DI for Android |
| Architecture | MVVM + Clean Architecture | Separation of concerns |

## Data Layer

| Component | Choice | Rationale |
|---|---|---|
| Local DB | Room | Official Android ORM, reactive via Flow |
| Remote Sync | Firebase Firestore | Real-time sync, offline support, free tier |
| Background Sync | WorkManager | Deferred, battery-friendly sync |
| Serialization | Kotlinx Serialization | First-class Kotlin support |

## Testing

| Component | Choice |
|---|---|
| Unit Tests | JUnit 5 + Turbine (Flow testing) |
| UI Tests | Compose UI Test |
| Mocking | MockK |

## Build

| Component | Choice |
|---|---|
| Build System | Gradle with Kotlin DSL |
| Version Catalog | Gradle Version Catalog (libs.versions.toml) |
| CI | GitHub Actions |
