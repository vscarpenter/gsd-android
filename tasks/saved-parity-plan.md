# Saved parity plan (October 2026 round)

Spec: `docs/2026-10-29-cross-platform-parity-spec.md`. Branch: `feat/saved-parity`.
Backlog: B-49 (Saved), B-50 (fixture), then B-13, B-14, B-42 (sync safety).

Each task is one red/green cycle and one commit.

1. [~] `:core:model` — `Task.savedAt`; `SavedPlacement` (save, return, guards);
       Saved left out of `FilterCriteria`, `Analytics`, `ReminderPlanner`; the
       `web-saved-14.2.0.json` fixture imports with placement intact.
2. [~] `:core:sync` — pull contract version 3 with a merge-same-version backfill;
       `saved_at` on the wire (`""` clears); queued payloads take `savedAt` from
       the current row; an incoming return resets device-local reminder state.
3. [~] `:core:store` — Room 2 → 3 (`savedAt` on three task tables,
       `contractVersion` on `sync_metadata`); `TaskStore` save, return, notes,
       and refusals (complete, timer, snooze while Saved).
4. [~] `:app` — matrix, widget, reminders, bulk, and palette leave Saved out or
       label it; Move to Saved with Undo; the Saved screen with search and
       Return to matrix.
5. [ ] `:core:sync` — B-13 (5xx is transient), B-14 (session fence), B-42
       (stale-delete guard).
6. [ ] Fold the spec's §6 amendments into `docs/gsd-android-spec.md`.

Verification: `./gradlew :core:model:test :core:sync:testDebugUnitTest
:core:store:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug`, run
from Android Studio's Gradle panel; instrumented and end-to-end checks per the
spec §5.

## Resuming from here (2026-10-29)

`[~]` means written with tests but **not yet compiled or run**. The Claude session that wrote
it could not reach Maven or the Android SDK, and Android Studio wouldn't take clicks while its
What's New tab was open.

Next:

1. Run `./gradlew :core:model:test :core:sync:testDebugUnitTest :app:testDebugUnitTest
   :app:assembleDebug`. Fix anything red.
2. The build writes `core/store/schemas/dev.vinny.gsd.store.GsdDatabase/3.json`. Commit it.
3. Run the instrumented store tests on an emulator: `./gradlew :core:store:connectedDebugAndroidTest`
   (`MigrationTest`, `TaskStoreSavedTest`).
4. Task 4 so far keeps Saved items off the matrix, the widget, and reminders. Still to build: Move
   to Saved with Undo, the Saved screen with search and Return to matrix, the palette label, and
   bulk skipping Saved.
5. Then tasks 5 and 6.
