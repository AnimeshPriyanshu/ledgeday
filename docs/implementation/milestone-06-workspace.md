# Milestone 6 — Workspace Feature

**Complexity**: 2 / 5
**Tasks**: T6.1, T6.2, T6.3, T6.4

## Description
Workspace CRUD: list screen with ViewModel, create dialog with validation, delete with confirmation, navigation wiring.

## Summary of Tasks
| Task | Title | Files |
|------|-------|-------|
| T6.1 | `WorkspaceListViewModel` (includes unit tests) | `:feature:workspace/` |
| T6.2 | `WorkspaceListScreen` (list + delete confirmation) | `:feature:workspace/` |
| T6.3 | `CreateWorkspaceDialog` | `:feature:workspace/` |
| T6.4 | Wire navigation → vault list | `:app/navigation/` |

## Completion Criteria
- [ ] Can create workspace with name validation
- [ ] Workspace list updates reactively via Room Flow
- [ ] Empty state visible when no workspaces
- [ ] Delete with confirmation and cascade warning
- [ ] ViewModel unit tests pass
