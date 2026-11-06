# GSD Android — progress

## Resuming From Here (2026-11-06, evening): everything merged, Dependabot config fixed

- **Done, merged to `main`:** #62 (`da26d48`), #63 (`d2990c1`), and #64 (`03f81c0`). #64 needed a merge from
  `main` first (`f9d6107`): it was cut from #61's head, so `main` brought #59's reconcile tests into the same
  spots in `SyncEngineTest` where the branch added the resurrect tests. Both sides were additions and both were
  kept. Gates on the merged tree, on `gsd-e2e`: `:core:model:test` 218, `:core:sync` 172,
  `:app:testDebugUnitTest` 98, `:core:store` connected 83, `:app` connected 267, 0 failures.
- **This branch:** `.github/dependabot.yml` was the GitHub template with an empty `package-ecosystem`, so
  Dependabot never ran and its check failed on every push that carried the file (first on `01b7250`, then on
  #64's merge commit). It now names `gradle` at `/`, weekly. Workflow actions stay pinned by SHA by hand; a
  `github-actions` entry would let Dependabot bump those too, and was left out.
- **Next, owner-side:** Pixel release smoke on the signed 0.10.2, paste the Play listing from #54 into the
  Console, and apply for production access (`docs/play/production-access-round-3.md`). The `/review` comment on
  #64 drew no reply from Qodo, so its installation most likely does not cover this repo yet.
- **Parity backlog:** `WEB-RESURRECT` and `IOS-RESURRECT` stand (see the afternoon section below).
- **Not verified:** nothing ran on a device against the live server. Dependabot's own check on this branch's
  push is the proof for the config.
- **Blockers:** none on this repo.

## Resuming From Here (2026-11-06, afternoon): resurrect on sight and the remote delete recheck

- **Done, pushed, PRs open:** #62 and #64, with this record as #63.
  - #62 `fix/remote-delete-recheck` (`0db6a73`, from `main`): `GsdDatabase.deleteFromRemote` is a transaction that
    skips when a pending row exists, as `abandonToTrash` does since #59. The realtime delete shares it. Spec 9.4.
  - #64 `feat/resurrect-on-sight` (`6858270`, cut from #61's head, which merged at 19:21 UTC): sign-in remembers
    the ids of the dropped Trash and Replace deletes in `sync_metadata.resurrectTaskIdsJson` (schema 4, migration
    3 to 4). Right after the pull, a run with remembered ids lists the account once, confirms the owner, removes
    each found id's tombstone, and writes the server copy under last-write-wins; then it forgets every id. No
    server copy means the Trash row stays. An archive's delete carries a payload and is not remembered, so the
    archive stands, as on the web. Spec 8.21, 9.4, and the tombstone rule amended.
- **Gates:** `:core:model:test` 218, `:app:testDebugUnitTest` 98, and `:app:assembleDebug` green on both branches, on
  `gsd-e2e`. Resurrect: `:core:sync` 165, whole `:core:store` connected 79, whole `:app` connected 267, 0 failures.
  Delete recheck: `:core:sync` 167, whole `:core:store` connected 82, 0 failures; its full `:app` connected suite
  was still running when this was written, and its PR carries the result.
- **Parity backlog, new:** `WEB-RESURRECT` and `IOS-RESURRECT`. Android alone brings back a task deleted while
  signed out once the account lists it. The web and iOS keep the shared tombstone rule (web
  `isRemoteNewerThanArchive`, iOS `TombstoneRepository`), so there the task stays in Trash while the server copy
  survives. The owner's rule on 2026-11-06: a task deleted while signed out should reappear when synced.
- **Merge order:** none left to respect. #61 merged first (`4b35e3e`), and #62, #63, and #64 each target `main`
  and merge in any order.
- **Not verified:** nothing ran on a device against the live server. The SignedOutDeletesTest fake lists only
  records updated after the cursor, as PocketBase does, which is what made the kept-cursor case testable.
- **Blockers:** none on this repo.

## Resuming From Here (2026-11-06): merges, round 3 doc, four sync fixes

- **Done, merged:** #55 `fix/cuba-dst-corpus` (the Havana due-time cases moved to past transitions) and #56
  `feat/whats-new` (release notes, the About screen, and the post-update line), each gated on the Mac with
  `:core:model:test`, `:app:testDebugUnitTest`, and `:app:assembleDebug`; `AboutTest` passed 3 of 3 on `gsd-e2e`.
- **Done.** The owner merged #57 to #60 on 2026-11-06, and #61 is open with every gate green. Four branched from
  `main` at `fe3b3a0`; B-55 branched from `d69879c` (before #56 merged). All five are independent.
  - #57 `docs/round-3-answers-0.10.2` (`f82b2ee`): `docs/play/production-access-round-3.md` says 0.10.2 everywhere
    the answers, the checked table, the checklist, and the feedback log said 0.10.1.
  - #58 `fix/pull-moved-due-resets-reminder` (`c4682aa`, B-55): a pull that moves the due date resets
    `notificationSent`, `lastNotificationAt`, and `snoozedUntil`, as web #669 does. Spec 4.7 and 9.2.
  - #59 `fix/reconcile-recheck` (`21f3835`, RECONCILE-RECHECK): reconciliation re-reads the pending queue after
    `confirmIdentity`, and `abandonToTrash` rechecks inside its transaction. Spec 9.4.
  - #60 `fix/realtime-session-fence` (`a1a763f`, AND-RT-FENCE): `applyRealtimeEvent` in `:core:sync` checks the
    session before each realtime write. Spec 9.4 and 9.6.
  - #61 `fix/signed-out-deletes` (`f1bcc3d` and `68d4685`, B-56): `confirmOwner` drops the queue's `DELETE` rows, so a Replace
    import made while signed out deletes nothing on the server after the next sign-in. Spec 8.21.
- **Gates per branch:** `:core:model:test`, `:app:testDebugUnitTest`, and `:app:assembleDebug` green on all five.
  The full `:app` connected suite on `gsd-e2e` ran for #61 (264 tests) and #60 (263 tests), 0 failures. #59 ran
  the whole `:core:store` connected suite, 0 failures. #58 and #57 ran the JVM suites only.
- **Next, for the owner:** merge #61. Qodo Merge was installed on 2026-11-06 and has acted on no PR in any of the
  three repos yet; check the installation's repository access, then comment `/review` on #61 to test it. Then
  the release smoke on the Pixel from the signed 0.10.2 build (sign in, sync, a firing reminder), paste the listing from #54 into
  the Console, and apply for production access. The round 3 doc's "Before you click Apply" list still applies.
- **Assumptions:** B-56 drops deletes at sign-in rather than clearing the queue at sign-out (the commit says
  why). `gsd-e2e` is the test AVD now that `gsd-test` is gone.
- **Not verified:** no device run of a realtime sign-out race, and B-55 was not watched on a device. One
  observation, not fixed: a sync run in flight across a sign-out and a same-account sign-in can list as a guest
  and end with `notSignedIn`, which signs the user out again. The window is milliseconds against an OAuth flow.
- **Blockers:** none on this repo.

## Resuming From Here (2026-11-05): What's New (`feat/whats-new`)

- **Done (not compiled here):** `:core:model` `WhatsNew.kt` with the bundled `whats-new.json` (md5 f0e74309) and the 21-case `whats-new-corpus.json` (md5 7a8d4ac6), both byte-identical to gsd-taskmanager. `WhatsNewCorpusTest`, `WhatsNewTest`. App: Settings > About row, palette action, `whats-new` route, and the matrix line under the capture bar. Last seen is DataStore `whatsNewLastSeenVersion`, excluded from cloud backup.
- **Next:** run `./gradlew :core:model:test :app:testDebugUnitTest :app:assembleDebug`, then `AboutTest` on the emulator. The cloud session couldn't reach Maven or Gradle, so nothing here has compiled yet.
- **Before release:** add the 0.11.0 (or next) entry to `whats-new.json` in gsd-taskmanager first, then copy it here byte-identical.

## Resuming From Here (2026-11-02): 0.10.2 live on Alpha

- **Done:** 13 (0.10.2) published to Closed testing, Alpha, on Nov 2 at 11:52 AM CST. It carries the 0.10.1 work, which never reached the Console. Gates and artifacts are in the local `docs/play/submission-checklist.md`.
- **Next:** do section A's release smoke on the Pixel. Sign-in, sync, and a firing reminder are still unwalked on a signed build.
- **Before applying for production:** the Console shows all three requirements met, and nobody has applied. Read the 0.10.2 note in `docs/play/production-access-round-3.md` first, because the draft answers still say 0.10.1.

## Resuming From Here (2026-11-02): public repo hardening

- **Done:** private vulnerability reporting, Dependabot security updates, and two `main` rulesets are live, and the wiki is off.
  PR #49 added the MIT license and the `author_association` check in `claude.yml`, and stopped tracking the Play checklist.
  The checklist is local-only now, so a fresh clone can recover it with
  `git show d6a77c1~1:docs/play/submission-checklist.md > docs/play/submission-checklist.md`.
- **Also done:** PR #50 added `SECURITY.md`, and PR #51 pinned the workflow actions to SHAs.
- **Assumptions:** PRs need 0 approvals because a solo owner can't approve their own. Admins can bypass the PR rule, but no one
  can force-push or delete `main`.

## Resuming From Here (2026-10-31): sprint 1, E-AND lane (escalation, reminders, tag analytics)

- **Done, all merged:** AND-ESC-1 (#35, the rule and its 38-case corpus test), AND-ESC-2 (#38, the setting,
  card tag, Mark urgent in the menu, swipe, and TalkBack, and the editor hint), AND-36 (#41, the null reminder
  row reads "Default reminder"), AND-27 (#43, top 8 tags with completion rate), and AND-REM (#44, all-day
  reminders fire at 09:00 on the due day, with all 117 due-time corpus cases running).
- **Next:** B-29, a quieter top bar on scroll. It is parked with Vinny (`sprint/questions/E-AND-01-b29-quieter-top-bar.md`)
  because the only definition is one line. If he says "recede", that file has the option 2 write-up.
- **Assumptions:** an all-day offset moves back one day per whole 1440 minutes and ignores the remainder
  (the Lead's reading, pinned by the corpus). The all-day hour is `ReminderPlanner.ALL_DAY_REMINDER_HOUR`, one
  line to change. Top tags sort by count then name, as iOS does; web sorts by count only and shows 10.
- **Not verified:** no all-day alarm was watched firing on a device, and the top-tag rows were not checked at 2x
  font scale. D-10 is still open, so a task with no reminder offset shows "Default reminder" here and "At time"
  on other devices once it syncs.
- **Blockers:** none.

## Resuming From Here (2026-10-31): sprint 1, S-AND lane, 0.10.1 ready to upload

- **Done and merged:** Saved parity (#34), reconcile read order (#37), far-future clamp (#39), sync history totals (#40), the tombstone fence (#42), and pull to refresh (#45, #47).
- **Next, for the owner:** merge `release/0.10.1`, upload `gsd-0.10.1-versionCode12.aab` to Alpha, then do section A's release smoke on a real device. Details in `docs/play/submission-checklist.md`.
- **Before applying for production:** the round 3 draft answers were corrected against this build. 0.10.1 carries parity and sync work, not tester reports, and the feedback log is empty. See `docs/play/production-access-round-3.md`, `0.10.1 release`.
- **Assumption, owner-confirmed 2026-10-31:** `api.vinny.io` has the `saved_at` field.
- **Backlog, sprint 2:** `RECONCILE-RECHECK`, `AND-RT-FENCE`, and always-send-a-number for `notify_before` (`~/Projects/GSD/sprint/findings/D-10.md`).
- **Blockers:** none on this repo.

## Resuming from here (2026-10-13): production access, round 3 (`release/0.10.0`)

Google declined production access on 2026-09-21 and again on 2026-10-09: "testers were not engaged" and
"you didn't follow testing best practices, which may include gathering and acting on user feedback through
updates." Diagnosis, dates, tester email, and draft answers: `docs/play/production-access-round-3.md`.

- [x] Cut `release/0.10.0` from `main` at `e4f1d8d`: versionCode 11, versionName 0.10.0 (`4813ff5`).
- [x] Gates on the Mac, signed bundle, upload 0.10.0 to Alpha with the notes in the round 3 doc. Submitted 2026-10-15 8:00 AM CDT from `d2ffa88`; see `docs/play/submission-checklist.md`.
- [x] Alpha track: add the countries testers live in. Checked 2026-10-15: the track already targets 178 countries/regions.
- [ ] Send the tester email and recruit 6 or more real testers. 12 are opted in, so one opt-out resets the clock.
- [ ] Ship 0.10.1 (versionCode 12) around 2026-10-20 with fixes for tester reports, logged in the round 3 doc.
- [x] Privacy page: Android, the feedback form, the backup rule, and account deletion, on `gsdtaskmanager.com`
      branch `fix/privacy-android-feedback` (`099eaf9`). Merge and deploy it before applying.
- [ ] Apply on 2026-10-23 or 2026-10-24, once the Console enables the button. Save the answers first.

## Resuming from here (2026-10-13): archive after a pull (`fix/archive-after-pull`)

Parity backlog B-46 (Android half) and B-47, the web's PR #603 rules. Why: `docs/2026-10-13-archive-after-pull.md`.

- [x] **B-47.** An archive's delete now carries the archived task, and the push skips it when the remote is
      newer than that version (`SyncEngine.remoteEditedAfterArchive`). Trash deletes are unchanged (B-42).
- [x] **B-46.** Signed in, `MaintenanceWorker` syncs instead of sweeping, and the sweep runs from
      `SyncCoordinator.afterCleanSync` after every clean run (`SyncResult.clean`). "Signed in" comes from the
      saved session, since the coordinator may still be starting when WorkManager launches the process.
- [x] Spec §8.12, §9.5, §11.5 amended. An independent review found no compile errors; its fixes are in.
- [x] **Codex on PR #32:** archiving now replaces the task's older queue rows, since the drain skips a row in
      backoff and an older update could recreate the record after the delete. New test
      `ArchiveStoreTest.archiveReplacesTheTasksOlderQueueRows`. Needs one more
      `./gradlew :core:store:connectedAndroidTest` on `gsd-test` before the push.
- [x] **Gates, run by the owner on the Mac** (the cloud workspace can't reach Maven or Gradle):
      `:core:sync:test` and `:app:testDebugUnitTest` pass, and on `gsd-test`, `connectedAndroidTest` passes
      68 store tests and 231 app tests with none failed.

## Left to do (as of 2026-09-24)

Every numbered finding in `docs/play/2026-09-20-polish-review.md` that code can fix is now fixed, along with
the copy and accessibility sections, the follow-ups, "Worth a look", and the backlog. The work is on
`claude/festive-pascal-b4c4mb`, PR #29, merged 2026-09-24 before its first build. Later that day, main
failed to compile. `5ac9e59` and `aaa73fa` on `fix/compile-after-29` fix the build and four tests, and every
gate is green there, including `connectedAndroidTest` on `gsd-test` (226 app tests, 65 store tests).

What is still open: the path to production (section A), the TalkBack pass on a device (H), the owner and
device items in K and L, the sibling repos' items (M), and the follow-ups this pass turned up (N). A number
in front of an item is its finding number in the review, which holds the evidence for each one.

### A. The path to production

- [x] **Closed-test gate.** Done. 12 testers stayed opted in for 14 days, and the owner applied for production
      access on 2026-09-20 at 8:08 PM. The Console says "We have your application for production access" and
      that a review usually takes 7 days or less. Production is still Inactive.
- [x] **Wait for Google's answer.** It came 2026-09-21 at 6:06 PM: "More testing required." A second
      application was declined the same way on 2026-10-09. See `docs/play/production-access-round-3.md`.
- [x] **18. Pick the production version.** Decided 2026-09-21: this build is 0.9.1, and 1.0.0 waits for the
      build that goes to Production.
- [x] **Cut a new build.** Done 2026-09-21: 0.9.1 (versionCode 10), bump `3c62197` on `release/0.9.1`. Every
      gate green, signed `.aab` and `.apk` verified, smoked on the minified build. Details and the release
      notes in `docs/play/submission-checklist.md`.
- [ ] **Release smoke on a real device, from a signed build.** Walk sync, a reminder, the widget, and a deep
      link. This has been open since 2026-08-29. Add one real sign-in: the callback should close the Custom Tab
      and land on the screen that started it (finding 4). During that sign-in, leave the tab and come back
      two ways. Through Recents the tab should still be there. Through the GSD icon it will be gone, which is
      the accepted cost. If Recents loses it too, move the launcher activity fix up (see "Decided").
- [x] **Upload the new build to Closed testing.** Done 2026-09-21 at 8:35 PM CDT, driven through Chrome:
      release 10 (0.9.1) on the Alpha track, sent for review on Save because managed publishing is off.
      Google published it at 9:08 PM the same night, so testers have it.
- [ ] **Promote to Production** once Google grants access: 0.10.1, or a 1.0.0 that testers have run first.

### B. Broken behavior (fix before production)

- [x] **4. Make `MainActivity` a single instance.** Done 2026-09-20, merged in #13.
      `MainActivity` is `singleTask`, so links reach the one instance through `onNewIntent`. The S4 regression
      went with it: a `gsd://capture` link now opens the sheet on the visible activity.
- [x] **1.** The "..." button on every task card does nothing. Done 2026-09-20, merged in #14. The button
      opens the long-press menu. A card with no menu shows no button.
- [x] **2.** Trash rows draw over the red swipe background, so their text is unreadable. Done 2026-09-20,
      merged in #15. A row at rest no longer paints a swipe background.
- [x] **3.** Swipe-to-delete never shows the Undo snackbar. Done 2026-09-20, merged in #16. One swipe ran its
      action 8 or 9 times. The actions now run once, in `onDismiss`.
- [x] **48.** The task menu is mostly dead outside Matrix. Done 2026-09-20, merged in #18. The owner chose to
      wire all eight actions. One `TaskActionsViewModel` owns the
      row operations for Matrix, Browse, and the smart view list. Select mode has no menu, and Trash and
      Archive cards draw no disc.
- [x] **49.** Browse search results and smart view lists have no swipe gestures. Done 2026-09-21 on branch
      `fix/swipes-on-every-list`. Both lists wrap each row in `SwipeTaskRow`, and the swipe calls the same
      callbacks as the row's menu.
- [x] **50.** Completing a task outside Matrix gets no haptic and no confetti. Done 2026-09-21 on branch
      `fix/celebrate-every-completion`. `CompletionCelebration` owns both, and all three list screens draw
      the confetti.
- [x] **51.** Reopening a task gives no haptic. Spec 5.8 says complete and uncomplete. Done 2026-09-21 on the
      same branch, in its own commit. iOS fires the haptic on every toggle.
- [x] **52.** Bulk Done gives no haptic and no confetti. Closed 2026-09-21 by a spec correction: iOS's bulk
      bar celebrates nothing and the web has no bulk bar, so the spec line was the outlier. No code change.
- [x] **5.** "Snooze 1 hour" on a reminder never fires again. Done 2026-09-20, merged in #20. A future snooze
      now overrides the sent flag in `ReminderPlanner`.
- [x] **44.** The editor saves a whole-row snapshot taken when it opened. Done 2026-09-20, merged in #22. Save
      merges the fields the user changed onto the row as it is now.
- [x] **45.** `OneShotRequest` can wedge if a handler ever suspends. Done 2026-09-20, merged in #24. Each send
      is its own object now, so two equal values stay two requests. I fixed the cause and did not take the
      note's fix. A handler may suspend safely.
- [x] **46.** A launcher shortcut that fires while GSD is on screen loses its link and lands on Matrix. Done
      2026-09-21 on branch `fix/shortcut-while-running`. The route collector and the request consumer run
      only while RESUMED, so the paused copy takes nothing.
- [x] **47.** A reminder tap opens nothing unless Matrix is on screen. Done 2026-09-20, merged in #21. The tap
      is now the `gsd://task/<id>` link, as spec 11.2 says.

### Notes for finding 46 (kept as the record of how it was found)

- **Proved on 2026-09-21 by `ShortcutWhileRunningTest`,** as the first note below guessed.
- **The cause, probable and not proved.** A static shortcut carries `NEW_TASK`, `CLEAR_TASK`, and `TASK_ON_HOME`
  (`dumpsys shortcut`, `flg=0x1000c000`). The system destroys the old `MainActivity` and starts a new one. A
  resumed activity must pause before it dies, so for a moment both copies exist. `PaletteController` lives in the
  process-wide `AppContainer`, and the route collector in `MainActivity` has no lifecycle gate. The dying copy
  takes `pendingRoute` first. A stopped activity is destroyed at once, which is why the home screen path works.
- **Reproduce it by hand.** With GSD on screen:
  `adb shell am start -n dev.vinny.gsd/.MainActivity -a android.intent.action.VIEW -d "gsd://smart-view/today-focus" -f 0x1000c000`.
  The app restarts on Matrix. Press Home first and the same command opens Today's Focus.
- **Two designs left over from finding 4.** Scope `PaletteController` to the activity, as a ViewModel. Or gate
  the router and `ConsumeWhileStarted` on RESUMED. Finding 4 needed neither, because `singleTask` leaves one
  living copy. `CLEAR_TASK` still makes a dying copy and a fresh one.
- **The test is the hard part.** I could not get a deterministic failing test for the RESUMED gate during S4.
  The compose test rules queue effects, which hides this race. `RunningAppLinkTest` shows how to count live
  activities, and an intent with the shortcut's flags may give a red test here.

### C. Reminders must match the listing

- [x] **6.** The editor has no date or time picker. Every due date lands at midnight.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `e7e23cc`: a date picker, a time picker, and an All day switch.
- [x] **7.** The app never asks for the notification permission in the normal flow.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `e7e23cc`: the first due date with a reminder asks, once.
- [x] **8.** Exact alarms are denied by default on Android 14 and later, with no in-app way to grant them. The
      listing promises exact timing.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `4102ab0`: an Allow exact alarms row that opens the system page.

### D. Play policy and listing accuracy

- [x] **15.** No privacy policy link inside the app.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `669fc77`: About links the policy.
- [x] **16.** Auto Backup is on while Settings says tasks never leave the device. Exclude cloud backup, or change
      the copy. Owner decision.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `8e12b5e`: the owner chose to exclude cloud backup; device-to-device transfer keeps no token or device id.
- [x] **17.** `docs/play/data-safety.md` predates the feedback form. The privacy page never mentions Android,
      feedback, or backups. Check the Console form against both.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `701503a`: the answers are current. The privacy page and the Console form are still the owner's; see section L.

### E. Layout, theming, and data handling

- [x] **9.** Share sheet: title under the status bar, a blank grey page over the sharing app, `EXTRA_SUBJECT`
      ignored, and no scroll container.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `2ac911e`: a scrolling bottom sheet over the sharing app that reads EXTRA_SUBJECT.
- [x] **10.** The text caret is invisible in dark mode in all 17 text fields.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `4d36835`: the caret is Tide in every field, and a source test holds it.
- [x] **11.** Landscape and tablets draw content under the status bar. Only the phone layout applies insets.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `dbc030c`: every layout applies safe-drawing insets.
- [x] **12.** Editor chip rows do not wrap at large font scales.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `024e391`: every chip row is a FlowRow.
- [x] **13.** Select mode shows no selected state on any card.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `767d0fd`: a selected card has a check mark and a tint border, and reads as a checkbox.
- [x] **14.** The tablet 2x2 grid has no card menu at all. Since finding 1 its cards show no "..." button
      either. Wrapping them in `TaskRowWithMenu` brings back both.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `03fee2c`: the grid has the menu; its long press drags.
- [x] **37.** No paper background outside the phone layout, so text turns unreadable when the in-app theme
      differs from the system theme.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `dbc030c`: every layout sits on paper.
- [x] **38.** Status bar icons follow the system theme, not the in-app theme.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `dbc030c`: they follow the in-app theme.
- [x] **39.** Export has no error handling and may not truncate. Import parses on the main thread and can stop
      halfway.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `41032e0`: export opens with `wt` and reports a failure; import parses off the main thread.
- [x] **40.** Account deletion, erase, and import run in the composition scope, so a rotation cancels them
      midway. The account-switch buttons and the OAuth callback have the same weakness (review of #5).
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `41032e0`: they run in the app's scope and report back once.
- [x] **41.** "Fetch titles for shared links" is a dead setting. Build the fetch, or remove the setting.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `2ac911e`: the owner chose to build the fetch, as iOS does.
- [x] **42.** A restored or transferred install shares its `deviceId` with the old phone.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `8e12b5e`: a moved install renews its device id.

### F. High-impact polish

- [x] **19.** Onboarding is a stub against spec 8.19.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `e46fbb4`: a four-page pager with the mark, the axes, the lock, a page indicator, and sign-in on the last page.
- [x] **20.** The Field Guide is one paragraph with no scroll, and it still says sync is coming. About lacks the
      mark, tagline, site link, and credit.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `e46fbb4`: a sectioned Field Guide and a full About.
- [x] **21.** Only Matrix has a top bar. Pushed screens have no title and no Up arrow, and the bottom bar loses
      its selected state on child routes.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `bc2b686`: every screen has the lockup bar, pushed screens have Up, and the tab stays selected.
- [x] **22.** The Browse search field has no container, icon, or clear button.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `8614139`: one shared search field, 48dp tall.
- [x] **23.** Material's baseline purple shows in the selected navigation label (`secondary` is unmapped).
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `6b3b49b`: every Material role maps to a GSD token.
- [x] **24.** `MaterialTheme` gets no `shapes` or `typography`, so chips, menus, dialogs, and snackbars fall back
      to Material defaults.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `6b3b49b`: MaterialTheme takes the spec's shapes and type.
- [x] **25.** Save is a quiet text button at the end of a long scroll. Swiping the sheet down discards edits with
      no prompt.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `f489b8d`: Save sits in a pinned header, and closing with edits asks first.
- [x] **26.** The command palette opens scrolled to the bottom of its list. Recheck the focus claim: on
      2026-09-20 the soft keyboard did come up when the palette opened in tests.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `b58d92c`: it opens at the top with the query focused.
- [x] **27.** Reminder notifications use a stock system icon, with no color and no category.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `ccb1a79`: the brand icon, Tide, and the reminder category.
- [x] **28.** Both launcher shortcuts show blank circles. The widget picker shows a generic preview.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `a5564bf`: shortcut icons, a real widget preview and label, and 36dp rows.
- [x] **29.** Settings: cramped radio rows, a check mark typed as text, fixed 24-hour quiet hours, and rows
      under 48 dp.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `b1701ae`: 48dp rows, real selected states, and adjustable quiet hours in the user's clock.
- [x] **30.** Editor details: subtask toggles with no role or state, a bare "90" estimate with no unit, and due
      chips with no selected state.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `bf437c6`: input chips, checkbox subtasks, and an estimate with a unit and a number pad.
- [x] **31.** The long-press menu gives Delete no destructive color. The delete-forever dialog has no title.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `78702ba`: Delete in alert, submenu cues, and delete dialogs that name the task.
- [x] **32.** Five surfaces use a bare line of text as their empty state.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `1f85ac8`: the shared empty state everywhere.
- [x] **33.** No list animates, so a completed task jumps to its new position.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `c5a67f0`: rows slide to their new place, gated by the animator setting.
- [x] **34.** The accent spine is a fixed 40 dp tall and covers only the top of a tall card.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `6c08093`: the spine runs the card's full height.
- [x] **35.** Card titles clamp to two lines at every font scale, against spec 5.4.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `6c08093`: the cap lifts from font scale 1.5.
- [x] **36.** A wide phone in landscape gets the tablet drawer, and its labels break mid-word beside a cutout.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `dbc030c`: the drawer needs 480dp of height, and labels stay on one line.

### G. Copy

- [x] 13 em dashes in user-visible strings.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `4e37c4e`: none left; spec 5.11 sets the copy rules.
- [x] Title case and sentence case mixed on one screen ("Sync Now" beside "Export tasks").
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `4e37c4e`: sentence case everywhere; names keep their capitals.
- [x] Two names for one action: "Complete" and "Done", "Delete permanently" and "Delete forever".
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `4e37c4e`: Complete, and Delete permanently.
- [x] Hardcoded English in Kotlin: due labels, palette labels, and option labels.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `4e37c4e`: all from resources, validation messages included.
- [x] Two places show a raw exception message to the user.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `4e37c4e`: no surface shows one.
- [x] Plurals: "1 days left" and "Delete 1 tasks?".
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `4e37c4e`: plurals resources.
- [x] Four dead strings still describe shipped features as future work.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `4e37c4e`: deleted.
- [x] "Completion by Quadrant" beside "Time by quadrant". "Today's Focus" uses a mouse pointer icon.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `4e37c4e`: sentence case, and the crosshair icon.
- [x] Straight quotes in most strings and curly quotes in one.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `4e37c4e`: straight everywhere.

### H. Accessibility

- [x] No text field has a TalkBack name, including the erase confirmation field.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `5c23b6d`: each hint sits inside its field; a source test holds it.
- [x] Zero `heading()`, `liveRegion`, `Role`, and `customActions` calls. Spec 13.3 promises custom actions.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `5c23b6d`: headings, live regions, roles, and card custom actions.
- [x] The quadrant picker and subtask toggles expose no state. Stat cards read the value before its label.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `5c23b6d`: a radio group, checkboxes, and one-node stat cards.
- [x] Contrast: `ink3` and ochre fall under 4.5:1 on several surfaces. The web already derives darker text inks.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `5c23b6d`: new light ink2, ink3, and success, quadrant text inks, and no row dim; `ContrastTest` holds 4.5:1. iOS still needs the text inks.
- [x] Correct `docs/play/accessibility-audit.md`. Its custom-actions and title line-cap claims do not match
      the code.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `5c23b6d`: corrected, and the code now matches both claims.
- [ ] Re-run the audit with TalkBack on a device once the semantics land.

### I. Follow-ups from the fixes merged on 2026-09-20

- [x] **S1.** A dead token more than 7 days from expiry fails every run with a 401, and the user stays signed
      in. Sign them out on that 401.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `aba60a2`: a 401 on that path signs the user out.
- [x] **S3.** A prompt rebuilt after a restart knows no email, so Settings shows none until the next sign-in.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `b9585cd`: the prompt keeps its email.
- [x] **S4.** Ctrl+N or Ctrl+K pressed off the Matrix tab waits for the next visit to Matrix. Decide whether the
      key should navigate there, or the request should expire.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `8efb77a`: the owner chose to go to Matrix and open; every shortcut goes through the router.
- [x] **S6.** `NotificationActionReceiver` has no catch. A database error there would crash the process from a
      notification button.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `b6134ac`: a failed action is logged and reported.
- [x] **After finding 43.** A screen pushed over Matrix is popped with `saveState` and never restored, so its
      view model lives until the activity ends. Small, and older than the fix.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `964af3c`: screens no tab restores are not saved.

### J. Worth a look (confirmed in code, not reproduced)

- [x] Alarm scheduling has no cap, and `appScope` has no exception handler.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `96a86f2`: the soonest 400 reminders, and a handler on `appScope`.
- [x] Recurrence adds days in UTC, so a daily series can read a day early after a daylight-saving change.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `71921a3`: recurrences advance in the device's zone.
- [x] No receiver listens for time zone changes, so quiet-hours deferrals go stale after travel.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `4102ab0`: the reschedule receiver takes time zone and clock changes.
- [x] The DataStore files have no corruption handler. Imported quiet hours go through `LocalTime.parse` unchecked.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `1edccbb`: every store resets a corrupted file, and quiet hours are checked on import and on read.
- [x] Every sync lists the whole remote collection twice.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `8cd4c0e`: a run with nothing queued lists it once.
- [x] The share target drops `EXTRA_TEXT` when it arrives as a `CharSequence`.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `2ac911e`: it reads a CharSequence.
- [x] A deep link on the very first launch can be lost behind onboarding. Seen live once.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `9cf5221`: the link waits for onboarding to end.
- [x] The Undo snackbar may sit under the keyboard on Browse search and in-view search. The app is edge to
      edge, and nothing uses `imePadding`. The reviewer of #18 raised it. `gsd-test` shows only the floating
      stylus toolbar, so it needs a device with a docked keyboard.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `b8992fb`: the snackbars use `imePadding`.
- [x] Older tests also write through the real store with no signed-out guard (`NotificationsTest` is one). On
      a signed-in device they would sync test tasks. The three classes from 2026-09-20 have the guard.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `d8348c4`: four NotificationsTest cases skip when signed in.
- [x] `TaskStore.change` still reads the row outside its transaction. A snooze or a timer write can then
      overwrite an editor save that committed a moment earlier. `edit` and `setCompleted` read inside. The
      reviewer of #22 raised it.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `84f2d2b`: it reads inside.
- [x] `TaskStore.edit` validates the merged row, so it now covers the stored fields too. A title over the limit
      that arrives by sync while the sheet is open would fail the save, and the sheet shows a legal title.
      Speculative, from the reviewer of #22.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `2a160ac`: it validates the editor's draft and normalizes the merged row.
- [x] Opening GSD from Recents after a reboot may run the last link again. `singleTask` makes the last link
      the task's base intent, and `handleIncomingIntent` never checks `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`.
      The reviewer of #13 raised it as speculative. Nobody has tried it.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `93fef15`: a launch from history skips the link.

### K. Older backlog, still open

- [x] Extract the upsert body of `SyncCoordinator.applyRealtime` into `:core:sync`, for a JVM test of the
      tombstone guard.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `0142abf`: with a JVM test of the tombstone guard.
- [ ] Land an Android-produced backup fixture in both sibling repos. No sibling imports an Android export today.
- [x] A cap test that admits exactly 10,000 imported tasks.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `d8348c4`: empty rows keep the model gate under a second.
- [x] Align spec 8.17's Feedback placement with the real Settings layout.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `13ed795`: Feedback has its own group, and the spec says so.
- [ ] Owner copy pass on the feedback roadmap labels.
- [x] Ctrl+1 to Ctrl+4 jump to quadrant is in spec 6.6 and is not wired.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `8efb77a`: wired as `gsd://quadrant/<id>`.
- [x] The two-button swipe reveal (Snooze and Delete) from spec 8.1 is not built.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `8665984`: built; a full trailing swipe snoozes, as on iOS.
- [x] `gsd://quadrant/<id>` opens Matrix and does not scroll to the quadrant.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `8efb77a`: it scrolls the phone's list to the header.
- [x] Onboarding's last page has no sign-in buttons.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `e46fbb4`: sign-in buttons that open sign-in, then finish onboarding.
- [ ] Apple and GitHub sign-in, and a two-device round trip, have never been tried live. Google sign-in has.
- [ ] Place the widget from a launcher for a visual pass.

### L. Owner decisions and housekeeping

- [x] Delete the nine merged branches, on origin and locally. Done 2026-09-20, after a check that each one
      was fully pushed and already contained in `origin/main`. The branch that carried this tick went too.
- [x] Origin no longer has `add-claude-github-actions-1788703830197`. `git branch -r` on 2026-09-20 showed only
      `main` and `fix/task-menu-everywhere`.
- [x] Origin no longer has `fix/task-menu-everywhere`. It was gone at a fetch late on 2026-09-20.
- [x] Local branch cleanup. Done 2026-09-21 on the owner's word: six merged local branches deleted after
      `git branch --merged main` listed each one.
- [x] `origin/docs/record-production-application` (#25) is still on origin. Deleting it waits for the owner's
      word; the ask was for local branches.
      Done 2026-09-24 on the owner's word, after a check that #25 merged and main held every commit.
- [x] Widen `--allowedTools` in `.github/workflows/claude-code-review.yml`. Four of seven reviews ended in 15
      seconds with a green check and no review.
      Done 2026-09-24 on `claude/festive-pascal-b4c4mb` (#29), `887d822`: the plugin may run the read-only gh commands it needs.
- [ ] Add a note to the workspace `CLAUDE.md` "Field limits" row. The numbers match, but iOS counts characters
      while the web and Android count UTF-16 units.
- [ ] Find out what deletes the `.img` files from the SDK system images. The `google_apis_playstore` image is
      still broken.
- [ ] `gsd-shot`: remove the leftover "Today's Focus" widget from its home screen.
- [x] Delete `/tmp/claude-s3-run.txt`, a stray file from the S3 session. Gone by 2026-09-24.
- [x] `app/build/outputs` holds a release APK built from a fix branch at versionCode 9. It is not the Play
      artifact. Moot by 2026-09-24: later builds replaced it.

### M. Not Android's to fix (upstream)

- [ ] Web: `resetEverything` leaves `deletedTasks` and `appPreferences` behind.
- [ ] Web: a trash-merge import overwrites the local row on an id collision.
- [ ] Web: a redundant completion still restamps `updatedAt` and `completedAt` (`lib/tasks/crud/toggle.ts`), so a
      tap that changes nothing can win last-write-wins. Found while fixing S5.
- [ ] iOS: no tombstone-suppression guard on pull or SSE, so archived and trashed ids can come back.
- [ ] iOS: the editor saves the whole row it opened (`App/Editor/TaskEditorView.swift`, `save()` starts from
      `original`). A completion made while the editor is open is undone. Android had the same bug (finding 44).
      The web merges its draft onto the stored row.
- [ ] iOS: Ochre and Slate text is under 4.5:1 in light mode. Android now uses the web's `--q3-ink #74591C` and
      `--q4-ink #615B54` for quadrant-colored text (spec 5.1, 2026-09-24).

### N. Found on 2026-09-24, while fixing the rest

- [x] Compile PR #29 and run every gate: `:core:model:test`, `testDebugUnitTest`, `connectedAndroidTest` on
      `gsd-test`, `:app:assembleDebug`, and `:app:assembleRelease`. Nothing on the branch was compiled, so expect
      small compile errors. Every API it uses was checked against the androidx sources, but not built.
      Done 2026-09-24 on `fix/compile-after-29`: `5ac9e59` fixes two compile errors in `:app` and four test files;
      `aaa73fa` fixes four instrumented tests that could not pass. Every gate green.
- [ ] Settings never shows the offline and stale-queue health messages that spec 9.7 lists. `Health.evaluate`
      in `:core:sync` has no caller, and `health_offline` and `health_stale` are unused strings.
- [ ] The Sync history screen lists runs but not the totals spec 8.17 names.
- [x] The capture bar drops a validation error silently: `MatrixScreen` passes `onError = {}`. An over-long
      title in the bar does nothing when submitted.
      Done 2026-09-24 on `fix/capture-validation-error`: the reason shows under the field and clears on the next edit.
- [ ] Trash's select button is a plain `IconButton`, so TalkBack hears no on or off state. Matrix uses an
      `IconToggleButton`.
- [ ] iOS should take the quadrant text inks from spec 5.1. Android now draws Ochre and Slate text in the web's
      darker inks, which supersedes the August "Android follows iOS" note. Listed in M as well.

### Decided, no action needed

- Text limits stay in UTF-16 units, as on the web. The editor asks the user to shorten an iOS emoji title
  before it saves.
- A snooze the user asks for still restamps and syncs, as on the web and iOS.
- Two published commit subjects run past 72 characters. Fixing them would rewrite published history.
- The declared absences in the parity spec stay absent.
- A tap on the GSD icon may close the sign-in tab. Decided by the owner on 2026-09-20. `singleTask` clears what
  sits above `MainActivity` on a launcher tap. A user who leaves mid sign-in and returns through the icon
  taps "Sign in" again. Three things must all hold for that: they sign in on Android, their provider sends
  them to another app, and they come back through the icon. The cost is one more tap, with no data lost.
  Giving the tab its own task was rejected: every sign-in would leave a stray tab in Recents, because the
  callback could no longer close it.
- If a tester ever reports it, the fix is a small launcher activity that holds the `LAUNCHER` filter. It
  finishes at once when it is not the task root. It moves the launcher filter, the splash theme, and the
  shortcut targets, so it waits until after the production launch.

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

## Resuming From Here (2026-09-24, every open finding fixed on PR #29, not yet compiled)
- **Done:** every finding code can fix, on `claude/festive-pascal-b4c4mb` (draft PR #29, one commit per fix,
  each with its spec amendment). Sections B to F, the Copy and Accessibility sections, the S follow-ups,
  "Worth a look", and the backlog. The ticks above name each commit. Tiers were Standard throughout.
- **Owner decisions this round:** exclude cloud backup (16), build the title fetch (41), and send Ctrl+N and
  Ctrl+K to Matrix (S4 follow-up). The build-access choice was to allow `dl.google.com`, which the container
  still refused.
- **A decision I made that the owner may reverse:** quadrant text now uses the web's darker Ochre and Slate
  inks, for AA. The August parity note had kept raw pigment on both native clients until an owner decision
  covered iOS too. Reverting is one function, `GsdColors.accentInk`.
- **Not compiled.** `dl.google.com` answered 403 all session, so no Android module built. What did run, green:
  `:core:model` 181 (0.7 s), `:core:sync` 102, and a standalone JVM build of the plain-Kotlin `:app` files with
  their tests, 55. Every Compose API was checked against the androidx sources for the BOM's version (Compose UI
  1.11): `hintText` semantics, for one, arrived in 1.13 and is not used.
- **Next, in this order:** build PR #29 and run every gate (section N, first item), then the TalkBack walkthrough
  (section H), then merge. Section A's release smoke fits after the merge.
- **Blockers:** the build is the one unknown. Expect small compile errors, since nothing was built.

## Resuming From Here (2026-09-22, 0.9.1 published to testers)
- **Done:** recorded the 0.9.1 publish in `docs/play/submission-checklist.md`, read from the Console through
  Chrome. Google published release 10 (0.9.1) on the Alpha track at 9:08 PM on 2026-09-21. The same read
  found a second tester group, the US-only target, an unrecorded 0.8.0 on Internal testing, and the source
  of the edge-to-edge warnings. Tier was Trivial, docs only.
- **Not in 0.9.1:** only finding 46 (`2f06185`). I suggested it ride along with section C instead of a 0.9.2
  of its own. The owner has not decided.
- **Open question for the owner:** section A says "Promote 0.9.1 to Production", but the checklist keeps
  1.0.0 for the build that goes to Production. Settle it before Google answers the application.
- **Next:** the real-device smoke in section A is unblocked. Then section C, as the block below says.
- **Blockers:** none. **Gates:** none run for this docs-only change.

## Resuming From Here (2026-09-21, finding 46 the resumed copy takes the route)
- **Done:** finding 46 is fixed on branch `fix/shortcut-while-running`, stacked on
  `docs/bulk-done-does-not-celebrate` (finding 52). Tier was Standard. The reproduction came first, and the
  owner approved the RESUMED gate in chat.
  - The route collector in `MainActivity` runs under `repeatOnLifecycle(RESUMED)`. `ConsumeWhileStarted` is
    `ConsumeWhileResumed`, with the same gate.
  - Spec 6.5 and 6.7 are amended.
- **Proof:** `ShortcutWhileRunningTest` sends both static shortcuts with their real flags while Matrix is on
  screen. On the old code the view never opened, 4 of 4 and 3 of 3 runs. The paused-screen case in
  `ConsumeWhileResumedTest` failed first with `expected [] but was [new-task]`.
- **A mutation that did not bite:** the consumer gate back on STARTED failed no shortcut test in three runs.
  The fresh copy wins that dispatch race by luck. The unit test is that gate's proof, and the record says so.
- **A test-side trap:** between the old copy's death and the fresh copy's first frame there is no compose
  hierarchy, and a finder throws `IllegalStateException`. The test waits on live activities first and catches
  that exception in its finder.
- **Merged:** both branches went into `main` directly with merge commits on the owner's word, at `b46e341`
  and `45192b4`, and `main` is pushed. The merge tree equals the tested tip. Six merged local branches are
  deleted. `origin/docs/record-production-application` (#25) is the one branch left on origin.
- **Next, in this order:** section C (findings 6 to 8: a date and time picker, the notification permission
  prompt, exact alarms on Android 14 and later), then section D (15 to 17: privacy policy link, Auto Backup
  versus the Settings copy, the data-safety form). Finding 16 is an owner decision.
- **Blockers:** none.
- **Gates:** `:core:model:test` 145, `testDebugUnitTest` 139 (`:core:sync` 95, `:app` 44), `connectedAndroidTest` 203
  on `gsd-test` (`:app` 143, `:core:store` 60, none skipped), `:app:assembleDebug`, and `:app:assembleRelease`
  (R8 clean). All green in 3m 14s on `6a8a0ab`, the fix commit before the rebase. The rebased commit
  `2f06185` has the same code, and differs only by the finding 52 docs beneath it.

## Resuming From Here (2026-09-21, 0.9.1 submitted, finding 52 closed)
- **Merge state:** #26, #27, and #28 are merged. `main` at `fa10578` carries every fix from the review and
  the 0.9.1 bump. GitHub deleted those three branches; `origin/docs/record-production-application` (#25)
  is still on origin, and four merged branches are still local. Deleting them waits for the owner's word.
- **Release:** 10 (0.9.1) is on the Alpha closed-testing track, sent for review at 8:35 PM CDT. The record,
  including that Save submits by itself with managed publishing off, is in
  `docs/play/submission-checklist.md`.
- **Done:** finding 52 closed on branch `docs/bulk-done-does-not-celebrate`, a spec correction with no code
  change. Tier was Trivial. iOS decided it: its bulk bar calls `bulkComplete` with no haptic and no confetti,
  and the web has no bulk bar. Spec 8.4 now says so and why.
- **Assumption, stated:** following the one sibling that has the surface. If the owner wants one burst on
  Bulk Done, it is a small change in `MatrixScreen` around `runBulk`, and the spec line flips back.
- **Not pushed:** the branch is local.
- **Next, in this order:** finding 46 (a launcher shortcut fired while GSD is on screen lands on Matrix),
  which gets a design gate with the two candidates in its notes. Then section C (findings 6 to 8, reminders
  and the listing) and section D (15 to 17, Play policy). Also worth the owner's eye: the Console's "For
  your next release" card flags edge-to-edge for SDK 35+ and the deprecated `enableEdgeToEdge` parameters;
  finding 11 (insets outside the phone layout) is the same area.
- **Blockers:** none.
- **Gates:** none run for this docs-only change. The last full run was on `bea2693` and then on the 0.9.1 bump
  `3c62197`: `:core:model:test` 145, `testDebugUnitTest` 139, `connectedAndroidTest` 200, all green.

## Resuming From Here (2026-09-21, findings 50 and 51 every list celebrates)
- **Done:** findings 50 and 51 are fixed on branch `fix/celebrate-every-completion`, stacked on the finding 49
  branch. Tier was Standard for 50 and Trivial for 51. Neither had a design gate: 50 was a move the todo had
  already called for, and 51 followed the spec and iOS.
  - `CompletionCelebration` in `:app` owns the haptic and the confetti trigger. Matrix, the smart view list,
    and Browse each remember one and draw `ConfettiOverlay`. `taskRowActions` requires it.
  - The haptic goes through `LocalHapticFeedback`, which a test can replace. A reopen gets the haptic and no
    confetti.
  - Spec 5.8 and 8.4 are amended.
- **Proof:** four list tests failed first with `expected 1 but was 0` and no confetti. The reopen haptic test
  failed first the same way. Five guards passed on their first run and each failed under a mutation.
- **Found on the way:** finding 51 (fixed) and finding 52 (Bulk Done celebrates nothing, open).
- **Not checked live:** I did not tap on `gsd-test` by hand. The tests drive the real screens against the real
  store, with a counting haptic in place of the platform's.
- **Not pushed:** both branches are local. Pushing and opening the two stacked PRs waits for the owner's word.
  Merge 49 first, then retarget 50's PR to `main`.
- **Next, in this order:** finding 52 if the owner wants it before production, since it is small. Then
  finding 46, which gets a design gate. Then section C (findings 6 to 8) and section D (15 to 17).
- **Blockers:** none.
- **Gates:** `:core:model:test` 145, `testDebugUnitTest` 139 (`:core:sync` 95, `:app` 44), `connectedAndroidTest` 200
  on `gsd-test` (`:app` 140, `:core:store` 60, none skipped), `:app:assembleDebug`, and `:app:assembleRelease`
  (R8 clean). All green on `bea2693`. The whole gate run took 2m 57s.

## Resuming From Here (2026-09-21, finding 49 swipes on every task list)
- **Merge state:** #24 and #25 merged overnight, so `main` at `e49c5d4` held everything from 2026-09-20. This
  branch is cut from it.
- **Done:** review finding 49 is fixed on branch `fix/swipes-on-every-list`. Tier was Standard: two composables
  change how a row behaves, and spec 8.13 already asked for it, so it had no design gate.
  - `ViewListContent` and `BrowseSearchResults` wrap each row in `SwipeTaskRow`. The swipe takes
    `onToggleComplete` and `onDelete` from the same `TaskRowActions` the menu uses.
  - A swipe delete offers Undo through the `UndoDeleteEffect` already on both screens. The leading swipe is a
    toggle, as on Matrix and iOS, so it reopens a task in This Week's Wins.
  - Spec 8.13 and 8.14 are amended.
- **Proof:** four tests in `ListSwipeActionsTest` failed first. Each found its row, swiped, and timed out on
  its last line, waiting for the real store to change.
- **A correction to the finding:** it cited iOS swipes at `SmartViewListView.swift:110-123`. Those lines swipe a
  smart view link. The task swipes are in `TaskListRow.swift:33-47`, which both iOS lists draw. The conclusion
  held, and the review doc's table row is corrected.
- **Not checked live:** I did not swipe by hand on `gsd-test`. The tests swipe the real screens against the
  real store.
- **Not pushed:** the branch is local. Pushing and opening the PR waits for the owner's word.
- **Next, in this order:** finding 50 (the haptic and confetti outside Matrix), which means lifting both out of
  `MatrixScreen`. Then finding 46, which gets a design gate. Then section C (findings 6 to 8) and section D
  (15 to 17).
- **Blockers:** none.
- **Gates:** `:core:model:test` 145, `testDebugUnitTest` 139 (`:core:sync` 95, `:app` 44), `connectedAndroidTest` 191
  on `gsd-test` (`:app` 131, `:core:store` 60, none skipped), `:app:assembleDebug`, and `:app:assembleRelease`
  (R8 clean). All green on the fix commit `024689f`. The whole gate run took 2m 58s.

## Resuming From Here (2026-09-20, finding 45 equal requests stay distinct)
- **Done:** review finding 45 is fixed on branch `fix/one-shot-handler-cannot-suspend`, stacked on the branch of
  #23. Tier was Trivial: one class in `:app`, with a JVM test.
  - `OneShotRequest` wraps each `send` in a private `Sent` object, compared by identity. A `StateFlow` skips a
    value equal to the last one its collector saw, and both real requests carry `Unit`.
  - Spec 6.7 is amended.
- **A deviation from the note, on purpose:** the todo said to make `handle` non-suspending. That forbids the
  trigger and leaves the cause. A sender on another thread could still wedge it, and no regression test can
  exist for a case that no longer compiles. With the wrapper, a handler may suspend, and a test pins it.
- **Proof:** one JVM test failed first with `expected 2 but was 1`. The handler suspended on its first call,
  a second `send(Unit)` landed, and it was never delivered.
- **Owner decision recorded:** a tap on the GSD icon may close the sign-in tab. It moved to "Decided", with the
  launcher activity fix noted for after launch. The release smoke gained a two-way check of the sign-in tab.
- **Housekeeping:** the owner asked for the merged local branches to go. All five went, the last one after
  #23 merged.
- **Next, in this order:** finding 49 first (swipes on Browse search results and smart view lists). The spec
  already asks for them and `SwipeTaskRow` is ready, so it needs no design gate. Then finding 50 (the haptic and
  confetti outside Matrix), which means lifting both out of `MatrixScreen`. Then finding 46, which gets a design
  gate: a lifecycle gate on the router, or an activity-scoped controller. After those, section C (findings 6 to
  8, reminders and the listing) and section D (15 to 17, Play policy).
- **Where the session stopped:** 2026-09-20, end of day, at the owner's word. The tree is clean, and every gate
  below ran on the last code commit.
- **A stranded commit, caught the same night:** #24 merged at `526a1ac`, moments before the push of `3f4c8b8`,
  the commit that set this order and added the lesson about checking a PR's state. The check had said OPEN,
  and the merge and the push crossed. That commit and the production note ride on
  `docs/record-production-application`.
- **Production:** the owner applied for production access on 2026-09-20 at 8:08 PM. See section A.
- **Blockers:** none.
- **Gates:** `:core:model:test` 145, `testDebugUnitTest` 139 (`:core:sync` 95, `:app` 44), `connectedAndroidTest` 187
  on `gsd-test` (`:app` 127, `:core:store` 60, none skipped), `:app:assembleDebug`, and `:app:assembleRelease`
  (R8 clean). All green. The connected run took 2m 16s.

## Resuming From Here (2026-09-20, finding 44 the editor saves only what the user changed)
- **Merge state:** the owner merged #20 and #21 while this was in progress. `main` at `7da3e87` has the same
  tree as this branch's base, so #22 targets `main` and its gate run holds for the merge result.
- **Done:** review finding 44 is fixed on branch `fix/editor-saves-only-its-fields` (#22). Tier was Standard.
  The owner approved the design in chat.
  - `EditorRules.merge(current, original, edited)` in `:core:model` is a per-field three-way merge over the
    twelve fields the editor owns. The reminder reset runs on its result.
  - `TaskStore.edit(id, transform)` reads the row inside its transaction, validates, stamps, enqueues, and
    writes. It returns null when the task is gone.
  - `TaskEditorViewModel.save` uses both for an existing task, and it takes an `onMissing` callback. The sheet
    shows the not-found toast, "That task no longer exists".
  - `EditorRules.applyReminderReset` had no caller left, so it is gone. Its three tests now go through `merge`.
  - Spec 8.3 is amended.
- **Assumption, stated to the owner:** a save on a task deleted while the sheet was open writes nothing. The
  owner approved the design without choosing, so I took my recommendation. The task stays in Trash.
- **Proof:** four JVM merge tests failed first against a stub that kept the old behavior. A store test ran
  eight concurrent edits against a stub that read outside the transaction, and 2 of 8 tags survived. Two view
  model tests failed on the old save: "the save undid a completion it never saw", and `expected missing but
  was done`.
- **Review of #22:** a fresh-context review approved it with no production defect. It found two test gaps,
  both confirmed by mutation and closed. Nothing pinned the queue row or the stamp `edit` writes: with the
  queue insert deleted, every test stayed green, and an editor save would never have synced. The merge tests
  pinned 4 of its 12 fields, so a table now covers all twelve in both directions. It also caught my misquote
  of the toast, which says "That task no longer exists".
- **Not changed:** `TaskStore.change` still reads outside its transaction. S5 fixed that pattern in
  `setCompleted`, and `edit` avoids it. `change` is a state change on one field, so the window is small.
- **Next:** findings 45, 46, 49, and 50. Then sections C to H of "Left to do".
- **Owner decisions still open:** whether a tap on the GSD icon may close the sign-in tab, and the three
  leftover branches under "Owner decisions".
- **Blockers:** none.
- **Gates:** `:core:model:test` 145, `testDebugUnitTest` 138 (`:core:sync` 95, `:app` 43), `connectedAndroidTest` 187
  on `gsd-test` (`:app` 127, `:core:store` 60, none skipped), `:app:assembleDebug`, and `:app:assembleRelease`
  (R8 clean). All green, after the review follow-ups. The connected run took 2m 23s.

## Resuming From Here (2026-09-20, finding 47 a reminder tap opens the task)
- **Done:** review finding 47 is fixed on branch `fix/reminder-tap-opens-the-task`, stacked on
  `fix/snoozed-reminder-fires-again` (#20). Tier was Trivial: two files and about ten lines.
  - `ReminderReceiver.tapIntent` builds a VIEW intent on `gsd://task/<id>`. The deep link router goes to Matrix
    first and then opens the task, so the tap works from any tab.
  - `MainActivity.handleIncomingIntent` no longer reads `EXTRA_TASK_ID`. The alarm and the two notification
    buttons still use that extra in their broadcasts.
  - Spec 11.2 is amended. It already named the link.
- **Proof:** `ReminderTapTest` failed first. From the Settings tab the old tap arrived, and "Edit Task" never
  showed. I extracted `tapIntent` with its old shape first, so the test sends what the receiver sends.
- **Next:** findings 44 (the editor saves a stale whole-row snapshot), 45, 46, 49, and 50. Finding 44 needs a
  design gate: it changes what Save writes.
- **Owner decisions still open:** whether a tap on the GSD icon may close the sign-in tab, and the three
  leftover branches under "Owner decisions".
- **Blockers:** none.
- **Gates:** `:core:model:test` 137, `testDebugUnitTest` 138 (`:core:sync` 95, `:app` 43), `connectedAndroidTest` 179
  on `gsd-test` (`:app` 124, `:core:store` 55, none skipped), `:app:assembleDebug`, and `:app:assembleRelease`
  (R8 clean). All green. The connected run took 2m 27s.

## Resuming From Here (2026-09-20, finding 5 a snoozed reminder fires again)
- **Merge state:** the owner merged #19, so `main` at `dd05fae` holds finding 48 and its review follow-ups. Its
  tree matched the branch tip the gates ran on.
- **Done:** review finding 5 is fixed on branch `fix/snoozed-reminder-fires-again`, based on `main`. Tier was
  Trivial: one condition in `ReminderPlanner.fireTime`, in `:core:model`. No design gate, because both siblings
  already fire again after a snooze.
  - A future snooze is the fire time even when `notificationSent` is true. A posted reminder with no future
    snooze still returns null.
  - Spec 11.1 is amended. It already said a future snooze is the fire time.
- **Sibling check:** iOS ignores the sent flag on its snooze branch (`App/Notifications/LiveReminderScheduler.swift`).
  The web's notification snooze resets the flag (`lib/notification-checker.ts`). I followed iOS.
- **Proof:** one JVM test failed first, with two guards beside it. One on-device test goes through the real
  Snooze receiver. On the old planner it failed with "a snoozed reminder has no fire time".
- **Not observed:** an alarm firing. `ReminderResync` re-plans on every store change, and that path did not
  change. The scheduler builds the same `PendingIntent` to schedule and to cancel, so a test cannot see an alarm.
- **Next:** finding 47 (a reminder tap opens nothing unless Matrix is on screen), then 44, 45, 46, 49, and 50.
- **Owner decisions still open:** whether a tap on the GSD icon may close the sign-in tab, and the three
  leftover branches under "Owner decisions".
- **Blockers:** none.
- **Gates:** `:core:model:test` 137, `testDebugUnitTest` 138 (`:core:sync` 95, `:app` 43), `connectedAndroidTest` 178
  on `gsd-test` (`:app` 123, `:core:store` 55, none skipped), `:app:assembleDebug`, and `:app:assembleRelease`
  (R8 clean). All green. The connected run took 2m 26s.

## Resuming From Here (2026-09-20, finding 48 the task menu works on every list)
- **Merge state:** the owner merged #17 and #18 to `main` on 2026-09-21 at 01:56 and 01:57 UTC, while the review
  of #18 was still being worked. GitHub deleted the branch. My next push made it again, with the three review
  follow-ups on top of the merged tip. They are not on `main` until their own PR merges. `main` at `b4c5218` has
  the same tree as the base those follow-ups were tested on.
- **Done:** review finding 48 is fixed on branch `fix/task-menu-everywhere`, stacked on the docs branch of #17.
  Tier was Non-trivial. The owner picked option A, "wire all eight", and approved the design in chat. The spec is
  `tasks/spec.md`.
  - `TaskActionsViewModel(store, trashStore)` owns the row operations. They moved out of `MatrixViewModel`, and
    `BrowseViewModel` lost its copy of `toggleCompleted`. Each of the three screens creates one.
  - `taskRowActions(...)` builds the full menu, and it holds the Share intent. `UndoDeleteEffect` offers Undo on
    all three screens.
  - `TaskRowWithMenu` takes `menuEnabled`, and Matrix passes `!selectionMode`. `TaskCard.onToggleComplete` is
    nullable, and Trash and Archive pass null.
  - Spec 5.6, 8.1, 8.13, and 8.14 are amended.
- **Proof:** ten tests failed first. The seven in `ListMenuActionsTest` drive the real list screens against the
  real store. Each opened the menu, tapped its item, and timed out waiting for the store to change.
  `SelectModeMenuTest` found the button still there in select mode. The Trash and Archive tests found the disc.
- **Checked live on `gsd-test`:** Share from the No Deadline list opened the system chooser with the task's
  text. Share has no automated test, because the project has no intent-testing library.
- **Found on the way:** findings 49 (no swipes on the two lists) and 50 (no celebration outside Matrix).
- **Review of #18:** a fresh-context review approved it and found one real bug. A cancelled `UndoDeleteEffect`
  never cleared its record, so a tab switch brought the same Undo offer back. The plain `finally` the reviewer
  suggested broke a second delete, and a guard test showed it. The effect now clears only the record it
  showed (`clearUndo(shown)`, a `compareAndSet`). The real-store tests skip on a signed-in device.
- **Next:** findings 5 and 47 together, because both are reminder paths. Then 44, 45, 46, 49, and 50.
- **Owner decision still open:** whether a tap on the GSD icon may close the sign-in tab (see "Owner
  decisions").
- **Blockers:** none.
- **Gates:** `:core:model:test` 134, `testDebugUnitTest` 138 (`:core:sync` 95, `:app` 43), `connectedAndroidTest` 177
  on `gsd-test` (`:app` 122, `:core:store` 55, none skipped), `:app:assembleDebug`, and `:app:assembleRelease`
  (R8 clean). All green, after the review follow-ups. The connected run took 2m 24s. Two runs before it took
  5m 17s and 6m 36s with every test passing, which looks like host load.

## Resuming From Here (2026-09-20, broken-behavior fixes merged to main)
- **Done:** the owner merged pull requests #13 to #16 to `main`, in order, with merge commits: findings 4, 1, 2,
  and 3. Each PR was retargeted to `main` before its merge, and every commit from the stack is on `main`. The four
  remote branches are gone.
- **First run together:** #13 gained a fourth link test and a cleanup wait after the branches above it were cut.
  Those changes never ran with #14 to #16 until this gate run on `main`.
- **Reviews:** the CI review posted nothing useful on #13 or #14, so fresh-context reviewers covered #13 to #15.
  #16 had no review that I read. Its root-cause tests went red first, and its guard test was proved by mutation.
- **Next:** finding 48, which needs the owner's choice first. Either wire all eight menu actions on Browse
  search results and smart view lists, or show only the wired ones. Then 5 and 47 together, then 44, 45, and 46.
- **Owner decisions still open:** finding 48's choice, and whether a tap on the GSD icon may close the sign-in
  tab (see "Owner decisions"). The four merged local branches still exist on this machine.
- **Blockers:** none.
- **Gates on `main` at `035a767`:** `:core:model:test` 134, `testDebugUnitTest` 138 (`:core:sync` 95, `:app` 43),
  `connectedAndroidTest` 165 on `gsd-test` (`:app` 110, `:core:store` 55), `:app:assembleDebug`, and
  `:app:assembleRelease`. All green. The connected run took 5m 17s, against 1m 30s to 2m earlier the same day.
  Every test passed, so I put it down to host load, as on the S6 run.

## Resuming From Here (2026-09-20, finding 3 swipe actions run once)
- **Done:** review finding 3 is fixed on branch `fix/swipe-delete-undo`, stacked on `fix/trash-row-background`.
  Tier was Standard: one composable, and a behavior change to both swipe directions.
  - `SwipeTaskRow` runs its actions in `onDismiss` and then resets the row. It no longer passes
    `confirmValueChange`, which Material 3 1.4.0 deprecates.
  - Spec 8.1 is amended.
- **Root cause, measured:** one trailing swipe called `onDelete` 8 times, and one leading swipe called
  `onComplete` 9 times. Material asks `confirmValueChange` on every drag frame. The review had guessed two calls.
- **Proof:** two tests in `SwipeTaskRowTest` failed first with `expected 1 but was 8` and `expected 1 but was 9`.
- **What I could not do:** reproduce the lost snackbar in a test. `SwipeDeleteUndoTest` passed on the old code in
  eight runs out of eight, at the default swipe speed and at 1.5 seconds. It stays as a guard for the path from a
  swipe to Undo, proved by mutation. Both of my timing explanations were wrong. Do not repeat them as fact.
- **Checked live on `gsd-test`:** a 600 ms swipe deleted the task, and the Undo snackbar appeared.
- **Not changed:** `MatrixViewModel.delete` still lets an empty result clear the last Undo record, and
  `TaskStore.delete` still reads the row outside its transaction. One call per swipe takes the pressure off
  both. S5 fixed the same read pattern in `setCompleted`.
- **Review of #14 and #15:** the CI review of #14 posted nothing after 13 turns. A fresh-context review found
  nothing above a nit in #15, and finding 48 in #14. I confirmed 48 by reading the three call sites.
- **Next:** finding 48, because finding 1 made it easier to hit. It needs the owner's choice first. Then 5 and
  47 together (both are reminder paths), then 44, 45, and 46.
- **Blockers:** none.
- **Gates:** `:core:model:test` 134, `testDebugUnitTest` 138 (`:core:sync` 95, `:app` 43), `connectedAndroidTest` 164
  on `gsd-test` (`:app` 109, `:core:store` 55), `:app:assembleDebug`, and `:app:assembleRelease` (R8 clean). All
  green. The connected run took 1m 59s.

## Resuming From Here (2026-09-20, review of #13)
- **Done:** #13 gained two commits after a fresh-context review, `ec977f4` and `0c2dff7`. The CI review of #13
  ran 27 turns with 11 denied tool calls and posted nothing, so its verdict is unknown.
  - The three link tests passed under `singleTop`, so nothing guarded the choice of `singleTask`. A fourth test,
    `aLinkClosesAPageAboveTheActivityAndStacksNoCopy`, fails under `singleTop` with `expected 1 but was 2`.
  - The test cleanup now waits for stray activities to be destroyed. One red test had leaked into the next.
  - Spec 6.4 says a launcher tap closes a page above `MainActivity`. Spec 10.2 limits "closes the tab" to a
    Custom Tab.
- **Recorded from that review:** finding 47, the Recents replay under "Worth a look", and the sign-in decision
  under "Owner decisions".
- **Gates on #13's branch after the change:** `:app` connected 103, all green. The two branches above it still
  merge cleanly (`git merge-tree`).
- **Stack:** #13 `fix/single-main-activity`, #14 `fix/task-card-options-button`, #15 `fix/trash-row-background`.
  Merge bottom-up with merge commits, and retarget the next PR to `main` after each merge.
- **Reviews still owed:** #14 and #15 have had no review that I have read.

## Resuming From Here (2026-09-20, finding 2 the Trash row background)
- **Done:** review finding 2 is fixed on branch `fix/trash-row-background`, stacked on
  `fix/task-card-options-button`. Tier was Trivial: one `when` in `matrix/SwipeTaskRow.kt`. No design gate,
  because a resting row has one right answer.
  - The `when` that picks the swipe background names all three states. `Settled` draws nothing.
  - Spec 8.1 is amended.
- **Proof:** the pixel test in `SwipeTaskRowTest` failed first with the alert red, `0xFFB23A2E`, where paper was
  expected. The mid-swipe guard passed at once, so I proved it by mutation. With the background never drawn,
  it failed on paper where the success green was expected.
- **Checked live on `gsd-test`:** a task deleted through the new "..." menu, then `gsd://trash`. The row sits on
  paper and its three labels are readable. One screenshot showed findings 4, 1, and 2 working together.
- **Not changed:** Trash still dims its rows to 72%. During a swipe the background shows through the dimmed
  row a little. That lasts only while the finger is down.
- **Next:** finding 3 (no Undo snackbar after a swipe-delete), then 5, 44, 45, and 46, each test-first. The
  probable cause of 3 is in the review: `confirmValueChange` fires twice, the second `store.delete` returns
  null, and the null overwrites `_lastDeleted` (`matrix/MatrixViewModel.kt:64-68`).
- **Blockers:** none.
- **Gates:** `:core:model:test` 134, `testDebugUnitTest` 138 (`:core:sync` 95, `:app` 43), `connectedAndroidTest` 161
  on `gsd-test` (`:app` 106, `:core:store` 55), `:app:assembleDebug`, and `:app:assembleRelease` (R8 clean). All
  green. The connected run took 1m 29s.

## Resuming From Here (2026-09-20, finding 1 the options button)
- **Done:** review finding 1 is fixed on branch `fix/task-card-options-button`, stacked on
  `fix/single-main-activity`. Tier was Trivial, with one design decision approved in chat: hide, not disable.
  - `TaskCard` takes one `onOpenMenu` callback. A long press and the "..." button both call it.
  - When the callback is null the card shows no button. Trash, Archive, and the tablet grid draw such a card.
  - Spec 5.6 and 8.1 are amended.
- **Proof:** two component tests in `ContextMenuTest` failed first. The button test found the button, tapped
  it, and failed on `'Edit' is not displayed`. The bare card test failed on a found "Task options" node.
- **Owner approvals that stand:** push and open a stacked PR when green, for findings 1 to 3, 5, 44, and 45.
- **Next:** finding 2 (Trash rows over the red swipe background), then 3, 5, 44, 45, and 46, each test-first.
- **Blockers:** none.
- **Gates:** `:core:model:test` 134, `testDebugUnitTest` 138 (`:core:sync` 95, `:app` 43), `connectedAndroidTest` 159
  on `gsd-test` (`:app` 104, `:core:store` 55), `:app:assembleDebug`, and `:app:assembleRelease` (R8 clean). All
  green. The connected run took 2m 4s.

## Resuming From Here (2026-09-20, finding 4 one MainActivity)
- **Done:** review finding 4 is fixed on branch `fix/single-main-activity`, based on `main`. Tier was Standard, and
  the design was approved in chat.
  - `MainActivity` is `singleTask`. That is the whole production change: one manifest attribute. `onNewIntent`
    already called the intent handler.
  - Spec 6.4 and 10.2 are amended.
- **Proof:** three on-device tests in `RunningAppLinkTest` failed first. The instance count gave
  `expected 1 but was 2`. The Dashboard link never selected its tab on the visible copy. The capture link never
  showed the New Task sheet on screen, which is the S4 regression. With the `handleIncomingIntent` call removed
  from `onNewIntent`, all three failed again, so they guard that path too.
- **Why `singleTask` and not `singleTop`:** the sign-in Custom Tab sits above `MainActivity` in GSD's task.
  `singleTop` only reuses an activity at the top of its task, so the OAuth callback would still stack a copy.
- **Both reviewer claims held.** The static shortcuts carry `NEW_TASK`, `CLEAR_TASK`, and `TASK_ON_HOME`. The
  reminder tap's `SINGLE_TOP` already landed in `onNewIntent`.
- **Checked by hand on `gsd-test`, with `dumpsys activity` after each step.** One record remained after a link
  on screen, a link with no activity, a cold start, both widget intent shapes, and the reminder shape. With a
  Chrome page above `MainActivity`, a forged `oauth-callback` closed the page, reached the original activity,
  and logged `StateMismatch`. A share started with `NEW_TASK` sat above `MainActivity` and left it intact.
- **Found on the way:** finding 46. A shortcut that fires while GSD is on screen lands on Matrix. A build with no
  launch mode does the same, so it is older than this fix. Notes are under "Left to do".
- **Not walked:** a widget placed on a launcher, a share from another app's chooser, and a real sign-in. The
  sign-in needs the owner's account, and it is now part of the release smoke.
- **Next:** findings 1 to 3, then 5, 44, 45, and 46, each test-first. Finding 1 is `IconButton(onClick = {})` at
  `matrix/TaskCard.kt:128`. The menu state lives in `TaskRowWithMenu`, which opens it only on a long press.
- **Blockers:** none.
- **Assumptions:** a link or a launcher tap may close another app's page that sits above GSD in its task. That
  came with the approved design.
- **Emulator:** `gsd-test` holds the fixed debug build with onboarding done. The sign-in tap opened Chrome's
  first-run screen, and I did not complete it.
- **Gates:** `:core:model:test` 134, `testDebugUnitTest` 138 (`:core:sync` 95, `:app` 43), `connectedAndroidTest` 157
  on `gsd-test` (`:app` 102, `:core:store` 55), `:app:assembleDebug`, and `:app:assembleRelease` (R8 clean). All
  green. The connected run took 1m 50s.

## Resuming From Here (2026-09-20, review fixes merged to main)
- **Done:** pull requests #3 to #9 are merged to `main`, in order, with merge commits: S1, S2, S3, S4, finding 43,
  S6, S5. The PRs were stacked, each based on the branch below it, so each one listed only its own commits. After
  each merge the next PR was retargeted to `main`. No rebase and no force push.
- **Found in review and fixed in #5 before it merged:**
  - `LocalErase.eraseAll` now clears the pull cursor inside its transaction. Before, "Start fresh" erased first and
    cleared the cursor in a later step. A process death in between left a wiped device on the old account's cursor.
  - `SyncCoordinator.start` finishes an owner change that died with no tasks left. It clears the cursor and records
    the owner before it signs in.
  - `start` also records a first owner that died unrecorded. Without it, a later account switch found no previous
    owner, raised no prompt, and reconciliation deleted the first account's tasks from the device.
  - My first test for this went vacuous once the erase cleared the cursor itself. It now builds the state directly,
    and it fails when the `adopt` call is removed.
  - Spec 8.16, 8.21, and 9.4 say all of this. Spec 4.7 no longer claims that no other device reads the device-local
    fields, because iOS reads them when it first creates its local copy.
- **Accepted at merge time, owner decision:** since S4, a `gsd://capture` link that arrives while the app is on
  screen opens the New Task sheet on the covered `MainActivity`. Confirmed live. Cold start, background start, and
  rotation work. It is recorded under review finding 4 and gets fixed there.
- **How the reviews went:** the CI review workflow ran for real on #3, #5, #7, and #8. On #4, #6, and #9, and on
  every re-run after a push, it finished in about 15 seconds with a green check and no review. Its
  `--allowedTools` allows only the inline comment tool, so the review plugin's first `gh` call is denied. I covered
  the gaps with fresh-context reviewers. They found the S4 regression above, the vacuous test, and findings 44 and 45.
- **Next:** finding 4 first (one `MainActivity` instance, which also ends the S4 regression). Then findings 1 to 3,
  5, 44, and 45, each test-first. The full list is under "Left to do" at the top of this file.
- **Owner decisions still open:** the merged branches were deleted on 2026-09-20. Two published commit
  subjects run past the 72 character limit (`b9c49ca` at 76 and `9bd2aba` at 75). Fixing them would mean rewriting
  published history, so they stay. The review workflow needs a wider `--allowedTools` to review every PR.
- **Blockers:** none.
- **Gates on `main` at `1387b16`:** `:core:model:test` 134, `testDebugUnitTest` 138 (`:core:sync` 95, `:app` 43),
  `connectedAndroidTest` 154 on `gsd-test` (`:app` 99, `:core:store` 55), `:app:assembleDebug`, and
  `:app:assembleRelease` (R8 clean). All green. This was the first run of the late #5 fixes together with S4 to S5.

## Resuming From Here (2026-09-20, S5 completion is a transition)
- **Done:** review finding S5 is fixed on branch `fix/completion-is-a-transition`, stacked on the S6 branch.
  Merged in #9. Tier was Standard: one store method, no new API, and both siblings
  already guard the spawn.
  - `TaskStore.setCompleted` reads the row inside the transaction and returns the task untouched when it already
    has the requested state. No spawn, no restamp, no queue row.
  - Spec 8.5 is amended.
- **Proof:** five on-device tests failed first. A redundant completion and bulk Done over a finished task gave
  `expected 2 but was 3`. Eight concurrent completes gave `expected 2 but was 9`. A redundant completion and a
  redundant reopen both restamped: `expected 12:00 but was 15:00`. The bulk test was checked against the old store
  by swapping the file back for one run. Gates are in the block's last line.
- **Decisions:** a redundant call writes nothing at all. The web still restamps in that case. Android does not,
  because a restamp from a no-op tap could win last-write-wins over a newer edit, and it would replace the real
  `completedAt` that the Dashboard reads. Bulk Done counts an already finished task as succeeded.
- **Not covered by its own test:** the stale notification action. The receiver calls `store.setCompleted(id, true)`
  and nothing else, so the store tests cover it. An on-device test would have to wait for an absence.
- **Status of the review:** S1 to S6 and finding 43 are fixed on seven stacked branches. None is merged.
- **Next:** owner decision to merge the seven branches in order: S1, S2, S3, S4, finding 43, S6, then this one.
  Then findings 1 to 5, each test-first. Finding 5 (Snooze 1 hour never fires again) is the next data-adjacent one.
- **Blockers:** none.
- **Gates:** `:core:model:test` 134, `testDebugUnitTest` 138 (`:core:sync` 95, `:app` 43), `connectedAndroidTest` 151
  on `gsd-test` (`:app` 97, `:core:store` 54), `:app:assembleDebug`, and `:app:assembleRelease` (R8 clean). All
  green. The connected run took 1m 55s.

## Resuming From Here (2026-09-20, S6 state changes never reject)
- **Done:** review finding S6 is fixed on branch `fix/state-changes-never-reject`, stacked on the finding 43
  branch. Merged in #8. The design was approved in chat (option "Validate typed input only"), and the
  spec is `tasks/spec.md`.
  - `TaskValidator.normalize` applies the three allowed coercions and never throws. `validate` is built on it.
  - `TaskStore.add`, `update`, and `replaceAll` still validate. `setCompleted`, its recurrence spawn, `snooze`, and
    the timers only normalize. `change(id)` and `duplicate(id)` are new and do the same.
  - `MatrixViewModel` uses `change` for the quadrant move and `duplicate` for Duplicate. `BulkActions` uses
    `change` for Move, Remove tag, and Set due. Bulk Add tag still validates, because the tag is typed.
  - Spec 7.11 and Appendix B are amended.
- **Proof:** the view model test reproduced the crash first: "Instrumentation run failed due to Process crashed"
  on `TitleLength`. Six store tests failed first with `TitleLength` on a title of 79 letters plus one emoji. The
  bulk test failed first with `expected BulkResult(1, 0) but was BulkResult(0, 1)`. The `normalize` coercion test
  failed first against an identity stub, and the "never rejects" test was proved by mutation. Gates are in the
  block's last line.
- **Decisions:** text limits still count UTF-16 units, as on the web. iOS counts characters. No choice matches both
  siblings, because they disagree with each other. The Android editor therefore refuses to save an iOS emoji title
  until the user shortens it, which is what the web does today.
- **Not changed:** `NotificationActionReceiver` still has no catch. Its two store calls can no longer throw a
  validation error, so S6 cannot reach it. A database error there would still crash.
- **Next:** owner decision to merge the six branches in order: S1, S2, S3, S4, finding 43, then this one. Then S5,
  which iOS solves by reading the persisted row before it decides the completion, and findings 1 to 5.
- **Blockers:** none.
- **Gates:** `:core:model:test` 134, `testDebugUnitTest` 138 (`:core:sync` 95, `:app` 43), `connectedAndroidTest` 146
  on `gsd-test` (`:app` 96, `:core:store` 50), `:app:assembleDebug`, and `:app:assembleRelease` (R8 clean). All
  green. The first full run failed one S4 test on a soft keyboard race. `0e01966` fixes that test and one more.
- **Emulator:** during this change the connected run grew from about 1m 45s to 4m 48s, and every class slowed
  down. It was back to 1m 55s on the next change with no reboot, so host load is the likelier cause.

## Resuming From Here (2026-09-20, finding 43 Matrix tab)
- **Done:** review finding 43 is fixed on branch `fix/matrix-tab-after-deep-link`, stacked on the S4 branch.
  Merged in #7.
  - `nav/TabNavigation.kt` holds `navigateToTab` and `navigateToRoute`. For Matrix, `navigateToTab` pops back to
    the start destination with `saveState`. For the other three tabs it is the old tab handler, unchanged.
  - The tab bar, rail, and drawer call `navigateToTab`. The deep link router calls it for Matrix, Dashboard, and
    Settings routes. The palette's navigation goes through `navigateToRoute`, which picks the tab helper for a
    top-level route and a plain push for anything else.
  - Spec 6.1 and 6.4 are amended.
- **Proof:** four on-device tests failed first on the old code. The Matrix tab stayed unselected after a smart view deep link and
  after the palette's Dashboard row. One Back press did not leave the app after `gsd://focus` or after the
  palette's Matrix row. Four more failed against my first fix: the editor sheet would not close after `gsd://capture`, it would
  not open after the tab returned to Matrix, and a stub `NavHost` showed the Matrix entry at CREATED instead of
  RESUMED (two tests). A ninth test guards the stack a sibling tab keeps. It passed before the change, and it
  failed when I turned restore off for every tab, so it does protect that behavior. Gates are in the block's
  last line.
- **The wrong turn:** the first fix kept `navigate()` for Matrix and only set `restoreState` to false. That call
  then reached `launchSingleTop`, which swaps the visible Matrix entry for a copy that never rises past CREATED.
  The sheet could not close and the screen stopped updating. The S4 `LaunchIntentTest` caught it in the full
  suite, before any push. The commit was amended, so the broken version is not in history.
- **Decisions:** tapping Matrix always lands on the Matrix root. A screen pushed over Matrix is popped with
  `saveState`, as before, and nothing restores it. Navigation keeps that entry's view model until the activity
  ends. That cost already existed on the same paths, and it is small.
- **Next:** owner decision to merge the five branches in order: S1, S2, S3, S4, then this one. Then S6 (a crash),
  S5, and findings 1 to 5, each test-first.
- **Blockers:** none.
- **Assumptions:** sub-screens (smart view, Archive, Trash, feedback) still push over the current screen, so Back
  returns to where the user was. Review finding 4 (`launchMode` and stacked activities) stays a separate change.
- **Gates:** `:core:model:test` 132, `testDebugUnitTest` 138 (`:core:sync` 95, `:app` 43), `connectedAndroidTest` 134
  on `gsd-test` (`:app` 92, `:core:store` 42), `:app:assembleDebug`, and `:app:assembleRelease` (R8 clean). All
  green. Checked by hand too: widget link, Matrix tab, then a captured task showed up in Do First.

## Resuming From Here (2026-09-20, S4 one-shot requests)
- **Done:** review finding S4 is fixed on branch `fix/new-task-requests-one-shot`, stacked on the S3 branch. Four
  code commits (`2cf82b3`, `2f77612`, `1d0f38c`, `fdbf6f5`) plus one docs commit (`8c49929`). Merged in #6.
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
  lead into it. Fixed on the next branch (see the block above).
- **Emulator:** the `google_apis` API 36 system image lost its `.img` files again mid-session. `sdkmanager --install`
  did nothing, because the leftover `package.xml` made it look installed. `--uninstall` then `--install` restored
  it (4.3 GB). The `google_apis_playstore` image is broken the same way and no AVD uses it. The cause is unknown.
- **Next:** see the finding 43 block above.
- **Blockers:** none.
- **Assumptions:** an open editor wins over a new-task request. A request made off the Matrix tab waits for the next
  visit to Matrix, then opens once. Review finding 4 (`launchMode` and stacked activities) stays a separate change.

## Resuming From Here (2026-09-20, S3 account switch keeps tasks)
- **Done:** review finding S3 is fixed on branch `fix/account-switch-keeps-tasks`, five commits from `f9a0ab1` to
  `9bd2aba`, stacked on `fix/reminder-bookkeeping-lww`. Merged in #5, with three more fixes from review. The pure `OwnerChange.adopt` in
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
  `d79ad2a`, stacked on `fix/sync-dead-session-guard`. Merged in #4. `ReminderReceiver` now records a
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
  `cbe1067`. Merged in #3. `SyncEngine.runSync` aborts with `NotSignedIn` when no owner resolves.
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
