# GSD Android — progress

## Done
- **Phase 0** foundations · **Phase 1** core local app (`v0.1.0-phase1`) · **Phase 2** task depth
  (`v0.2.0-phase2`) · **Phase 3** organization & insight (`v0.3.0-phase3`) · **Phase 4**
  notifications (`v0.4.0-phase4`)
- **Phase 5 — sync** (`v0.5.0-phase5`): snake_case wire mapper with lossy time entries and
  device-local preservation; lenient PocketBase dates + 5 s cursor rewind; JWT payload decode;
  SSE frame parser + apply rules (echo filter, owner fail-closed, delete queue-protection);
  OkHttp PocketBase client (bare-JWT header, typed 429/401); the LWW SyncEngine passing the full
  §13.6 matrix (pull/push/reconcile/backoff/failed/429-abort/history/health); PKCE auth with
  state validation, Keystore-encrypted token, 7-day-skew refresh with transient/definitive split;
  SyncCoordinator trigger fabric (launch/foreground/network/120 s cadence/1.5 s debounce/manual,
  failed-requeue on the §9.5 set, foreground-only SSE with backoff + post-drop full sync);
  device identity; Account settings, quiet sync chip, sync history screen, account switch prompt,
  delete account, erase-everywhere (remote-first).
- **Live-verified without credentials:** OAuth bounce page client-agnostic (GET static forward,
  POST 302 promotion); auth-methods envelope + all three providers with PKCE material.

- **Phase 6 — native surfaces** (`v0.6.0-phase6`): §6.4 deep-link router (all routes, cold-start
  pending route, not-found toast — smoke-tested per route via adb), §12.2 share target with URL
  title derivation, §12.1 Glance Today's Focus widget (push-updated snapshot, day-night GSD
  colors, deep-link taps), §6.5 static shortcuts. Deferrals: share-sheet title enrichment
  (fetchShareTitles fetch stack), quadrant-route scroll position, widget render verified by
  build + route smoke only (place from the launcher for a visual pass).

- **Phase 7 — Play readiness** (`v0.7.0-phase7`): adaptive icon + monochrome layer, minified
  release build (R8 clean) + profileinstaller, accessibility audit (0 unlabeled clickables,
  2× reflow pass, reduced-motion by construction — `docs/play/accessibility-audit.md`),
  Data Safety answers + permission declarations, listing copy + shot-list, submission checklist.
  Version 0.7.0; Settings reads BuildConfig.

## Cross-platform parity catch-up (2026-08-29) — COMPLETE
The web and iOS clients ran a parity push on 2026-08-28/29 that this repo predated. The gaps
and exact contract values were specified in `docs/2026-08-29-cross-platform-parity-spec.md`
(which won over `docs/gsd-android-spec.md` until Task 12 folded the amendments back in — done,
see below). Implementation plan: `tasks/parity-plan.md` (all twelve tasks checked off). Fixtures
are byte-copied from `../gsd-taskmanager/tests/fixtures/cross-platform/`. Verified non-gaps
(no work needed): field limits, design tokens, quiet-hours seed (22:00–07:00 matches iOS; web's
08:00 is the outlier), wire model, reminder presets.

**All twelve tasks landed, commits `f64b325..HEAD`:**
1. Capture-parser corpus alignment — `d381af6`
2. Backup envelope types + export 2.1.0 — `6b13a32`
3. Envelope import (lenient, three-array cap) — `a6b8150`, `1af6924`
4. Cross-platform backup fixture pair — `dca33c5`
5. Trash — Room v2, `TrashStore`, delete rewiring — `e2ad1cb`, `a52cd41`
6. Backup assembly + import application across the seven stores — `d86141d`
7. Trash surfaces (undo = restore, Trash screen, purge scheduling) — `35cca0d`, `e330eba`
8. Sync tombstone suppression (archive + trash, pull + SSE) — `3e7bf76`
9. Feedback contract (seven-key payload, 8 Android slugs incl. `wear-os`) — `1fd37ea`
10. Feedback submission + Settings surface — `018c83b`, `de92e4a`
11. Two-tier Apple sign-in note — `5ace1c6`
12. Fold amendments into `docs/gsd-android-spec.md`, README, this file; full verification sweep
    — this commit

`docs/gsd-android-spec.md` now carries `> Amended 2026-08-29` markers at every touched section
(§4.4, §7.10, §8.16, §8.20, §9.4, §9.6, §8.2, Appendix C, §8.17, §10.4, §6.4, Appendix B) and
describes what the shipped code does — it is the spec of record again; the parity spec document
stays as the dated record of *why*.

**2026-08-29 spec verification + revision (done):** a claim-by-claim check against all three
repos corrected five rules the draft had presented as "shared" sibling behavior — restore now
preserves original timestamps (faithful undo, matches web+iOS; NOT a fresh `updatedAt`),
singletons import in replace mode only (merge never adopts another device's configuration),
erase-clears-trash is attributed to iOS (the web's omission is an upstream bug), the lenient
per-record import is a declared Android choice (web is all-or-nothing), and the start-of-day
purge cutoff is iOS's rule (web uses a rolling instant). The plan's Task 5/6/10 code sketches
and tests were re-aligned to match. Do not re-introduce the fresh-stamp or both-modes-singleton
behavior; the spec labels every contested rule with its source client.

### Ship path (in order)
1. ✅ **Parity plan Tasks 1–4** (`:core:model`, JVM-only, fast): corpus parser alignment, envelope
   export/import, cross-platform fixture pair. Gate: `./gradlew :core:model:test` sub-second.
2. ✅ **Tasks 5–8** (`:core:store` + `:core:sync`, emulator needed for instrumented tests): Room
   v1→v2 trash, backup assembler/applier, trash UI + undo-as-restore, sync tombstone guard.
3. ✅ **Tasks 9–11** (`:app`): feedback payload/API/screen, two-tier Apple note.
4. ✅ **Task 12**: fold amendments into `docs/gsd-android-spec.md`; full sweep
   (`testDebugUnitTest`, `connectedAndroidTest`, `:app:assembleRelease`).
5. ✅ **Release build**: 0.8.0 (versionCode 8) committed (`112a099`); signed
   `app-release.aab` (6.4 MB) and `app-release.apk` (4.8 MB, aapt2-verified 0.8.0) built from
   the post-fix-wave tree in `app/build/outputs/`.
6. ☐ **Release smoke on a device**: sideload the release APK (`adb install -r` — a debug-signed
   install must be uninstalled first, which wipes local data; back up or sync first) and walk
   the new surfaces (trash, feedback, restore-syncs-as-create, v1→v2 migration on upgrade).
7. ✅ **Play submission** (2026-08-29): the 0.8.0 `.aab` went straight to the **Closed testing
   – Alpha** track (submission #2, Published 5:19 PM; #1 was canceled and resubmitted). Google
   reviewed and published the release, store listing, app content, and store settings.
8. ☐ **Closed-test gate** (owner, `docs/play/submission-checklist.md`): 12 opted-in testers for
   14 continuous days, then "Apply for production". As of 2026-09-06 the Console reports
   **0 testers opted in**; the clock has not started.

### Upstream follow-ups (not Android's, don't block)
- Web: `resetEverything` leaves `deletedTasks` + `appPreferences` behind (privacy-shaped).
- Web: trash-merge import overwrites the local row on id collision (archive rule says skip).
- iOS: no tombstone-suppression guard on pull/SSE — resurrects archived/trashed ids.

## Cross-platform parity catch-up — round 2 (2026-09-06) — COMPLETE
The siblings kept shipping after the August round: iOS #16 (`18dd222`, 2026-09-01 — completion
rings, day-based deadline status, Browse root search, first-frame skeletons), the off-list
reminder rule (iOS #12 / web #520), and the web's 2026-09-04 security review (#527 — smart-view
bounds, bounded fail-closed remote scans, three-verdict remote-absence rule, malformed-version
push guard, route-first account deletion, erase completeness). Audited claim-by-claim 2026-09-06;
fixtures still byte-identical in all three repos. Owner approved the full scope the same day.
- **Spec:** `docs/2026-09-06-cross-platform-parity-spec.md` (won over the main spec for the
  sections it touches until Task 15 folded them in — done, see below).
- **Plan:** `tasks/parity-plan-2026-09-06.md` — 15 TDD tasks, module order, one commit each.
- **Pre-existing Android gaps the audit surfaced** (all fixed by the tasks below): instant-based
  dashboard "upcoming" hid a task due earlier today; upcoming rows were inert; the editor showed
  "None" for an off-list reminder; push overwrote a remote record with an unparseable version;
  the palette had no feedback action; erase left the feedback draft; smart-view import skips were
  uncounted.
- **Branch:** `feat/parity-2026-09` (docs `b7772f3`, `07b1b50`, `3886978`, `7d2cab1`, then the
  code). Not merged, not pushed.

**All fifteen tasks landed** — the sixteen code commits from `585a125` through `d8ef6e5`, the AGP
pin `e2240cb`, and this docs commit:
1. Per-quadrant completion stats, `DeadlineStatus`, rounded percent, day-based upcoming — `585a125`
2. Shared filtered-list ordering (`TaskOrdering.forList`) + trimmed search query — `1e202cd`
3. `ReminderOptions.including` + `ReminderLabel.of` (off-list offsets, never snapped) — `18aa868`
4. Smart-view bounds on import/export, every skip counted — `00188fc`
5. Bounded, fail-closed remote listings driving pull, index, and erase — `b398541`, `55c88a3`
6. Malformed remote version never authorizes an overwrite — `9fc9cfc`
7. Three-verdict remote-absence reconciliation (`abandonToTrash`) — `0abc4ee`
8. Route-first account deletion, bounded fallback, surfaced outcomes — `518dec1`, `e4d4439`
9. Tiered feedback failure copy; erase clears the saved draft — `25108ec`
10. "Send feedback" in the command palette — `4f0c12f`
11. First-frame skeletons on the Matrix, Dashboard, and smart-view lists — `269f10d`
12. Completion rings, day-based deadline badges, tappable upcoming rows — `03d180c`, `602715e`
13. Browse root search over every active task, in the shared list order — `e48f0ce`
14. Editor renders an off-list stored reminder instead of "None" — `d8ef6e5`
15. AGP `9.3.2` pinned (`e2240cb`); fold amendments into `docs/gsd-android-spec.md`, README,
    this file, `tasks/lessons.md`; full verification sweep — this commit

`docs/gsd-android-spec.md` now carries `> Amended 2026-09-06` markers at every section S§9 named
(§5.10, §6.1, §7.9, §8.1, §8.3, §8.13, §8.14, §8.15, §8.16, §8.17, §8.21, §9.4, §9.5, §13.3,
Appendix B) and describes what the shipped code does — it is the spec of record again; the parity
document stays the dated record of *why*.

**Verification sweep (2026-09-06, all green, AGP 9.3.2, emulator-pinned where instrumented):**

| Command | Result |
|---|---|
| `./gradlew :core:model:test` | **130** tests, 0 failed, 0 skipped — BUILD SUCCESSFUL in ~1 s |
| `./gradlew testDebugUnitTest` | **119** tests, 0 failed (`:core:sync` 80, `:app` 39; `:core:store` has no JVM unit tests) — BUILD SUCCESSFUL |
| `ANDROID_SERIAL=emulator-5554 ./gradlew connectedAndroidTest` | **110** instrumented tests, 0 failed, 0 skipped (`:app` 69, `:core:store` 41) — BUILD SUCCESSFUL in 1m 26s |
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |

`ANDROID_SERIAL` is not optional here: a Pixel 10a is attached over wireless ADB, and without the
pin the instrumented run installs on the phone instead of the emulator.

**Declared absences (do not flag in a future parity audit)** — spec S§1.2: the web's Review
verdict + stat rail (#519) and its returning-user feedback nudge (#525, web-only by decision —
Android, like iOS, keeps Settings and the palette as its only feedback entry points); the iOS
stat-card digit morph; the web's pre-parse smart-view structural walker, its read-path guard, and
`.strict()` unknown-key rejection; the web's sign-in ownership guard over three stores; Sentry key
trimming. Android's "None" reminder still leaves `notificationEnabled` alone (out of scope).

## Resuming From Here (2026-09-20, S4 one-shot requests)
- **Done:** review finding S4 is fixed on branch `fix/new-task-requests-one-shot`, stacked on the S3 branch. Four
  code commits (`2cf82b3`, `2f77612`, `1d0f38c`, `fdbf6f5`) plus one docs commit. Not merged. The last three code
  commits and the docs commit are not pushed.
  - `palette/OneShotRequest` holds a request until a consumer claims it with `compareAndSet`.
  - `palette/ConsumeWhileStarted` consumes only while the screen is at least STARTED. `MatrixScreen` uses it for the
    New Task and palette requests.
  - `TaskEditorViewModel.openNew` keeps an open draft.
  - `MainActivity.onCreate` handles the launch intent only when `savedInstanceState` is null.
  - Spec 6.4 and 6.7 are amended.
- **Proof:** six on-device tests failed first. The sheet and the palette came back after a trip to the Browse tab.
  A stopped screen took a request (`expected [] but was [new-task]`). A second `openNew` wiped a half-typed title and
  swapped an open edit for a blank task. A recreation reopened the sheet. Gates on the branch: `:core:model:test`
  132, `testDebugUnitTest` 138 (`:core:sync` 95, `:app` 43), `connectedAndroidTest` 125 on `gsd-test` (`:app` 83,
  `:core:store` 42), `:app:assembleDebug`, and `:app:assembleRelease` (R8 clean). All green.
- **Found on the way:** review finding 43. After a deep link or a palette Navigation row opens another screen, the
  Matrix tab does nothing, and only Back reaches Matrix. The widget header and the "Today's Focus" shortcut both
  lead into it. Not fixed. Recommended next, ahead of S5 and S6, because every widget tap leads into it.
- **Emulator:** the `google_apis` API 36 system image lost its `.img` files again mid-session. `sdkmanager --install`
  did nothing, because the leftover `package.xml` made it look installed. `--uninstall` then `--install` restored
  it (4.3 GB). The `google_apis_playstore` image is broken the same way and no AVD uses it. The cause is unknown.
- **Next:** owner decision to merge the four branches in order: S1, S2, S3, S4. Then finding 43, S5, and S6, each
  test-first.
- **Blockers:** none.
- **Assumptions:** an open editor wins over a new-task request. A request made off the Matrix tab waits for the next
  visit to Matrix, then opens once. Review finding 4 (`launchMode` and stacked activities) stays a separate change.

## Resuming From Here (2026-09-20, S3 account switch keeps tasks)
- **Done:** review finding S3 is fixed on branch `fix/account-switch-keeps-tasks`, five commits from `f9a0ab1` to
  `9bd2aba`, stacked on `fix/reminder-bookkeeping-lww`. Not merged, not pushed. The pure `OwnerChange.adopt` in
  `:core:sync` clears the pull cursor on an owner change and, for "Keep my tasks", seeds one unstamped UPDATE row
  per active task that has no queue row. `SyncCoordinator.confirmOwner` runs it and records the new owner last.
  `SyncCoordinator.start` raises the "Different account" prompt again when the saved token's user differs from
  the last confirmed owner and tasks exist. Spec 8.21 and 9.4 are amended.
- **Proof:** a JVM test reproduced the loss first (`expected [synced-1, synced-2, unsent] but was [unsent]`). Three
  on-device tests failed first too: the kept task never reached the new account, the new account's older task
  never arrived, and the prompt never came back. Gates on the branch: `:core:model:test` 132, `testDebugUnitTest`
  134 (`:core:sync` 95, `:app` 39), `connectedAndroidTest` 119 on `gsd-test` (`:app` 77, `:core:store` 42),
  `:app:assembleDebug`, and `:app:assembleRelease` (R8 clean). All green.
- **Also fixed:** the S1 coordinator set `signedIn` before `sessionExpired`, and the session test caught it about
  one run in three. Commit `8b8456f` swaps the order and makes the test wait for coroutines before it closes the
  database.
- **Decisions:** the seed runs once at the decision, not on every nil-cursor sync as iOS does. A same-owner
  sign-in changes nothing, so a remote delete made while signed out still lands.
- **Next:** owner decision to merge the three branches in order: S1, S2, S3. Then S4 (one-shot New Task requests
  and the launch intent), S5, and S6, each test-first.
- **Blockers:** none.
- **Known gap:** a prompt rebuilt after a restart has no email and no provider, so Settings shows no email until
  the next normal sign-in.

## Resuming From Here (2026-09-20, S2 reminder bookkeeping and LWW)
- **Done:** review finding S2 is fixed on branch `fix/reminder-bookkeeping-lww`, three commits from `45d0a7e` to
  `d79ad2a`, stacked on `fix/sync-dead-session-guard`. Not merged, not pushed. `ReminderReceiver` now records a
  posted reminder through the new `TaskStore.markReminderSent`, one column update that never restamps `updatedAt`
  and queues no push. Spec 4.7 and 11.2 are amended, and the README's enqueue invariant names the exception.
- **Proof:** both new tests failed first on the restamp. The store test saw `updatedAt` move from 12:00 to 13:00,
  and the receiver test saw it move after a real broadcast. Gates on the branch: `:core:model:test` 132,
  `testDebugUnitTest` 127, `connectedAndroidTest` 116 on `gsd-test` (`:app` 74, `:core:store` 42),
  `:app:assembleDebug`, and `:app:assembleRelease` (R8 clean). All green.
- **Scope decision:** snooze still restamps and syncs. Web and iOS both treat a user-initiated snooze as a
  synced write, so changing Android alone would break parity. Only the "reminder posted" write is local-only,
  which is what the web's `markNotificationSent` already does.
- **Next:** owner decision to merge both branches, S1 first. Then S3 (re-enqueue on "Keep my tasks" and reset the
  cursor on owner change), S4 (one-shot New Task requests and the launch intent), S5, and S6, each test-first.
- **Blockers:** none.
- **Emulator note:** `gsd-test` boots from a snapshot that holds a release-signed 0.9.0 install. The first
  connected run after each boot fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, removes that install, and the
  second run passes. The snapshot restores it on the next boot.

## Resuming From Here (2026-09-20, S1 dead-session sync guard)
- **Done:** review finding S1 is fixed on branch `fix/sync-dead-session-guard`, seven commits from `97cb8d3` to
  `cbe1067`. Not merged, not pushed. `SyncEngine.runSync` aborts with `NotSignedIn` when no owner resolves.
  `reconcileDeletions` calls the new `SyncRemote.confirmIdentity` before the first ABANDON or APPLY verdict.
  `PocketBaseApi` implements it with auth-refresh plus a record-id comparison. `SyncCoordinator` ends the session
  on `notSignedIn` and raises `sessionExpired`, and Settings shows the expired note. Spec 9.4 and 10.3 are amended.
- **Proof:** the first red test deleted the synced task with a null owner and an empty listing, so the engine half
  of S1 was real whatever the server does. Gates on the branch: `:core:model:test` 132, `testDebugUnitTest` 127
  (`:core:sync` 88, `:app` 39), `connectedAndroidTest` 114 on `gsd-test` (`:app` 73, `:core:store` 41),
  `:app:assembleDebug`, and `:app:assembleRelease` (R8 clean). All green.
- **Next:** owner decision to merge the branch. Then S2 (reminder bookkeeping must not restamp `updatedAt`), S3
  (re-enqueue on "Keep my tasks" and reset the cursor on owner change), S4 (one-shot New Task requests and the
  launch intent), S5, and S6, each test-first. `SyncCoordinatorSessionTest` is a harness S3 can reuse.
- **Blockers:** none.
- **Assumptions:** PocketBase answers a guest listing with 200 and zero items. The fix no longer depends on it.
  A dead token with more than 7 days left now fails each run with a 401 from the identity check, and the user
  stays signed in. Signing them out on that 401 is a possible follow-up, left out of scope.
- **Build output note:** `app/build/outputs/apk/release/app-release.apk` was rebuilt from this branch at
  versionCode 9. It is not the 0.9.0 artifact that went to Play. The `.aab` was not rebuilt.
- **Uncommitted on purpose:** this file and `docs/play/2026-09-20-polish-review.md`. The amended spec points at
  the review doc, so commit both when you are ready.

## Resuming From Here (2026-09-20, pre-production polish review)
- **Done:** a read-only polish and launch-readiness review of 0.9.0, run live on the `gsd-shot` emulator (API 36)
  plus a full read of `:app`. No code changed. Findings, evidence, and a fix order live in
  `docs/play/2026-09-20-polish-review.md`. Both files from this session are uncommitted.
- **Data safety first (review S1 to S6):** a dead session lets sync hard-delete every synced local task, because
  only `push()` checks for an owner (`SyncEngine.kt:123`, `:197-208`, `SyncCoordinator.kt:63-67`). The server's
  guest response is the one unverified link. A firing reminder restamps `updatedAt` and pushes a stale copy
  (`ReminderReceiver.kt:74-77`). "Keep my tasks" on an account switch deletes the kept tasks, and the cursor never
  resets. The New Task sheet reopens on every return to Matrix once the shortcut has run (`AppContainer.kt:93-101`),
  and rotation re-runs the launch deep link (`MainActivity.kt:100`).
- **Headline results:** twenty more defects break visible behavior. The worst five are small fixes: the dead "..."
  button on every task card (`TaskCard.kt:128`), the Trash screen drawn over the red swipe background, the missing
  Undo snackbar after a swipe-delete, deep links and widget taps that stack `MainActivity` copies and do not
  navigate, and "Snooze 1 hour" reminders that never fire again (`ReminderPlanner.kt:24`).
- **Reminder gap:** the editor has no date or time picker, so every Android due date is midnight. The app never
  asks for the notification permission in the normal flow, and exact alarms are denied by default on Android 14+
  with no in-app path to grant them. The listing promises exact timing.
- **Policy items:** no in-app privacy policy link, Auto Backup on while the copy says tasks never leave the
  device, `data-safety.md` predates the feedback form, and the 0.9.0 versus 1.0.0 call for production.
- **Next:** fix S1 to S6 test-first, starting with a null-owner engine test that must delete nothing. Then
  findings 1 to 5, then 6 to 8 (reminders), then 9 to 14 and 37 to 42, then the policy items and the privacy page.
  The production application can proceed in parallel. Correct `docs/play/accessibility-audit.md` afterward:
  its custom-actions and title line-cap claims do not match the code.
- **Blockers:** none for the work itself. Owner decisions: the production version number, and whether to disable
  cloud backup or soften the privacy copy.
- **Assumptions:** the double `confirmValueChange` call is the probable cause of the missing Undo snackbar, not a
  proven one. The keyboard-overlap question in the editor sheet is unverified, because the emulator only showed
  the floating stylus toolbar.
- **Emulator note:** `gsd-shot` now runs the 0.9.0 debug build with its seeded data intact. A "Today's Focus"
  widget from this session is still on its home screen.

## Resuming From Here (2026-09-06, parity round 2 landed)
- **Merge state:** merged to `main` 2026-09-06 (fast-forward) and pushed (`origin/main` = `8105427`); the comprehension check was skipped at the owner's direction.
- **Done:** all fifteen tasks of the September parity round landed on branch `feat/parity-2026-09`
  (the commits listed above, `585a125` through this one), each TDD'd and reviewed clean. `docs/gsd-android-spec.md`
  is amended and is the spec of record again; `README.md`'s status paragraph, this file, and
  `tasks/lessons.md` are updated. The four-gate sweep is green (table above). The pending AGP
  `9.3.0 → 9.3.2` bump is committed (`e2240cb`) — it is the version every gate ran under.
  **The branch is not merged and not pushed.**
- **Next (owner decisions, in order):**
  1. Merge `feat/parity-2026-09` into `main` (or open a PR — nothing has been pushed yet).
  2. ✅ **Mid-window closed-test release shipped 2026-09-06** as 0.9.0 / versionCode 9
     (bump `64e1cd9`) — see the Play block below.
  3. ✅ Release smoke done on the emulator (minified 0.9.0: capture → complete → dashboard);
     sync + reminder on a signed build still unwalked — do it from the Play install on the Pixel.
- **Blockers:** none.
- **Assumptions:** the declared absences in spec S§1.2 (listed above) stay absent by decision —
  a future parity audit must not flag them as gaps. `tasks/implementation-notes.md` was not used
  this round; it is the earlier phases' ledger (last written in Phase 5) and was left untouched.

## Resuming From Here (2026-09-06, Play closed test)
- **Where the release is:** 0.8.0 (versionCode 8) is live on the Closed testing – Alpha track
  since 2026-08-29 5:33 PM, one country/region, 9,295 supported devices. Production is
  Inactive. There is no Google review pending — the closed-test tester gate is the blocker.
- **Blocker:** the Dashboard's "Apply for production" card shows 0 of 12 testers opted in.
  Google Group membership (`gsd-android-testers@googlegroups.com`, 4 members) does not count;
  each tester must tap "Become a tester" at
  <https://play.google.com/apps/testing/dev.vinny.gsd>, then install from Play. The 14-day
  clock starts only once 12 accounts are opted in; recruit to ~15 to absorb churn.
- **Done 2026-09-06:** tester call sent to the group from vscarpenter@gmail.com; group welcome
  message carries both links; recruiting post on r/AndroidClosedTesting (reciprocal — test
  the other developers' apps back and stay opted in); PR #1 (Claude Code GitHub workflows)
  merged; `CLAUDE_CODE_OAUTH_TOKEN` secret confirmed present.
- **Done 2026-09-06 (afternoon) — 0.9.0 to Alpha:** version bumped to 0.9.0 / versionCode 9
  (`64e1cd9`), `testDebugUnitTest` 120 green, signed `.aab` + `.apk` built and aapt2/jarsigner
  verified, minified build smoke-tested on the `gsd-test` emulator, bundle uploaded to Closed
  testing – Alpha as release 9 (0.9.0) with `<en-US>` notes, saved, and **sent for review at
  3:04 PM CDT** (submission #3, Submission activity: "In review"). Until Google publishes it, testers
  still get 8 (0.8.0). Details: `docs/play/submission-checklist.md` § 0.9.0.
- **Next:** check Publishing overview for the 0.9.0 review outcome (a closed-test update is
  usually quick); then watch the Dashboard opted-in count daily; record the date it reaches 12; on
  day 14 submit the production questionnaire (how testers were recruited, how they used the
  app, feedback received, changes made — keep the Reddit thread and group replies as
  evidence; a mid-window closed-test release with a small fix is a strong answer). After
  Google approves production access, promote the Alpha release to Production.
- **Still open from 2026-08-29:** release smoke on a device (item 6 above) was not recorded as
  done. (The AGP `9.3.0 → 9.3.2` bump is **resolved** — committed as `e2240cb` on
  `feat/parity-2026-09`, 2026-09-06; `main` still reads 9.3.0 until that branch merges.)
- **Unrelated, same developer account:** Travel Times Milwaukee (`com.vinny.traveltimes`)
  missed its 2026-08-31 target-API-level deadline; updates to that app are blocked until its
  target SDK is raised. Does not affect GSD.

## Resuming From Here (2026-08-29, post-parity)
- **Done:** all twelve parity-plan tasks landed and verified (commits `f64b325..HEAD`, listed
  above); `docs/gsd-android-spec.md` amended and is the spec of record again; `README.md`
  status paragraph updated. Full verification sweep run and green — see the Task 12 report
  (`.superpowers/sdd/parity-plan/task-12-report.md`) for the exact commands and results,
  including `:app:assembleRelease`: the owner's `keystore.properties` is present, and the
  build produced a validly signed release APK (`apksigner verify` passes; signed 2026-08-29
  16:22). No signing gap.
- **Also done (post-fold-in):** the final whole-branch review's two Important fixes landed —
  import now sweeps tombstone stores for ids that became active (`7961100`) and delete
  tolerates a vanished row (`22ff674`), both with red-confirmed regression tests; version
  0.8.0/versionCode 8 committed (`112a099`); signed `.aab` + `.apk` built from that tree.
- **Next:** release smoke on a device (sideload the APK), then the Play Console owner steps
  in `docs/play/submission-checklist.md` (upload the 0.8.0 `.aab` to internal testing).
- **Blockers:** none. One loose end: an uncommitted one-line AGP bump `9.3.0 → 9.3.2` in
  `gradle/libs.versions.toml` appeared during the 2026-08-29 session (provenance unclear);
  every green gate and both release artifacts were built under 9.3.2 — commit it (or revert
  and rebuild) before the next release build for reproducibility.
- **Post-parity backlog (from the final review):** extract `SyncCoordinator.applyRealtime`'s
  ApplyUpsert body into `:core:sync` for a JVM behavioral test of the tombstone-guard wiring;
  land an Android-produced backup fixture in both sibling repos' fixture directories (today no
  sibling imports an Android export — the last one-way mirror); `<plurals>` for
  `trash_days_left` ("1 days left"); align amended spec §8.17's Feedback placement with the
  actual Settings layout; a cap test admitting exactly 10,000; owner copy pass on roadmap
  labels ("Apple Calendar or Google Calendar" reads oddly on Android).
- **Assumptions (all labeled in the spec):** restore preserves timestamps; singletons
  replace-only; smart-view `description` is a documented round-trip loss.

## Earlier — ALL EIGHT PHASES COMPLETE
The spec's roadmap is fully implemented. Remaining work is in `docs/play/submission-checklist.md`:
all owner steps (deletion URL, signing, release smoke, Console upload, screenshots) plus the
post-1.0 deferral list. TalkBack with-ears pass is documented in the accessibility audit.
- **Phase 5 live-verified on a Pixel 10a (2026-07-19):** Google sign-in through the Custom Tab →
  bounce → callback → exchange path works, and push/pull round-trips against the live backend.
  Two live-only bugs were found and fixed in the process: the missing INTERNET permission
  (hidden by a swallowed exception) and the wire push omitting `owner` / carrying `id:""` in
  PATCH bodies (server 400s read from the queue's lastError). Apple/GitHub sign-in and a
  two-device round-trip remain untested but share the verified path.
- **Phase 5 deferrals:** onboarding's final-page sign-in buttons (Settings is the sign-in surface
  for now); expanded-width palette dialog styling; Chrome JS-redirect user-gesture caveat on the
  GET bounce path (fix is server-side 302 if it bites).
- **Play Console (2026-07-19):** app created and every app-content declaration saved, including
  Data Safety (deletion URLs filed as the privacy page's deletion section); category, contact
  details, and listing text in as a draft. Nothing sent for Google review yet. Remaining owner
  items: listing graphics, upload keystore + `keystore.properties`, `:app:bundleRelease`,
  release smoke, internal-testing upload — see `docs/play/submission-checklist.md`.
