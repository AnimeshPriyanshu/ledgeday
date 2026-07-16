# Data Flow

## Offline-First Write Path

```
User Action
    │
    ▼
ViewModel → UseCase → Repository
                          │
                          ▼
                    Local Database (Room)
                          │
                          └──→ Sync Queue (Pending changes)
                                    │
                              [When online]
                                    │
                                    ▼
                            Remote Database
```

## Offline-First Read Path

```
Repository.getTransactions()
    │
    ├──→ Emit cached data from Room immediately
    │
    └──→ (Optional) Fetch latest from remote → update Room → re-emit
```

## Sync Flow

```
┌──────────┐         ┌──────────────┐         ┌──────────┐
│  Device A │ ←──────→ │   Cloud      │ ←──────→ │  Device B │
│  (Room)   │   sync   │  (Firestore) │   sync   │  (Room)   │
└──────────┘         └──────────────┘         └──────────┘
```

- Each transaction has a `lastModified` timestamp
- Conflicts are resolved by **last-write-wins** based on `lastModified`
- Sync is triggered by:
  - Local write completion
  - Periodic background sync (WorkManager)
  - Pull-to-refresh
