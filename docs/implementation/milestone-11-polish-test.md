# Milestone 11 — Polish & Testing

**Complexity**: 3 / 5
**Tasks**: T11.1, T11.2, T11.3, T11.4, T11.5, T11.6, T11.7, T11.8, T11.9

## Description
UI polish (empty states, loading shimmer, error snackbars, navigation guard), repository integration tests, UI tests for critical journeys, and manual edge-case verification. ViewModel unit tests are already included in their respective feature tasks.

## Summary of Tasks
| Task | Title | Files |
|------|-------|-------|
| T11.1 | Empty states for all list screens | All screen files |
| T11.2 | Loading shimmer/skeleton states | All screen files |
| T11.3 | Error snackbar handling | `AppScaffold`, all screens |
| T11.4 | Navigation guard for unsaved form changes | `TransactionFormScreen`, `NavGraph` |
| T11.5 | Repository integration tests (in-memory Room) | `:core:data/test/` |
| T11.6 | UI test: add transaction journey | `:app/androidTest/` |
| T11.7 | UI test: edit and delete transaction journey | `:app/androidTest/` |
| T11.8 | UI test: auth journey | `:app/androidTest/` |
| T11.9 | Manual edge-case testing walkthrough | N/A |

## Completion Criteria
- [ ] Every list screen has informative empty state
- [ ] Shimmer visible during initial data load
- [ ] Errors appear as snackbars, not crashes
- [ ] Dirty form shows discard-confirmation dialog
- [ ] All repository integration tests pass
- [ ] All 3 UI journey tests pass
- [ ] All MVP exit criteria met
