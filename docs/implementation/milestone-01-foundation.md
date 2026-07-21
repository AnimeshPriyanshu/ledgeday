# Milestone 1 — Project Foundation

**Complexity**: 2 / 5
**Tasks**: T1.1, T1.2, T1.3, T1.4, T1.5, T1.6

## Description
Set up Gradle multi-module project, version catalog, Hilt, and compile-check all modules.

## Summary of Tasks
| Task | Title | Files |
|------|-------|-------|
| T1.1 | `settings.gradle.kts` with module includes | Root |
| T1.2 | Version catalog with all dependencies | `gradle/libs.versions.toml` |
| T1.3 | Empty module `build.gradle.kts` files | All 9 modules |
| T1.4 | `VaultLedgerApp` with `@HiltAndroidApp` | `:app` |
| T1.5 | Empty `MainActivity` with Compose | `:app` |
| T1.6 | ktlint and Detekt configuration | Root |

## Completion Criteria
- [ ] `./gradlew :app:assembleDebug` succeeds
- [ ] App launches on emulator
- [ ] All 9 modules compile
