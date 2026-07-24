# Sync Protocol

## Strategy: Last-Write-Wins (LWW)

Each Transaction has a `lastModified` timestamp. When two devices sync:

1. Compare `lastModified` of local vs. remote record
2. The record with the later timestamp wins
3. The losing record is overwritten entirely

## Sync Trigger Events

| Trigger | Mechanism |
|---|---|
| Local transaction write | Immediately enqueue sync |
| App foreground | Check for pending sync |
| Periodic (15 min) | WorkManager periodic task |
| Pull-to-refresh | Manual user-initiated sync |
| Remote change (partner) | Firestore snapshot listener |

## Sync Flow Detail

```
1. Local write occurs → transaction saved to Room with synced=false
2. Transaction added to SyncWork queue
3. WorkManager executes SyncWorker (respects battery/data saver)
4. SyncWorker:
   a. Reads all unsynced transactions from Room
   b. Uploads to Firestore with server timestamp
   c. On success: marks synced=true in Room
5. Firestore snapshot listener fires on partner's device
6. Partner's device applies incoming changes to local Room
7. Partner's UI reactively updates via Room Flow
```

## Conflict Example

```
Device A edits Transaction X at T1 → syncs to cloud (lastModified=T1)
Device B edits Transaction X at T2 → syncs to cloud (lastModified=T2)
    → Cloud accepts T2 (later timestamp)
    → Device A receives T2 version via snapshot listener
    → Device A overwrites local with T2
```

## Offline Behavior

- All writes go to Room immediately with `synced=false`
- The UI is always driven by Room — the user never sees a "saved" vs. "synced" distinction during normal use
- A subtle sync status indicator shows when changes are pending
