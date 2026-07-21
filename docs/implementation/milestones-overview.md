# Implementation Milestones — Vault Ledger MVP

## Overview

11 milestones, ordered from foundation to finished application.
Each milestone compiles, is independently testable, and does not break previous work.

## Dependency Graph

```
 1. Foundation ── 2. Domain Layer ── 3. Data Layer ── 4. Repos & DI ──┬
                                                                      │
 5. Navigation ───────────────────────────────────────────────────────┤
                                                                      │
                                      ┌─ 6. Workspace ── 7. Vault ── 8. Txn List ── 9. Txn Form
                                      │
10. Auth ─────────────────────────────┤
                                      │
11. Polish & Test ────────────────────┴──────────────────────────────────────────────
```

## Parallel Tracks

| Track | Milestones | Start After |
|-------|------------|-------------|
| A — Feature spine | M1 → M2 → M3 → M4 → M6 → M7 → M8 → M9 | — |
| B — Navigation (parallel) | M5 | M1 |
| C — Auth (parallel) | M10 | M1 |
| D — Integration | M11 | M9, M10 |

## Changes from Original Roadmap

| Change | Reason |
|--------|--------|
| M2: Domain First | Clean Architecture: domain defines models, data layer implements |
| M3: Data Layer split out | Entities now mirror domain, not define it |
| T4.3 + T4.4 split | Balance cache atomicity is distinct from basic CRUD |
| T8.2 + T8.3 + T8.4 split | Balance header, list, and row are separate composables |
| Delete moved to M8 | Delete action lives on the list screen, not the form |
| M11 added | Testing and polish separated from feature milestones |
| T2.4 added | Shared infrastructure (UiState, formatting) was missing |
| Unit tests in each VM task | Avoids 3-hour all-ViewModel test task |
| UI tests split per journey | Each journey test < 2 hours |

## Total Estimate

| Metric | Value |
|--------|-------|
| Milestones | 11 |
| Total tasks | 55 |
| Estimated team | 2–3 developers |
| Estimated time | 5–7 days (with AI assistance) |
