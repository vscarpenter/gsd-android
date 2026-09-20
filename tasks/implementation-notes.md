# Implementation notes (tactical deviations ledger)

## Phase 0

- **Task 1 / AGP 9 built-in Kotlin:** AGP 9.x errors if `org.jetbrains.kotlin.android` is applied — Kotlin support is built into AGP now. Removed the plugin from all Android modules (and the root classpath); `kotlin { jvmToolchain(17) }` still works, and the Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) is still applied explicitly. Plan's build files predated this.
- **Task 1 / KSP versioning:** KSP moved off the `<kotlin>-<ksp>` version scheme; current releases are standalone (2.3.x, Kotlin-version-independent). Pinned `ksp = "2.3.10"` instead of the plan's guessed `2.4.0-2.0.4`.
- **Task 2.4 slip:** wrote impl before running the red step (test was authored first); re-tightened after.
- **Task 2.7:** trailing-swipe reveals delete only; the two-button (snooze+delete) reveal §8.1 describes needs a custom swipe container — snooze is reachable via long-press menu presets meanwhile. Revisit with Phase 4 notification actions.
- **Task 3.6:** Ctrl+1–4 jump-to-quadrant not wired (needs scroll-position plumbing); Ctrl+K/F/N + Esc work. Navigation section lists the four surfaces; smart views appear in their own section rather than duplicated under Navigation.
- **Task 5.5:** SyncEngine drafted API-first before its tests (design-heavy class); the §13.6 matrix suite was written immediately after and is the acceptance gate.

## S1 dead-session sync guard (2026-09-20)

- **Identity check endpoint:** the approved design said the check fetches the signed-in user's own record. It uses `POST /api/collections/users/auth-refresh` and compares `record.id`. A plain record view depends on the `users` view rule, which lives in the PocketBase admin UI and could pass for a guest. auth-refresh needs a valid token whatever the rules say. The returned token is ignored.
- **Coordinator mechanism:** the approved design re-checked `currentUserId()` after `ensureFreshToken()`. The coordinator reads the run's `notSignedIn` flag instead, so the engine stays the one place that decides "no owner". The run aborts before any request, and Sync History records one "Not signed in" row.
- **Copy:** `health_expired` was unused until this change surfaced it. It lost its em dash on the way in.
- **Instrumented tests:** `SyncCoordinator` and the Settings rows need Android classes, so their tests are instrumented (`SyncCoordinatorSessionTest`, `SignedOutAccountTest`). They run with no network: the token sits 30 days from expiry, so no refresh fires.
