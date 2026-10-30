# Saved parity plan (October 2026 round)

Spec: `docs/2026-10-29-cross-platform-parity-spec.md`. Branch: `feat/saved-parity`.
Backlog: B-49 (Saved), B-50 (fixture), then B-13, B-14, B-42 (sync safety).

Each task is one red/green cycle and one commit.

1. [x] `:core:model` — `Task.savedAt`; `SavedPlacement` (save, return, guards);
       Saved left out of `FilterCriteria`, `Analytics`, `ReminderPlanner`; the
       `web-saved-14.2.0.json` fixture imports with placement intact.
2. [x] `:core:sync` — pull contract version 3 with a merge-same-version backfill;
       `saved_at` on the wire (`""` clears); queued payloads take `savedAt` from
       the current row; an incoming return resets device-local reminder state.
3. [~] `:core:store` — Room 2 → 3 (`savedAt` on three task tables,
       `contractVersion` on `sync_metadata`); `TaskStore` save, return, notes,
       and refusals (complete, timer, snooze while Saved).
4. [x] `:app` — matrix, widget, reminders, bulk, and palette leave Saved out or
       label it; Move to Saved with Undo; the Saved screen with search and
       Return to matrix.
5. [x] `:core:sync` — B-14 (session fence) and B-42 (stale-delete guard); 121/121 green (20:39). B-13 needs no change: the 2026-10-12 audit misread web #583 (see the spec §4).
6. [x] Fold the spec's §6 amendments into `docs/gsd-android-spec.md` (§6.1, §7.2, §7.9, §7.10,
       new §8.22, §9.1, §9.2, §9.4, §9.5).

Verification: `./gradlew :core:model:test :core:sync:testDebugUnitTest
:core:store:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`, run
from Android Studio's Gradle panel; instrumented and end-to-end checks per the
spec §5.

## Resuming from here (2026-10-29)

`[~]` means written with tests but **not yet compiled or run**. The Claude session that wrote
it could not reach Maven or the Android SDK, and Android Studio wouldn't take clicks while its
What's New tab was open.

Next:

1. First run (17:59): `:core:model:test` 195/195 green; `:app:assembleDebug` built. Three
   `SavedSyncTest` failures came from test ids shorter than the mapper's 4-character minimum, fixed
   in `f38c9cb`. Second run (18:03): `:core:sync` 115/115 and `:app` 89/89 green.
2. Done: `3.json` committed in `7c470d6`.
3. Run the instrumented store tests on an emulator: `./gradlew :core:store:connectedDebugAndroidTest`
   (`MigrationTest`, `TaskStoreSavedTest`).
4. Task 4 (20:24 run: model 197/197, app 93/93, assembleDebug green; `SavedScreenTest` still
   needs an emulator): Move to Saved in the row menu with Undo; the Saved screen from
   Browse (search, notes, Return to matrix with a quadrant and date review, delete with Undo); a
   Saved entry in the palette, and Saved task results labelled and routed to Saved. Bulk needs no
   change: it acts on the matrix, which no longer shows Saved items. Run
   `./gradlew :core:model:test :app:testDebugUnitTest :app:assembleDebug`, then
   `SavedScreenTest` on an emulator.
5. Then tasks 5 and 6.

## Before the PR (2026-10-29, 20:45)

Green on the JVM: model 197, sync 121, app 93, and `assembleDebug`. Still owed, on an emulator:
`./gradlew :core:store:connectedDebugAndroidTest :app:connectedDebugAndroidTest` (the v2 → v3
migration, `TaskStoreSavedTest`, `SavedScreenTest`). Task 3 stays `[~]` until those run. Then the
end-to-end check against a local PocketBase (spec §5) and the tester build for Play.
