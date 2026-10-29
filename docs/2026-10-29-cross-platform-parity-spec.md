# GSD Android — Cross-Platform Parity Specification (October 2026 round)

> **Status:** draft brief, 2026-10-29. Not yet approved or planned. Written from the 2026-10-29 parity round (the GSD Project's `parity/` docs: backlog B-49, B-13, B-14, B-42; roadmap phase 1).
>
> **Precedence:** once approved, this document wins over `docs/gsd-android-spec.md` for every section it touches until its amendments (§6) are folded in. Everything it doesn't touch stays governed by the main spec and the 2026-08-29 and 2026-09-06 parity specs.
>
> **Ship target:** the Android build that goes to Play production. Saved items already exist on the server, so a public Android build that shows them as active tasks is a cross-client correctness bug.

## 0. Authority map (what changed where since the September round)

Android `main` has had no code change since `e4f1d8d` (2026-10-13). In that time the web and Apple clients shipped one new synced field and one new sync rule.

| Source | Commit | What it is | Origin |
|---|---|---|---|
| web #630 | `42f00ef` (2026-10-26) | **Saved**: a task kept outside the matrix, with short notes. New synced field `saved_at` | Web; decision in web `docs/adr/0018-saved-task-placement.md` |
| iOS #34 | `0114c51` (2026-10-26) | Native Saved, same contract | iOS `docs/adr/0001-saved-placement-parity.md`, `docs/2026-10-26-saved-ios.md`, `spec.md` (Saved placement) |
| web #632 / iOS #36 | `310e363` / `4352ce1` (2026-10-27) | **Pull contract version.** A build that first reads a new synced field re-pulls everything once and merges same-version rows | web `docs/adr/0019-pull-contract-version-backfill.md`; iOS `docs/2026-10-27-synced-field-backfill.md` |
| MCP 1.4.0 | `d17cfb3` (2026-10-26) | The MCP server keeps `saved_at` on writes and leaves Saved out of active tools | web `packages/mcp-server` |
| web #583, #538, push delete guard | Oct 7, Sep 11 | Sync-safety rules Android hasn't fully adopted (backlog B-13, B-14, B-42) | web `lib/sync/*` |

Primary sources, for anyone re-verifying a rule:

- Web: `lib/use-tasks.ts:55-56` (active vs Saved split), `lib/tasks/saved.ts`, `lib/sync/task-mapper.ts` (`saved_at`), `lib/constants/sync.ts:63` (`PULL_CURSOR_VERSION: 3`), `lib/sync/pb-pull.ts`, `docker/pb_migrations/1793030400_saved_tasks.js`, `lib/sync/error-categorizer.ts:64-82`, `lib/sync/sync-session.ts`, `lib/sync/pb-push.ts:95-105, 169-180`.
- iOS: `GSDKit/Sources/GSDSync/SyncCursor` (contract version), `SyncEngine` (`mergeSameVersion`), `GSDStore/TaskStore.swift` (Saved actions and guards).
- Fixtures: web `tests/fixtures/pinned-copies/pb-record-shape.json` (has `saved_at`); iOS `GSDKit/Tests/GSDStoreTests/Fixtures/web-saved-14.2.0.json` (md5 `8d46a3b18e9381ea1ebd124ab5dc2f7b`).

## 1. Verified current behavior (code-read, 2026-10-29)

- `core/sync/.../WireTask.kt` has no `saved_at`. `WireJson.json` uses `ignoreUnknownKeys = true` and `explicitNulls = false`, so a pulled `saved_at` is dropped and a push leaves the server's value alone. **No data loss today.**
- Android shows Saved items as active tasks: in the matrix (`MatrixViewModel.buildSections`), smart views, the widget, analytics, reminders, and bulk actions. It lets you complete them and start timers, which web and Apple refuse until the task returns to the matrix.
- A web or Apple backup restored on Android loses Saved placement, because `Task` has no `savedAt` and unknown keys are ignored.
- Push sends the payload captured at enqueue time (`SyncEngine.push`, `SyncQueueEntity.payloadJson`), not the current row.

## 2. Change 1 — Pull contract version (do this first)

### 2.1 Why it comes first

iOS hit this on 2026-10-27. Rows pulled by a build that didn't know `saved_at` are stored without it, and the cursor has already moved past them. An equal `client_updated_at` is a no-op on pull, so even sign-out and sign-in can't bring the field across. If Android then adds `saved_at` with the wire default `""` (`encodeDefaults = true`), its next edit of such a row pushes `saved_at: ""` with a newer stamp and **un-saves the item on every client**.

### 2.2 Rule

- Store a contract version next to the pull cursor (`SyncMetadataEntity`), numbered like the web's `PULL_CURSOR_VERSION` and iOS's `SyncCursor.contractVersion`. This change sets it to **3**.
- A cursor without the current version was written by an older build. The next sync pulls from the epoch once with **merge-same-version**: a remote record whose `client_updated_at` equals the local `updatedAt` is merged through `WireMapper.pull` (device-local fields survive, the new field lands) and isn't counted as a conflict. Strictly older remote records stay a no-op.
- Stamp the version only after that pull completes with no error. `BoundedListing` already fails closed, and `run()` skips push when pull throws, so no push runs before the backfill succeeds.
- A fresh install, or a cleared cursor, has nothing to backfill and stamps the version on its first full pull.
- Ordinary pulls keep the strictly-newer rule.

### 2.3 Queued payloads from before the upgrade

A change queued by the old build carries a payload with no `savedAt`. When the drain sends a CREATE or UPDATE, take `savedAt` from the **current local row** (after the backfill pull), not from the payload. If the row is gone, send the payload without the field (`savedAt = null`, omitted by `explicitNulls = false`).

## 3. Change 2 — Saved

Behavior matches web ADR 0018 and iOS ADR 0001. Where they differ, the web wins.

### 3.1 Model and storage

- `Task.savedAt: Instant?` (`:core:model`). Non-null means Saved. Serialized as `savedAt` in `TaskJson` and the backup envelope.
- Room migration **2 → 3**: add a nullable `savedAt INTEGER` to `tasks`, `archived_tasks`, and `deleted_tasks`, plus `contractVersion` on `sync_metadata` (§2). Export the version 3 schema. A migration test opens a version 2 database with rows in all three tables and checks nothing is lost.

### 3.2 Wire

- `WireTask.savedAt: String? = null` with `@SerialName("saved_at")`, so an unknown value is **omitted** rather than defaulted to `""`.
- Push: an ISO-8601 string when Saved; `""` when the task is in the matrix, which clears the field on the server (iOS spec: "returning MUST send `""`"). Omit it only in the §2.3 case.
- Pull: `""` or missing means not Saved.
- An incoming change from Saved to the matrix resets this device's `notificationSent`, `lastNotificationAt`, and `snoozedUntil`, as iOS does.
- Copy `web-saved-14.2.0.json` byte-identical into `core/model/src/test/resources/fixtures/cross-platform/` and run it (backlog B-50).

### 3.3 Actions (`TaskStore` stays the only mutation path)

- **Move to Saved:** for an incomplete task, from the card menu and the editor. Stop a running timer first. Offer Undo.
- **Return to matrix:** review the quadrant and optional due date, then clear `savedAt` and the reminder state above. Returning never completes the task or spawns a recurrence.
- **Edit notes:** notes are `description` (600 UTF-16 units, the existing limit). A note write reads the latest row and changes only `description`.
- **Refuse while Saved:** complete, start timer, snooze, and bulk actions. A notification action (`NotificationActionReceiver`) that arrives for a task that is now Saved does nothing.

### 3.4 Where Saved items don't appear

Matrix (`MatrixViewModel`), smart views (`FilterCriteria`), Browse search results (except labeled as Saved), Dashboard analytics and upcoming deadlines (`Analytics.kt`, `DeadlineStatus.kt`), reminders (`ReminderPlanner.fireTime` returns null; `ReminderScheduler.resyncAll` cancels), the widget (`WidgetSnapshot`), and bulk selection.

### 3.5 Where Saved items still count

The dependency graph (a Saved prerequisite still blocks its dependents, including Ready to Work), backups, erase, account and import safety checks, and the palette, which labels a Saved result "Saved" and opens the Saved screen.

### 3.6 Surface

A **Saved** entry in Browse (phone) and the drawer (tablet), above Archive, with title, note, and tag search and an empty state. Rows show the title and the note beside it. Follow the Inkwell tokens; Saved rows use the muted surface, not a quadrant wash (web `components/command-palette/task-item.tsx:60`).

## 4. Change 3 — Sync-safety ports

| Backlog | Rule | Android today | Change |
|---|---|---|---|
| B-13 | A structured 429 or 5xx is transient and doesn't use up the 5-try budget (web #583, `lib/sync/error-categorizer.ts:64-82`) | 429 aborts the loop without a retry (`PocketBaseApi.RateLimited`); a 5xx counts toward the 5 tries (`SyncEngine.kt` push `catch`) | Treat 5xx like 429: stop the drain, keep items pending, don't bump `retryCount` |
| B-14 | A sync write belongs to the account signed in when the sync started; sign-out, erase, or a new sign-in fences it (web #538, `lib/sync/sync-session.ts`) | An in-flight pull or SSE event can still write after sign-out (`SyncEngine.kt:45-54`) | Capture the owner at the start of `run()` and each SSE session; check it inside the same transaction as every local write and drop the write when it no longer matches |
| B-42 | A queued delete is stale when the remote changed after the delete was queued (web `isStaleDelete`, `pb-push.ts:101-105`) | Only an archive's delete is checked, against the archived version (`remoteEditedAfterArchive`, `SyncEngine.kt:219-224`) | Also skip any delete when the remote stamp is newer than the queue row's creation time. Covers Trash and manual deletes |

## 5. Tests (TDD, red first)

- `:core:model`: `savedAt` round-trips through `TaskJson` and the backup envelope; `web-saved-14.2.0.json` imports with placement intact; Saved items drop out of `FilterCriteria`, analytics, `DeadlineStatus`, and `ReminderPlanner`, and stay in `DependencyGraph`.
- `:core:sync`, against the existing fake remote: (1) an old-version cursor past a Saved record applies `saved_at` in one sync and leaves the queue empty; (2) a pre-upgrade queued UPDATE for a Saved task pushes the current row's `savedAt`, never `""`; (3) the next sync is incremental; (4) return pushes `""`; (5) a 5xx leaves `retryCount` unchanged; (6) a write after sign-out is dropped; (7) a stale manual delete is skipped.
- `:core:store`: migration 2 → 3 keeps every row in all three task tables.
- Instrumented: Move to Saved, Undo, edit notes, and Return to matrix on a phone and a tablet.
- End to end, as iOS did (`gsd-iosapp/scripts/verify-saved-sync.mjs`): a local PocketBase 0.40.4, web saves, Android pulls and edits notes, returns, saves again, and the web reads one record with notes intact.

## 6. Amendments to `docs/gsd-android-spec.md` (after the code is green)

- §7.2 (Task), §7.10 (Room tables), §7.9 (FilterCriteria): add `savedAt`, the version 3 tables, and the Saved exclusion.
- New §8.22 (Saved): actions, refusals, and the screen; cross-reference it from §8.1, §8.11, §8.13, §8.15, and §8.16.
- §9.1 and §9.2 (wire and mapper): add `saved_at`, including `""` to clear and the reminder-state reset on return.
- §9.4 (pull and cursor): the contract version and merge-same-version backfill.
- §9.5 (push): 5xx is transient; the edit-beats-delete guard; queued payloads take `savedAt` from the current row.
- §9.6 (realtime): the session fence applies to SSE writes too.
- §6.1 and §6.2 (navigation): the Saved entry in Browse and the drawer.
- §11 (notifications): no reminder for a Saved task.

## 7. Out of scope

Escalation, top three, and the weekly sweep (roadmap phases 2, 4 and 5) have their own specs. Due time on web and Apple (D-1) is their work, not Android's. Apple's open sync items (B-07, B-08, B-44, B-45) stay in the Apple backlog.

## 8. Acceptance criteria

1. A Saved item created on web or Apple never appears in Android's matrix, smart views, widget, analytics, or reminders, including on a device upgraded from 0.10.0 with that item already pulled.
2. No sequence of Android edits, queued changes, upgrades, or sign-in cycles clears `saved_at` on the server unless the user returns the item to the matrix.
3. Save, notes, and return on Android round-trip to web and Apple with one server record and the same task id.
4. The B-13, B-14, and B-42 tests pass, and `./gradlew :core:model:test` stays sub-second.
5. The GSD Project's `parity/feature-matrix.md` rows O8, P2, S13, S14, and S19 change to ✅ for Android, and B-49 and B-50 close.
