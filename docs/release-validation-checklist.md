# Release Validation Checklist — Vault Ledger MVP

> **Status**: Document only. Manual verification has NOT been performed.
> This checklist documents the required validation steps before release.

## MVP Features

- [ ] F-01: Create Workspace — user can create a named workspace
- [ ] F-02: Create Vault — user can create a vault within a workspace with name, description, color
- [ ] F-03: Add Transaction — user can add inflow/outflow transactions with amount, description, date
- [ ] F-04: Edit Transaction — user can modify existing transaction fields
- [ ] F-05: View Balance — balance updates correctly after add/edit/delete
- [ ] F-06: Delete Transaction — user can delete with confirmation dialog
- [ ] F-07: Authentication — user can register, log in, log out

## Screen States

### Workspace List
- [ ] Empty state: "No workspaces yet" with guidance
- [ ] Loading state: shimmer placeholders
- [ ] Error state: inline retry + snackbar notification
- [ ] Populated: workspaces displayed in cards

### Vault List
- [ ] Empty state: "No vaults in this workspace"
- [ ] Loading state: shimmer placeholders
- [ ] Error state: inline retry + snackbar notification
- [ ] Populated: vaults with name, color dot, balance

### Transaction List (Vault Detail)
- [ ] Empty state: "No transactions yet"
- [ ] Loading state: shimmer placeholders
- [ ] Error state: inline retry + snackbar notification
- [ ] Populated: transactions with type, description, date, amount
- [ ] Balance header displays correct running total

### Transaction Form
- [ ] Add mode: empty fields, "Add Transaction" title
- [ ] Edit mode: pre-populated fields, "Edit Transaction" title
- [ ] Validation errors shown inline
- [ ] Save error shown as snackbar with Dismiss
- [ ] Unsaved changes: discard confirmation dialog on back
- [ ] Successful save navigates back

### Auth
- [ ] Splash screen shows during auth state check
- [ ] Unauthenticated → Auth screen (login/register)
- [ ] Authenticated → Workspace List
- [ ] Login form: email, password, Sign In button
- [ ] Register toggle: shows Create your account, Sign Up button
- [ ] Field validation: email format, password length
- [ ] Firebase error messages show user-friendly text

### Settings
- [ ] App version displayed
- [ ] Invite Partner button (placeholder)
- [ ] Log Out button with confirmation

## Edge Cases

- [ ] Empty vault: balance shows $0.00
- [ ] Single transaction: balance equals transaction amount
- [ ] Multiple transactions: balance sums correctly
- [ ] Inflow + Outflow: balance = sum(inflow) - sum(outflow)
- [ ] Edit transaction: balance recalculated
- [ ] Delete transaction: balance recalculated
- [ ] Rapid add → edit → delete sequence
- [ ] Very long description (500 chars): truncation at limit
- [ ] Amount with decimals: cents handled correctly
- [ ] Zero amount: rejected with validation
- [ ] Future date: rejected with validation
- [ ] Date before 2000: rejected with validation

## Offline Behavior

- [ ] App launches in airplane mode (if previously authenticated)
- [ ] Existing data loads from Room cache
- [ ] Create workspace while offline (queued for sync)
- [ ] Create transaction while offline (queued for sync)
- [ ] After restoring network, data syncs

## Auth Flows

- [ ] Register with email + password
- [ ] Log in with existing credentials
- [ ] Log out clears back stack to Auth screen
- [ ] Auth state persists across process death
- [ ] Incorrect credentials show error message
- [ ] Weak password shows Firebase error message
- [ ] Duplicate email shows Firebase error message

## Performance Targets

- [ ] Cold start P95 < 2s
- [ ] Sync P95 < 10s (including background sync)
- [ ] Scroll performance: no jank in transaction lists

## Security

- [ ] Firestore security rules deployed (blocker)
- [ ] Data-at-rest encryption decision documented
- [ ] Input validation on all fields
- [ ] No Firebase API keys in source code

## Code Quality

- [ ] 0 ktlint blocking violations
- [ ] 0 Detekt complexity warnings on new code
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] All UI journey tests pass
- [ ] Test coverage: 100% domain-layer code covered
- [ ] Crash-free rate > 99.5% over 7 days of dogfooding

## Build

- [ ] `./gradlew assembleDebug` succeeds
- [ ] `./gradlew assembleRelease` succeeds (with ProGuard)
- [ ] Installable APK works on Android 8.0+ (API 26)
- [ ] No known P0/P1 bugs
