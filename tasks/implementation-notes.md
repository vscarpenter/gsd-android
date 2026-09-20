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

## S2 reminder bookkeeping and LWW (2026-09-20)

- **Tier:** Standard. About 20 production lines in three files, no wire or schema change, and the web client already behaves this way. No `tasks/spec.md` for this one; `tasks/spec.md` still holds the S1 spec.
- **Scope decision:** only the "reminder posted" write left the enqueue path. A user-initiated snooze still restamps and syncs, because web (`lib/tasks/crud/snooze.ts`) and iOS (`TaskStore.snooze`) both do. The web's `markNotificationSent` is the model for the new write: a plain put with no `updatedAt` change and no queue row.
- **Test precision:** `TaskStore.add` returns an `Instant` with sub-millisecond precision, and Room stores epoch millis. Compare `updatedAt` values with `toEpochMilli()` in instrumented tests.

## S3 account switch keeps tasks (2026-09-20)

- **Seed timing:** the seed runs once inside `confirmOwner`, not on every nil-cursor sync as iOS does. The iOS cursor only advances when a pull applies a record, so an account where no pull applies one re-seeds every task on every sync.
- **Same-owner sign-in:** unchanged on purpose. No seed and no cursor reset, so a remote delete made while signed out still lands. iOS resurrects such a task; Android does not.
- **Restart guard:** the rebuilt prompt carries the token's user id and no email, and `authManager.lastProvider` is gone after a restart. The email shows again after the next normal sign-in.
- **Found while testing:** the S1 coordinator set `signedIn` before `sessionExpired`. The session test caught it about one run in three. Fixed in `8b8456f`.

## S4 one-shot requests (2026-09-20)

- **Tier:** Standard. About 25 production lines in five files, with no wire or schema change. The plan lived in `tasks/todo.md`; `tasks/spec.md` still holds the S3 spec.
- **Extra composable:** the plan put `repeatOnLifecycle` inline in `MatrixScreen`. It became `palette/ConsumeWhileStarted`, so the lifecycle gate has its own test. That test failed first with `expected [] but was [new-task]`.
- **Test route:** the first palette test left Matrix through the palette's Dashboard row. It passed against the broken code, because the Matrix tab never came back (review finding 43). The tests now leave through the Browse tab and wait on the selected tab.
- **Sheet dismissal:** on the 320x640 `gsd-test` screen the sheet covers the scrim's center, so `performClick()` on "Close sheet" lands on the sheet. The tests invoke the scrim's `OnClick` semantics action.
- **v2 compose rule:** `LaunchIntentTest` uses `junit4.v2.createEmptyComposeRule`. The v1 rule runs effects inline, so the `pendingRoute` collector navigates before the `Scaffold` has composed the `NavHost`. The run then dies with "Navigation graph has not been set". Production posts the effect, and a fresh-install launch on `gsd://capture` through onboarding did not crash.
- **Not changed:** `openTaskRequests` is still a nullable `StateFlow` that its consumer resets. The `onCreate` guard covers its rotation replay. Two stacked activities can still both open the task (review finding 4).
- **Residual:** Ctrl+N or Ctrl+K pressed off the Matrix tab waits for the next visit to Matrix, then opens once. Before the fix it opened on every visit.

## Finding 43 Matrix tab (2026-09-20)

- **Tier:** Standard. About 25 production lines in three files, with no wire or schema change. The plan lived in `tasks/todo.md`.
- **Read the library first:** the fix rests on `NavControllerImpl.kt` in Navigation 2.9.0 (sources jar in the Gradle cache). `executePopOperations` files the saved stack under the `popUpTo` destination on a non-inclusive pop, and `navigate` checks `backStackMap` for a restore right after that pop.
- **Why Matrix pops instead of navigating:** Matrix is always the `popUpTo` target, so a restore brings back the screen the same call popped. For the other three tabs the saved stack is filed under their own route, which is what multi-back-stack needs.
- **The wrong turn:** the first fix kept `navigate()` and set `restoreState = destination != MATRIX`. In `navigate`, `launchSingleTop` only runs when the restore branch does not, so the old code never reached it for Matrix. The new call did, and `launchSingleTopInternal` replaced the visible entry with a copy that stayed at CREATED (`ON_CREATE`, then nothing, confirmed by hand with a lifecycle observer). `collectAsStateWithLifecycle` and `ConsumeWhileStarted` both went silent. The S4 `LaunchIntentTest` failed in the full suite and pointed at it.
- **I misread that failure at first.** I took the failing line for the wait on the sheet opening. It was the wait on the sheet closing. Logging `openNew` and `dismiss` with the view model's identity settled it in one run.
- **Guard test:** `aSiblingTabKeepsItsStackAcrossAVisitToMatrix` passed before the change, so I proved it by mutation. With `restoreState = false` for every tab it failed on the missing Archive screen.
- **Deterministic red for the doubled entry:** the test waits for `pendingRoute` to clear before it presses Back. Without that wait, Back can land before the router pushes the second Matrix entry, and the test passes against broken code.
- **Known cost:** an entry popped with `saveState` keeps its view model until something restores it. A smart view pushed over Matrix is never restored, so its view model lives until the activity ends. The old code had the same cost on the same paths.
- **Not changed:** with a sub-screen open, no tab shows as selected. That was already true for Archive opened from Browse.
