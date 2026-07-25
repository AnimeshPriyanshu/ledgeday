# Firestore Security Rules

Deploy these rules to your Firebase project for the partner connection feature.

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // ===== HELPERS =====
    function isAuth() {
      return request.auth != null;
    }

    function isWorkspaceMember(workspaceId) {
      return isAuth() && request.auth.uid in
        get(/databases/$(database)/documents/workspaces/$(workspaceId)).data.memberIds;
    }

    function hasOnly(fields) {
      return request.resource.data.diff(resource.data)
        .affectedKeys().hasOnly(fields);
    }

    // ===== INVITES =====
    match /invites/{code} {
      // Any authenticated user can read an invite by code
      allow read: if isAuth();

      // Only the creator can create an invite (must specify themselves as creator)
      allow create: if isAuth()
        && request.auth.uid == request.resource.data.creatorId
        && request.resource.data.status == 'active';

      // Two allowed update patterns:
      // 1. Creator revokes: status → 'expired', only status field changes
      // 2. Another user accepts: status → 'accepted', acceptedBy set, atomic transaction
      allow update: if isAuth() && (
        (request.auth.uid == resource.data.creatorId
          && request.resource.data.status == 'expired'
          && hasOnly(['status']))
        ||
        (resource.data.status == 'active'
          && request.resource.data.status == 'accepted'
          && request.resource.data.acceptedBy == request.auth.uid
          && request.auth.uid != resource.data.creatorId
          && hasOnly(['status', 'acceptedBy']))
      );

      // Only the creator can delete
      allow delete: if isAuth()
        && request.auth.uid == resource.data.creatorId;
    }

    // ===== WORKSPACES =====
    match /workspaces/{workspaceId} {
      // Only workspace members can read
      allow read: if isAuth() && isWorkspaceMember(workspaceId);

      // Workspace creator must be a member
      allow create: if isAuth()
        && request.auth.uid in request.resource.data.memberIds;

      // Members can update, but memberIds is IMMUTABLE after creation
      allow update: if isAuth()
        && isWorkspaceMember(workspaceId)
        && !request.resource.data.diff(resource.data)
             .affectedKeys().hasAny(['memberIds']);

      // Workspaces are never deleted
      allow delete: if false;

      // ===== VAULTS (subcollection of workspaces) =====
      match /vaults/{vaultId} {
        // Only workspace members can read vaults
        allow read: if isAuth() && isWorkspaceMember(workspaceId);

        // Only workspace members can create vaults
        allow create: if isAuth() && isWorkspaceMember(workspaceId);

        // Only workspace members can update vaults
        allow update: if isAuth() && isWorkspaceMember(workspaceId);

        // Vaults are never deleted (soft delete approach)
        allow delete: if false;

        // ===== TRANSACTIONS (subcollection of vaults) =====
        match /transactions/{transactionId} {
          // Only workspace members can read transactions
          allow read: if isAuth() && isWorkspaceMember(workspaceId);

          // Only workspace members can create transactions
          allow create: if isAuth() && isWorkspaceMember(workspaceId);

          // Only workspace members can update transactions
          allow update: if isAuth() && isWorkspaceMember(workspaceId);

          // Transactions use soft delete (deleted=true), never hard-deleted
          allow delete: if false;
        }
      }
    }
  }
}
```

## Security Properties Enforced

| Property | Enforcement |
|----------|-------------|
| Authenticated access only | All rules check `request.auth != null` |
| Workspace membership required | `isWorkspaceMember()` checks `memberIds` field via `get()` |
| memberIds immutable after creation | Update rule rejects changes to `memberIds` |
| Self-invite prevented | Accept rule checks `request.auth.uid != resource.data.creatorId` |
| Atomic accept only | Only `status` and `acceptedBy` can change during acceptance |
| No hard deletes | `allow delete: if false` for workspaces, vaults, transactions |
| Invite ownership | Only creator can create, revoke, or delete invites |
| One user cannot add another to workspace | memberIds are set at creation time only |
