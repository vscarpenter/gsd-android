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

## S6 state changes never reject (2026-09-20)

- **Tier:** Non-trivial. It changes `TaskStore`, the single mutation path, and spec 7.11. The design went through the one approval gate, with three options and a recommendation.
- **Reproduction before design:** the red store tests came first, because they hold under any of the three designs. They gave the proposal its evidence.
- **Sibling check:** the web validates in `create.ts` and `update.ts` only. iOS validates in capture, create, and `save` only. Android alone validated on every mutation, and it did so because spec 7.11 said "every write path". The spec was the outlier, so the spec changed.
- **`validate` is built on `normalize`:** one function holds the coercions, so the two levels cannot drift. The limit checks now read the normalized task, which is what the old code did for tags and the estimate.
- **Private `write` helper:** `add`, `update`, `change`, and `duplicate` share one enqueue-then-write transaction. `setCompleted` keeps its own, because the completion and the spawn must land together.
- **Duplicate moved into the store:** the view model used to build the copy and call `add`. `TaskStore.duplicate(id)` now owns it, as the iOS store does.
- **Honest red for new API:** `normalize`, `change`, and `duplicate` first went in as stubs over the old behavior, so the tests failed on `TitleLength` and not on a compile error.
- **Gate detour, not an S6 change:** the first full run failed `PaletteRequestsTest`, an S4 test. My first guess (window focus) was wrong for that test: the log showed the dialog held focus, and `dumpsys input_method` showed the soft keyboard taking the Back press. A second test in `TabNavigationTest` then failed about one combined run in four, and that one was the focus race. Both are fixed in `0e01966`, and the two classes passed 8 combined runs in a row.

## S5 completion is a transition (2026-09-20)

- **Tier:** Standard. One store method and about ten lines. No `tasks/spec.md` change; it still holds the S6 spec.
- **Sibling check:** the web reads inside its transaction under a per-task lock and spawns only on the false-to-true change. iOS reads the persisted row before it decides. Android read outside the transaction and never compared states.
- **One step past the web:** a redundant call writes nothing here. The web still restamps `updatedAt` and `completedAt`. The reason is the S2 lesson about bookkeeping writes and last-write-wins.
- **The race was worse than the review said:** the double-fire test used eight concurrent calls so the red run could not pass by luck. All eight spawned.
- **Red check for a test written after the fix:** the bulk Done test came after the store change, so I swapped the old `TaskStore.kt` back for one run. It failed with `expected 2 but was 3`, then passed with the fix restored.

## Merging the review fixes (2026-09-20)

- **Method:** seven stacked pull requests, #3 to #9, merged bottom-up with merge commits, which is how #1 and #2 were merged. `main` had not moved since the stack began, so after each merge `origin/main` matched the merged branch exactly.
- **"No issues" needed a definition.** I used: a finished review, every comment read, and nothing above a nit left open. Four CI reviews were no-ops, so I ran fresh-context reviews for #4, #6, #9, and the late fixes on #5.
- **#5 changed under review.** The CI review found the erase and cursor ordering bug. I fixed it in a worktree so the reviewers reading the main tree were not disturbed. A second reviewer then found that my new test had gone vacuous, that the spec contradicted the code in three places, and a sibling bug with an unrecorded first owner. All three landed in #5 before it merged.
- **#6 merged with a known regression, by owner decision.** I reproduced it by hand first: app on screen, capture link sent, no sheet on the visible activity, sheet found on the covered one after Back. I did not improvise a lifecycle fix inside the merge window, because the proper cure is finding 4.
- **Not done:** no branch was deleted, and no PR comment was posted. Both need the owner's go-ahead.

## Finding 4 one MainActivity (2026-09-20)

- **Tier:** Standard. One manifest attribute, with no Kotlin change. `onNewIntent` already called `handleIncomingIntent`. The design went through the one approval gate.
- **Reproduction before design:** `RunningAppLinkTest` came first. Its instance count fails under the activity-scoped controller and under the RESUMED gate too, because both leave the second copy in place. That settled the choice of design.
- **`singleTask`, not `singleTop`:** the sign-in Custom Tab sits above `MainActivity` in GSD's task. `singleTop` would still stack a copy on the OAuth callback.
- **Reviewer claims, both confirmed:** the static shortcuts carry `NEW_TASK`, `CLEAR_TASK`, and `TASK_ON_HOME` (`dumpsys shortcut`, `flg=0x1000c000`). The reminder tap's `SINGLE_TOP` already landed in `onNewIntent` before this change.
- **Mutation check:** the three tests went red on the missing launch mode, so none had proved it guards `onNewIntent`. With the `handleIncomingIntent` call commented out, all three failed.
- **Stray copies in a red run:** the scenario closes only the activity it launched. The test class finishes any other `MainActivity` in `@After`, so one failure cannot leak into the next test.
- **Not changed:** `openTaskRequests` is still a nullable `StateFlow`. One instance ends its double-open, so it needs no one-shot treatment.

## Finding 1 the options button (2026-09-20)

- **Tier:** Trivial. About ten production lines in two files. One decision went to the owner: hide the button on a card with no menu, or disable it. The answer was hide.
- **Wider than the review said:** the review named an empty lambda. Six surfaces draw a task card, and three of them have no menu: Trash, Archive, and the tablet grid. Filling in the lambda would have left a dead button on all three.
- **One callback, not two:** `onLongPress` became `onOpenMenu`. A second parameter would let the two gestures drift apart.
- **Menu position unchanged:** the menu still anchors to the row, as it does after a long press. Moving it under the button is polish, and nobody asked for it.

## Finding 2 the Trash row background (2026-09-20)

- **Tier:** Trivial. One `when` in one file. I skipped the design gate, because a row at rest has one right answer.
- **The cause was an `else`:** `SwipeToDismissBoxValue` has three values. The `else` branch gave `Settled` the red delete background, and the background content is always composed.
- **First pixel tests in the repo:** `captureToImage().toPixelMap()` on a row whose content paints nothing. Compare with `toArgb()`, and read the expected colors from `LocalGsdColors` inside the composition so the test holds in either theme.
- **Sample points:** on `gsd-test` one dp is one pixel. The mid-swipe test samples 8 px from the left edge at mid height. That point is left of the check icon and clear of the rounded corners.
- **Not done:** an opaque paper layer under the dimmed Trash row. It would stop the background showing through during a swipe. The finding is about the resting state, so I left it.

## Review of #13 (2026-09-20)

- **The CI review said nothing, and that was not a verdict.** The run log showed `num_turns` 27, `permission_denials_count` 11, and no comment on the PR. The action hides the result text, so I ran a fresh-context reviewer.
- **I verified each claim before acting.** The `singleTop` claim took one mutation run. The launcher tap claim took one `am start` with the launcher's action, category, and flags `0x10200000`. Finding 47 took one grep for the consumers of `openTaskRequests`.
- **Follow-ups went on #13's own branch.** They touch only `RunningAppLinkTest.kt` and spec 6.4 and 10.2, which no branch above it edits. The shared records (this file, the todo, the review, the lessons) went on the top of the stack, so nothing conflicts.
- **Not acted on:** the Recents replay is speculative, and I have no failing test for it. It is in the todo.

## Finding 3 swipe actions run once (2026-09-20)

- **Tier:** Standard. One composable, but both swipe directions change how they fire.
- **Measure before fixing:** the review guessed that `confirmValueChange` fired twice. A counter in a component test showed 8 and 9. The fix for two calls and the fix for nine are the same, but the count explained the completion path too.
- **Read the library:** the Material 3 1.4.0 sources jar is in the Gradle cache. It shows `onDismiss` called from `LaunchedEffect(state.settledValue)`, and the official sample resets the state afterward. `SwipeTaskRow` follows that sample.
- **A test that never went red:** `SwipeDeleteUndoTest` passed on the old code 8 times out of 8. I tried a 1.5 second swipe on the theory that speed hid the race. It passed 3 more times. I kept the test as a guard, said so in its KDoc, and proved it by mutation.
- **Reviewer nits I left alone in #15:** the two color fields in `SwipeTaskRowTest` are not `@Volatile`, and a failed color assert prints decimal.

## Finding 48 the task menu works on every list (2026-09-20)

- **Tier:** Non-trivial. One new view model shared by three screens, and about ten files. The owner chose option A and approved the design in chat.
- **Follow the pattern that was already there:** each screen already creates a `TaskEditorViewModel` beside its own view model. `TaskActionsViewModel` works the same way, so it needed no interface and no delegation.
- **Moved, not copied:** `BrowseViewModel` had one copied method and none of the other six. The move leaves one copy of each operation in the app.
- **No shims:** `MatrixViewModel` kept no forwarding methods. Its one test that used them, the S6 over-limit test, moved to `TaskActionsViewModelOverLimitTest`.
- **All ten tests red before any wiring:** each list test failed on its last line, after the menu opened and the item was tapped. I checked the line numbers, because a broken row matcher would have failed earlier and for the wrong reason.
- **A wait that passed too soon:** in the Browse test, the typed query put the title on screen before any result row existed. The menu helper now waits for the row's own "Task options" button.
- **Comments moved into the new file lost their em dashes.** Moved prose counts as revised prose.
- **Not done:** swipes on the two lists (finding 49) and the celebration outside Matrix (finding 50).

## Review of #18 (2026-09-20)

- **One real bug, older than the PR and spread by it:** a cancelled `UndoDeleteEffect` skipped its cleanup. I extracted that effect for three screens, so the stale Undo offer went with it.
- **I tested the reviewer's fix before using it.** A plain `finally` cleared the record on every cancellation, and a second delete is a cancellation. The guard test failed on it, so the clear became `compareAndSet(shown, null)`.
- **I dropped the explicit `dismiss()`.** Material's `showSnackbar` clears `currentSnackbarData` in its own `finally`, under its mutex (1.4.0 sources). A dismiss from my `finally` could have closed the newer snackbar.
- **My iOS claim was half true.** iOS drops the menu button and the disc in select mode, and keeps `.contextMenu`. I had read line 26 of `TaskListRow.swift` and not line 48. The review doc is corrected. The commit message and the first PR text were already published.
- **Not settled:** whether the snackbar hides under a docked keyboard. `gsd-test` has none.

## Finding 5 a snoozed reminder fires again (2026-09-20)

- **Tier:** Trivial. One condition in a pure function. I skipped the design gate, because both siblings already fire again after a snooze.
- **The spec was right and the code was not.** Spec 11.1 says a future snooze is the fire time. The planner had put the `notificationSent` check ahead of it.
- **I corrected my own reasoning midway.** I argued for the planner because a snooze could arrive by sync. Spec 4.7 says `snoozedUntil` is device-local, so that case does not exist. The planner is still the right place: it is pure, and it covers the notification button, the menu presets, and the editor.
- **An alarm is not observable from a test.** `ReminderScheduler.cancel` builds the same `PendingIntent` that `schedule` does, so `FLAG_NO_CREATE` cannot tell the two apart. The on-device test asserts the planner's answer for the state the real receivers leave behind.

## Finding 47 a reminder tap opens the task (2026-09-20)

- **Tier:** Trivial. The spec already named the mechanism, so there was nothing to design.
- **Refactor under green first:** I pulled the tap intent into `ReminderReceiver.tapIntent` with its old shape and ran the five notification tests. Then I wrote the red test against that function, and then I changed the shape. The test sends exactly what the receiver sends.
- **One router, not two:** `MainActivity` had a second way into a task, the extra, and it skipped the navigation the link does. Removing it left one path, which the deep link tests already cover for cold start and a missing task.
- **No shim for old notifications:** a posted notification holds the old intent only until the app updates. Android clears an app's notifications when its package is replaced. I did not verify that on a device. If an old notification did survive, its tap would open the app and no task. That is what it did before from any tab but Matrix.
