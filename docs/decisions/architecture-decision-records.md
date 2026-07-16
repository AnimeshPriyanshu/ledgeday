# Architecture Decision Records

## ADR-001: Offline-First with Room as Source of Truth

**Status**: Accepted

**Context**: The app must work fully offline. Many sync strategies treat the cloud as primary.

**Decision**: Room (local SQLite) is the single source of truth. The remote database is a replica. All UI reads come from Room.

**Consequences**:
- (+) Full offline functionality
- (+) UI always reactive via Room Flows
- (-) Sync logic is more complex (must handle conflict resolution)
- (-) Initial load requires local DB population

---

## ADR-002: Last-Write-Wins Conflict Resolution

**Status**: Accepted

**Context**: With only two partners per workspace, conflict probability is low but must be handled.

**Decision**: Use `lastModified` timestamp with last-write-wins. No CRDT or merge strategies.

**Consequences**:
- (+) Simple to implement and reason about
- (+) Well-supported by Firestore
- (-) Can lose data if two edits happen at the exact same millisecond (extremely unlikely)

---

## ADR-003: Firebase Firestore as Sync Backend

**Status**: Accepted

**Context**: Need real-time sync with minimal backend maintenance.

**Decision**: Use Firestore for cloud sync and Firebase Auth for authentication.

**Consequences**:
- (+) Built-in real-time listeners
- (+) Free tier sufficient for MVP
- (+) Handles authentication, security rules, and hosting
- (-) Vendor lock-in to GCP
- (-) Firestore costs grow with usage

---

## ADR-004: Single Activity with Compose Navigation

**Status**: Accepted

**Context**: Modern Android architecture favors single-activity with declarative navigation.

**Decision**: Single `MainActivity` using Jetpack Compose Navigation with type-safe routes.

**Consequences**:
- (+) Simplified lifecycle management
- (+) Type-safe navigation arguments
- (-) Deep linking requires additional configuration

---

## ADR-005: Balance as Derived Value

**Status**: Accepted

**Context**: Storing balance as a field risks inconsistency with transaction data.

**Decision**: Balance is never stored as user-editable data. It is always calculated from transactions. A cached field on Vault is updated atomically on transaction changes.

**Consequences**:
- (+) No consistency bugs
- (+) Simple mental model
- (-) Slight performance cost on large vaults (mitigated by indexing)
