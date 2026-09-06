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
- **Next:** watch the Dashboard opted-in count daily; record the date it reaches 12; on
  day 14 submit the production questionnaire (how testers were recruited, how they used the
  app, feedback received, changes made — keep the Reddit thread and group replies as
  evidence; a mid-window closed-test release with a small fix is a strong answer). After
  Google approves production access, promote the Alpha release to Production.
- **Still open from 2026-08-29:** release smoke on a device (item 6 above) was not recorded as
  done; the AGP `9.3.0 → 9.3.2` bump is still uncommitted — `gradle/libs.versions.toml` on
  `main` reads 9.3.0 while the shipped artifacts were built under 9.3.2. Resolve before the
  next release build.
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
