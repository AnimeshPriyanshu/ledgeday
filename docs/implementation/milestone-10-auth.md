# Milestone 10 — Auth Integration

**Complexity**: 3 / 5
**Tasks**: T10.1, T10.2, T10.3, T10.4, T10.5, T10.6, T10.7

## Description
Firebase Authentication with email/password. Splash screen checks auth state. Login/register form with error handling. Logout in settings.

## Summary of Tasks
| Task | Title | Files |
|------|-------|-------|
| T10.1 | Configure Firebase in project | `:app/build.gradle.kts`, `google-services.json` |
| T10.2 | `AuthRepository` interface + `FirebaseAuthRepository` | `:core:domain/repository/`, `:core:data/repository/` |
| T10.3 | `SplashViewModel` + `SplashScreen` | `:core:ui/screen/` |
| T10.4 | `AuthViewModel` (includes unit tests) | `:core:ui/screen/` |
| T10.5 | `AuthScreen` (login/register form) | `:core:ui/screen/` |
| T10.6 | Wire auth-aware navigation graph | `:app/navigation/`, `MainActivity` |
| T10.7 | Logout in `SettingsScreen` | `:feature:settings/` |

## Completion Criteria
- [ ] Can register with email/password
- [ ] Can log in with existing credentials
- [ ] Auth state persists across app restarts
- [ ] Unauthenticated → Auth screen, authenticated → Workspace List
- [ ] All Firebase errors show user-friendly messages
- [ ] Logout clears back stack to Auth
