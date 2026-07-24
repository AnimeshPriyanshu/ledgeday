# User Journeys

## Journey 1: Solo User Creates a Vault

```
1. Opens app → sees auth screen
2. Registers with email/password
3. Lands on empty workspace list → taps "+" FAB
4. Creates workspace "Personal" via dialog → enters vaults screen
5. Taps "New Vault" → names it "Freelance Income"
6. Taps the vault → sees empty transaction list
7. Taps "+" → enters amount, description, selects "INFLOW"
8. Saves → transaction appears in list, balance shows updated value
```

## Journey 2: Partner Invite & Shared Usage

```
1. User A creates workspace "Household"
2. User A goes to Settings → Invite Partner → generates invite link
3. User A shares link with User B
4. User B opens link → joins workspace
5. User B opens vault "Household" → sees it's empty
6. User B adds a transaction → it appears immediately
7. User A (on different device) sees the transaction appear after sync
8. Both users see the same balance
```

## Journey 3: Offline Transaction

```
1. User opens vault while on airplane mode
2. Existing transactions are visible (cached in Room)
3. User adds a new transaction → it appears in the list immediately
4. Balance updates locally
5. When connectivity returns, transaction syncs automatically
6. Partner sees the new transaction after sync completes
```
