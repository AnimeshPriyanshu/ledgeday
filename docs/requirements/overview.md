# Product Overview

## 1. Product Vision

A world where every individual and partnership can track their financial movements with zero friction, zero complexity, and total confidence — online or off.

Vault Ledger is the simplest way for two people to know exactly where their money stands, without learning accounting or maintaining spreadsheets.

## 2. Problem Statement

Millions of individuals and informal partnerships (couples, roommates, freelancers, small business partners) need a lightweight way to track shared or personal financial movements. Existing solutions fall into two inadequate camps:

- **Spreadsheets** — flexible but fragile; no mobile experience, no sync, easy to corrupt
- **Accounting software** — overengineered; requires double-entry, chart of accounts, tax categories, and a mental model most non-accountants don't have

The gap is a **mobile-first, offline-capable, two-person ledger** that does one thing well: record transactions and show a balance. No invoices, no budgets, no reports. Just the number.

## 3. Target Audience

### Primary — Partnership Pair
- Couples managing shared household expenses
- Freelancers with a business partner
- Roommates splitting shared costs
- Two-person small business owners

### Secondary — Solo Individual
- Sole proprietor wanting a simple income/expense tracker
- Anyone who finds spreadsheet maintenance painful

### Demographics
- Age: 20–55
- Tech comfort: moderate (comfortable with smartphone apps)
- Platform: Android (v9 / API 28+ initially)
- Geography: global, English-first with localization planned

## 4. Goals

### For v1.0 (MVP)

| ID | Goal | Measured By |
|---|---|---|
| G-01 | Users can create vaults and record transactions offline | Transaction write success rate offline = 100% |
| G-02 | Balance is always accurate | 100% agreement between displayed balance and sum of transactions |
| G-03 | Two partners can share a workspace | Partner invite accept rate > 80% in testing |
| G-04 | Transactions sync between paired devices | P95 sync latency < 10 seconds |
| G-05 | App is stable | Crash-free rate > 99.5% |

### Post-MVP (within 6 months)

| ID | Goal |
|---|---|
| G-06 | Achieve 1,000 MAU |
| G-07 | 4.5+ star rating on Google Play |
| G-08 | D7 retention > 40% |
| G-09 | Zero data loss incidents in production |

## 5. Non-Goals

| Area | Reason |
|---|---|
| Full double-entry accounting | Overengineered for the target user; creates confusion |
| Inventory management | Out of scope; different problem domain |
| Recurring transactions / budgeting | Would shift product into budgeting category |
| Bank feeds / transaction import | Massive integration surface, security risk, support burden |
| Multi-user beyond 2 partners | Adds coordination complexity without clear demand |
| Web / iOS (v1) | Focus on Android; validates demand before multi-platform |
| Tax reporting | Legal liability per jurisdiction |
| Receipt scanning / image attachments | Increases storage, sync payload, and moderation needs |
| Public API or third-party integrations | Premature; would constrain internal architecture |

## 6. Core Philosophy

These principles guide every technical and product decision:

1. **Offline-first is not a feature — it is the architecture.** The app must work identically with or without internet. Cloud sync augments the local experience; it never gates it.

2. **The balance is truth.** Every UI state must derive from verifiable transaction math. If the balance can't be recalculated from scratch, it's a bug.

3. **Simplicity over flexibility.** When choosing between a simple solution that covers 90% of uses and a complex one that covers 99%, choose the simple one. Complexity can always be added later; it can never be removed.

4. **Partners are peers.** There is no "owner" and "guest" in a workspace. Both partners have equal read/write access. Symmetry reduces confusion.

5. **Data is the user's property.** The app is a window into the user's data, not a silo. Export must always be possible (CSV). Delete must be irreversible and complete.

6. **Silent is better than noisy.** Minimize notifications, permissions, and interruptions. The app should feel like a notebook, not a dashboard.

7. **Trust but verify.** LWW sync is simple and works for this use case, but every sync must be observable: the user should be able to see that sync happened and what changed.

## 7. Business Objectives

| Objective | Timeframe | Rationale |
|---|---|---|
| Validate demand for lightweight pair-ledger | 3 months post-MVP | Measure organic growth and retention before investing further |
| Establish Google Play presence with strong ratings | 3 months post-MVP | Ratings drive discoverability in a crowded finance category |
| Evaluate monetization model (one-time purchase / subscription / donation) | 6 months post-MVP | Free during validation phase; monetize only after PMF is confirmed |
| Build foundation for multi-platform expansion | 12 months | Architecture decisions today must not block web/iOS tomorrow |

## 8. Success Metrics

### Product Health (Tracked Weekly)

| Metric | Target (v1) | Stretch |
|---|---|---|
| Crash-free rate | 99.5% | 99.8% |
| ANR rate | < 0.1% | < 0.05% |
| Cold start time (P95) | < 2s | < 1.2s |
| Sync success rate (network available) | 99% | 99.9% |
| P95 transaction sync latency | < 10s | < 3s |
| Daily active users (DAU) | — | TBD post-launch |
| D7 retention | — | > 40% |
| Store rating | 4.0+ | 4.5+ |

### Engineering Quality (Tracked Per Release)

| Metric | Threshold |
|---|---|
| Unit test coverage (domain layer) | > 90% |
| UI test coverage (critical paths) | 100% |
| ktlint violations | 0 blocking |
| Detekt complexity warnings | 0 on new code |
| Dependency vulnerability count | 0 critical / high |

### Data Safety (Hard Guarantees)

| Metric | Guarantee |
|---|---|
| Data loss due to sync conflict | 0 occurrences |
| Unauthorized cross-workspace access | 0 occurrences |
| Transaction data permanently lost after delete confirmation | 0 occurrences |

## 9. MVP Definition

### Scope

The MVP targets the solo user use case (Phase 1). Partner sync (Phase 2) is explicitly excluded from MVP to validate core utility before adding multi-device complexity.

### MVP Features (from Features Catalog)

| ID | Feature | Must-Have |
|---|---|---|
| F-01 | Create Vault | Yes |
| F-02 | Add Transaction (inflow/outflow) | Yes |
| F-03 | Auto-calculated balance per vault | Yes |
| F-04 | Transaction list sorted by date | Yes |
| F-05 | Edit Transaction | Yes |
| F-06 | Delete Transaction | Yes |
| F-07 | Multiple vaults | Yes |

### MVP Exit Criteria

All must pass before MVP is declared complete:

1. All MVP features implemented and tested
2. Crash-free rate > 99.5% over 7 days of dogfooding
3. No known P0/P1 bugs
4. 100% of domain-layer code has unit test coverage
5. Balance matches transaction sum in all tested scenarios (including edge cases: empty vault, single transaction, edits, deletes)
6. App functions fully offline (no crash on network-off launch)
7. Play Store listing approved with privacy policy

### MVP Exclusions

- Partner workspace/sync
- Categories
- Search & filter
- Export
- Dark mode
- Biometric lock

## 10. Future Roadmap Summary

```
Phase 1 (MVP) ─→ Phase 2 (Sync) ─→ Phase 3 (Quality) ─→ Phase 4 (Growth)
   Solo vault        Partner sync        Polish             Multi-platform
   Transactions      Workspace           Categories         Web companion
   Auto balance      sharing             Search/filter      iOS (TBD)
   Edit/delete       Near real-time      Export CSV         Monetization
   Offline-first     Conflict            Dark mode
                     resolution          Biometric lock
```

### Phase 2 — Partner Sync (3 months post-MVP)

Workspace sharing, invite flow, Firestore sync, sync status indicator, conflict resolution.

### Phase 3 — Quality (6 months post-MVP)

Categories with customization, full-text search, CSV export, dark mode, biometric app lock, accessibility audit.

### Phase 4 — Growth (12 months)

Web companion app (read-only), iOS evaluation, optional cloud backup beyond sync, monetization.
