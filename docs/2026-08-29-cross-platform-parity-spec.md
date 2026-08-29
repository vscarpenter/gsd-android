# GSD Android — Cross-Platform Parity Specification (August 2026 catch-up)

- **Date:** 2026-08-29
- **Status:** Revised 2026-08-29 after a claim-by-claim three-repo verification — the five contested §2–§3 rules now name the client they follow; ready to implement
- **Purpose:** Bring gsd-android to feature and contract parity with the web app and the iOS app as they ship **today**. The Android app is feature-complete against `docs/gsd-android-spec.md` (dated 2026-07-19), but the sibling clients ran a major parity push on 2026-08-28/29 that changed four shared contracts and added one feature after that spec was written. This document is the authority for the catch-up work; `tasks/parity-plan.md` is the implementation plan. When this document and `docs/gsd-android-spec.md` disagree, **this document wins** — the plan's final task folds the amendments back into the main spec.
- **Method:** Every value below was read from the current web (`gsd-taskmanager`) and iOS (`gsd-iosapp`) trees on 2026-08-29, not from memory or from either repo's own spec. A same-day verification pass then corrected five rules where web and iOS quietly disagree and the draft had recorded a coherent pick as a shared observation; each contested rule now says which client it follows.

## 0. Authority map (where each contract truly lives)

| Contract | Authority (current) | Beware |
|---|---|---|
| Backup envelope | web `lib/schema.ts` (`importPayloadSchema`) + web `docs/adr/0014` + iOS `GSDKit/Sources/GSDModel/TaskExport.swift` + iOS `docs/2026-08-28-trash-and-backup-parity.md` | iOS `spec.md` §6.16 and Android spec §8.16 both describe the **retired v1 envelope** |
| Trash | web `lib/db.ts` v16 + web ADR 0015 + iOS DB migration v6 (commit `e5735e8`) | Neither client's product spec has a trash section; the ADRs + code are it |
| Capture parser | `gsd-taskmanager/tests/fixtures/cross-platform/capture-parser-corpus.json` (40 cases, byte-identical copy in each repo) + `gsd-iosapp/docs/2026-08-28-capture-parser-parity.md` | Android spec §8.2/Appendix C predates nine settled decisions |
| Feedback | web `lib/feedback/feedback-payload.ts` + iOS `GSDKit/Sources/GSDModel/Feedback.swift` + `gsd-iosapp/docs/2026-08-29-feedback-parity.md` | Absent from every product spec |
| Field limits | web `lib/constants/schema.ts` ↔ iOS `Validation.swift` ↔ Android `TaskValidator.kt` | Already at exact three-way parity — verified 2026-08-29, no work |
| Design tokens | web `app/globals.css` + `app/css/inkwell-tokens.css` (Inkwell 1.4.0 "GSD Editorial") ↔ iOS `App/Theme/Theme.swift` | Android's §5 values verified current — no work (see §1.2) |
| Wire model | `gsd-iosapp/spec.md` §7.1 | Unchanged since July — no work |

## 1. Verified parity — explicitly **no work**

These were audited against the current trees so future sessions don't re-litigate them.

### 1.1 Already matching

- **Field limits** — Android `TaskValidator.kt` matches web `SCHEMA_LIMITS` and iOS `FieldLimits` (title 1–80, description ≤600, tag 1–30 ×≤20 lowercase, subtask 1–100 ×≤50, deps ≤50, time entries ≤1000 with notes ≤200, estimate 1–10080 with 0 = unset, id ≥4). One asterisk: iOS enforces no id-length rule at all and checks the 200-char time-entry note only at the input UI; Android and the web enforce both in the validator. Still no work.
- **Quiet hours** — Android seeds `22:00`/`07:00` on toggle-on (`NotificationsGroup.kt:95`), which **matches iOS** (`SettingsView.swift:297-298`). The web seeds `22:00`/`08:00` — a web↔iOS divergence that is not Android's to fix. Android/iOS defer a reminder to the window's end; web suppresses the whole check pass while inside the window — accepted per-client mechanics.
- **Reminder offsets** — the five shared values (15/30/60/120/1440). Android's editor alone *offers* the extra "at time" (0) and 5-minute options; iOS and the web only render an off-list stored value in place rather than snapping it (iOS `reminderOptions(including:)`). The superset is Android's accepted divergence. No change.
- **PocketBase wire model** — no post-July wire changes in either sibling. Android's live-verified client stands.
- **Sync cursor strategy** — the web moved to a `client_updated_at` cursor (its own PB-0.23+ hedging); iOS and Android still use the server `updated` cursor with the 5 s rewind, live-verified. Per-client choice, not a shared contract. No change.

### 1.2 Design tokens

Android's §5.1–§5.3 values match the shipped Inkwell 1.4.0 "GSD Editorial" system: quadrant pigments (`#B23A2E`/`#2C6680`/`#8A6A22`/`#6F685F` light; `#E0705F`/`#6FAACB`/`#CFB266`/`#A9A096` dark) and washes are byte-equal — Android's washes correspond to the web's `--q*-header` tier (`#F4E4E0`/`#E1ECF1`/`#F0E9D8`/`#ECE9E3`; dark `#3A211D`/`#173039`/`#322B17`/`#2A2620`), which is the shared hand-tuned set iOS also carries. Two web-only refinements are **documented divergences, not gaps**: the web adds a subtler `--q*-wash` tier, and derives darkened text inks `--q3-ink #74591C` / `--q4-ink #615B54` (light mode only) for AA text on washes, where iOS and Android use raw pigment for both fill and text. Android follows iOS. Do not "fix" this without an owner decision spanning both native clients.

### 1.3 Deliberate one-sided features (do not port, do not flag)

- Web-only: streaks (`StreakIndicator`).
- iOS/Android-only: bulk multi-select, custom smart views with pinning (the web retired those write paths in #521; its `smartViews`/`appPreferences` stores are data-continuity-only).
- Declared in each repo's `PRODUCT.md`; parity audits skip them. **Consequence for Android:** keep smart views fully alive locally, and round-trip the web's inert stores through backups without semantic interpretation.

---

## 2. Change 1 — Backup envelope 2.1.0 (replaces spec §8.16's envelope)

Android exports `{tasks, exportedAt, version: 1}` and imports only `tasks`. The shared envelope is now a **seven-store, versioned document** (web ADR 0014 + 0015; iOS commit `e5735e8`). The reference example is `gsd-taskmanager/tests/fixtures/cross-platform/ios-backup-export.json` — an envelope iOS actually exported, which the web test suite validates.

### 2.1 Export (authoritative shape)

Top-level keys **sorted** — an Android convention adopted from the iOS exporter's `.sortedKeys`; the web exporter emits insertion order, and key order is not part of the import contract — pretty-printed (2-space indent), dates ISO-8601 fractional-ms `T`/`Z`:

| Key | Content | Rules |
|---|---|---|
| `version` | `"2.1.0"` | **String**, not int |
| `exportedAt` | ISO-8601 ms | |
| `tasks` | full camelCase Task records | **plus `"quadrant"`: the derived §7.8 id — backups now carry it** (import still ignores it); rich `timeEntries` (`{id, startedAt, endedAt?, notes?}`), null optionals omitted |
| `archivedTasks` | Task records + `"archivedAt"` (ISO) | flat extension, same record shape, quadrant written |
| `deletedTasks` | Task records + `"deletedAt"` (ISO) | flat extension (trash — §3) |
| `smartViews` | **custom views only** (`isBuiltIn: false`) | `{id, name, icon, criteria, isBuiltIn, createdAt, updatedAt}`; `criteria` is the shared camelCase FilterCriteria — Android's `FilterCriteria` serialization is already compatible. Web views may also carry an optional `description`; iOS drops it on round-trip and Android does the same — a documented loss, not a bug |
| `notificationSettings` | singleton | `{"id": "settings", enabled, defaultReminder, soundEnabled, quietHoursStart?, quietHoursEnd?, permissionAsked, updatedAt}` — `id` is the **string literal** `"settings"`, quiet-hours keys omitted when off, `updatedAt` ISO |
| `archiveSettings` | singleton | `{"id": "settings", enabled, archiveAfterDays}` — `archiveAfterDays` ∈ {30, 60, 90} |
| `appPreferences` | singleton | `{"id": "preferences", pinnedSmartViewIds, maxPinnedViews: 5, smartViewsEnabled: true}` — the web schema requires the last two *keys* (any int ≥ 0 / any boolean); the values 5/`true` are exporter convention (iOS derives 5 from `SmartViewPinning.maxPins`), and both siblings ignore them on import |

### 2.2 Import (lenient, version-tolerant)

- **Order of guards:** 10 MB UTF-8 byte cap first → structural decode → record-count cap → per-record lenient decode.
- **The count cap counts all three task-bearing arrays:** `tasks + archivedTasks + deletedTasks ≤ 10,000` (`smartViews` and singletons are not counted). This closes the same hole web #520 and iOS #14 closed.
- `version`: accept a string **or** a number, normalize to string; **absent is accepted** and treated as `"1"` (iOS-style leniency; a v1 Android/iOS export must keep importing).
- Every store key beyond `tasks` is **optional**. **Absent key ≠ empty array**: absent means "this backup says nothing about that store" and leaves the local store untouched — in *both* modes; an empty array *present* in the payload clears that store in replace mode.
- Incoming `quadrant` values are ignored and recomputed from the flags (Android's lenient `TaskJson` already does this by ignoring unknown keys).
- Per-record failures skip-and-count, never abort — a declared **Android leniency choice in the iOS style, not a web mirror**: the web is all-or-nothing here (its zod `importPayloadSchema` rejects the entire payload on any invalid record, including an unparseable `archivedAt`/`deletedAt`). Archived/deleted records use the same lenient task decode plus their optional timestamp; a record with an unparseable extra timestamp imports with the timestamp defaulted to now rather than being dropped — one corrupt row never blocks a 10,000-row restore.
- **Store scopes by mode** (mirrors web `lib/tasks/import-export.ts`):

| Store | Replace | Merge |
|---|---|---|
| `tasks` | existing behavior stands (stamp `updatedAt`, enqueue `update` per import + `delete` per missing id, atomic swap) | existing behavior stands (colliding ids regenerate, deps + `parentTaskId` remap) |
| `archivedTasks` | clear then insert | insert; **on id collision the existing local row wins** (skip) — no remap. Skip any id also present in the imported or local active `tasks` ("an id lives in one store, never both") |
| `deletedTasks` | clear then insert | insert; existing wins on collision — Android extends the archive rule; the web currently *overwrites* the local trash row on a collision (bare `put`, no existing-row check) — an asymmetry to fix upstream, not to copy; skip ids present in active or archived stores |
| `smartViews` | clear **custom** views then insert | upsert by id. Never write a payload row with `isBuiltIn: true` — an Android guard (built-ins are derived here, never stored); the web ships no such filter and relies on never exporting one |
| three singletons | put when present | **not applied** — merge combines task lists and never adopts another device's configuration (the web's deliberate rule: `import-export.ts` applies settings in replace mode only) |
| **Sync** | archived/deleted/smart-view/singleton imports enqueue **nothing** (those stores never sync) | same |

### 2.3 The cross-platform fixture pair (adopt, byte-identical)

Copy `gsd-taskmanager/tests/fixtures/cross-platform/ios-backup-export.json` and `web-backup-2.1.0.json` into `core/model/src/test/resources/fixtures/cross-platform/` **byte-identical** and run both through import in the JVM suite (decode, validate counts, re-export, re-import round-trip). This is the same discipline the sibling repos use: same-client round-trip tests are a mirror, not a check. If Android's export shape ever changes, regenerate an Android fixture and add it to **both** sibling repos' fixture directories in the same change.

---

## 3. Change 2 — Trash (new feature; amends spec §8.20)

Web (#484, ADR 0015, Dexie v16) and iOS (migration v6, `e5735e8`) made deletion recoverable. The store invariant is now **three-state**: a task id lives in exactly one of `tasks`, `archived_tasks`, `deleted_tasks`.

### 3.1 Behavior (authoritative; per-client divergences labeled inline)

- **Delete → trash.** Deleting an active task moves the full record to the trash store with `deletedAt = now`. The remote **`delete` is enqueued *before* the local move, in the same transaction; if the enqueue fails the delete aborts** (the enqueue-before-write invariant, extended).
- **Retention: 30 days.** Android follows **iOS's cutoff**: start-of-day (local) 30 days back (`TrashRetention.swift`); the web uses a rolling `now − 30 d` instant — accepted per-client mechanics, same category as the quiet-hours split. What *is* shared: purge deletes strictly older than the cutoff, so a row whose `deletedAt` sits exactly on it is **kept** (both siblings use strict `<`). Purge runs on launch and on the periodic maintenance sweep. Purge enqueues nothing.
- **Restore** re-activates the record **with its original timestamps preserved** — a faithful undo, matching both siblings (web `lib/tasks/crud/restore.ts` documents "preserves the original id, completed state, and timestamps"; iOS `TaskStore.restoreFromTrash` re-saves the row verbatim). It **enqueues `create`** — safe because the remote record was already deleted at trash time, so there is no LWW contest. Reminders for the restored task are rescheduled. *(Correction: an earlier draft specified a fresh `updatedAt = now`; neither sibling does that.)*
- **Delete forever** (single row) and **Empty trash** enqueue nothing — the remote record was already deleted when the task entered the trash.
- **Trash contents never sync** (like the archive). A remote delete arriving via SSE/reconcile removes the local active row directly, **without** a local trash entry — only the deleting device holds the recoverable copy.
- **Erase All Data clears the trash** (local erase and erase-everywhere both) — matches iOS, whose erase path empties the trash with the comment that erase "must mean all of it". The web currently does **not**: `lib/reset-everything.ts` omits `deletedTasks` (and `appPreferences`) from its clear list, leaving recoverable task copies behind after "erase all data" — a web bug to fix upstream, not behavior to copy.
- **Import/export:** trash rows travel as `deletedTasks` (§2); imported trash rows enqueue nothing.
- **Archive interplay:** archiving stays unchanged (a *separate* store; archive is for completed history, trash for deletions). Restoring from trash returns to *active*, never to archive.

### 3.2 Android reimagining

- **Undo snackbar stays** and becomes a *view over trash*: delete shows `Deleted "<title>"` + Undo (~6 s; ~12 s under TalkBack) exactly as today, but Undo now calls trash-restore instead of re-creating from an in-memory snapshot. Nothing is lost when the snackbar times out anymore.
- **Trash screen** in Browse, as a sibling row below Archive (icon `delete_outline`, live count): dimmed read-only rows showing time remaining ("29 days left", `ink3`; final day in `alert`), swipe to Restore (leading) / Delete forever (trailing, confirmed "This can't be undone."), multi-select bulk bar (Restore / Delete forever), toolbar "Empty trash" (confirmed). Deep link `gsd://trash`. Empty state: "Trash is empty" with the §5.10 treatment.
- **Room migration v1 → v2** creates `deleted_tasks` (same columns as `archived_tasks` with `deletedAt` instead of `archivedAt`, indexed on `deletedAt`). First real migration — write the instrumented migration test.

## 4. Change 3 — Sync tombstone suppression (amends spec §9.4/§9.6)

Android's pull and SSE apply paths know nothing about the archive (or the new trash), so a remote record for a locally-archived task resurrects as an active duplicate. The web guards both apply paths with one shared rule (`isRemoteNewerThanArchive`, `lib/sync/pb-sync-helpers.ts`); Android must adopt it for **both** tombstone stores:

- On pull/SSE upsert of a record whose `task_id` is in `archived_tasks` or `deleted_tasks`: compare remote `client_updated_at` against the row's `archivedAt`/`deletedAt` **as millisecond instants**.
  - Remote **strictly newer** → the remote edit wins: remove the tombstone row and apply the record as an active upsert (this is deliberate cross-device resurrection — the user edited it elsewhere after archiving/deleting here).
  - Otherwise (older, equal, either timestamp missing or unparseable) → **drop the record. Fail closed: never resurrect on bad data.**
- Deletion reconciliation (§9.4) continues to ignore both tombstone stores.
- SSE delete events and reconcile-deletes keep their current semantics (local hard delete of the active row; no trash entry — §3.1).
- **Sibling status:** this guard is **web-only today** — iOS's pull/SSE paths consult only the active table and resurrect archived/trashed ids unconditionally (`SyncEngine.swift` scopes the archive out explicitly), so once Android lands this, iOS is the remaining client with the hole (sibling backlog item, not Android's to fix). One nuance: the web clamps remote `client_updated_at` for clock skew before comparing; Android's existing parse-or-drop wire handling is the accepted equivalent.

## 5. Change 4 — Capture parser corpus alignment (amends spec §8.2 + Appendix C)

The 40-case corpus (`capture-parser-corpus.json`, byte-identical across `gsd-taskmanager`, `gsd-iosapp`, and the MCP server) is now **the** parser contract — it supersedes Appendix C's nine vectors. Adopt it byte-identically at `core/model/src/test/resources/fixtures/cross-platform/capture-parser-corpus.json` with a corpus-driven test (`{name, input, expect: {title, urgent, important, tags, urls}}` per case) and change `CaptureParser`/`UrlSanitizer` until all 40 pass. The settled decisions Android's July parser predates (from `gsd-iosapp/docs/2026-08-28-capture-parser-parity.md` and web #524):

1. **Unicode tags** — tag charset is Unicode word characters (letters/marks/digits/underscore to start, `-` allowed as continuation), not ASCII-only. Leading-hyphen tags do not parse as tags.
2. **Tag dedupe is case-insensitive**; tags store lowercase (unchanged).
3. **Tag cap 20 by truncation** — excess tags drop; the capture is *not* rejected.
4. **Over-length tags (>30) drop but still strip** from the title.
5. **Duplicate URLs collapse to one** (first occurrence).
6. **Repeated flag tokens** (`!! !!`, `! !`) all strip in one pass.
7. **URL extraction is boundary-anchored** (the web's behavior won): a URL token is recognized at word boundaries, not greedily mid-word.
8. **Raw URL storage** (iOS's behavior won): store the trimmed original string; never WHATWG-normalize.
9. **Length guard is `>= 2048`** (Android already correct).

Where the list above and the corpus disagree, **the corpus wins** — implement to the fixtures.

## 6. Change 5 — Feedback (new feature; adds a Settings surface)

Anonymous, opt-in product feedback — web #516, iOS #15, divergence notes in `gsd-iosapp/docs/2026-08-29-feedback-parity.md`. Nothing here reads the task database, sync config, or auth store.

### 6.1 Wire contract (exact)

- `POST https://api.vinny.io/api/collections/feedback/records`, `Content-Type: application/json`, **no Authorization header, no cookies, no identifiers beyond the payload**.
- Payload: **exactly these 7 snake_case keys**, pinned by an exact-keys test (extra key = test failure):

| Key | Rule |
|---|---|
| `submission_id` | 1–64 chars; a fresh nanoid per draft, **stable across retries of the same draft** (server dedup key) |
| `sentiment` | `"up"` \| `"down"` \| `""` |
| `category` | `"idea"` \| `"praise"` \| `"gripe"` \| `"bug"` \| `""` |
| `message` | trimmed, ≤ 1000 chars |
| `votes` | deduped array of roadmap slugs, filtered to this build's list, max = list size |
| `app_version` | `"android-<versionName>.<versionCode>"`, clamped to 20 chars by truncation (iOS-style `prefix(20)`; the web instead *rejects* >20 — clamping is the send-always choice) |
| `client_submitted_at` | ISO-8601, non-empty, stamped at send |

- **Response handling:** 2xx = sent. A 400 whose parsed body has `data.submission_id.code == "validation_not_unique"` (duplicate `submission_id` — the structured check both siblings ship, not a substring scan) **collapses to success** — the earlier attempt landed. Anything else keeps the draft and offers retry.
- **Roadmap slugs are per-client curated; never reuse a retired slug.** Web ships 8 (`natural-language-dates, calendar-sync, task-templates, weekly-review, focus-timer, task-notes, ios-widgets, shared-lists`); iOS retires `ios-widgets` and adds `apple-watch`. **Android ships the 7 shared slugs + the additive `wear-os`** ("Wear OS app" — polling demand for the current anti-goal, exactly as iOS polls `apple-watch`): `natural-language-dates, calendar-sync, task-templates, weekly-review, focus-timer, task-notes, shared-lists, wear-os`. Owner may re-curate; the list is a hardcoded constant (offline-safe, write-only collection).

### 6.2 Android reimagining

- Settings gains a **Feedback** row (between Data & Storage and About): sentiment toggle (up/down), category chips, message field (≤1000, live count near limit), roadmap vote checklist, and — non-negotiable disclosure parity — a **"What gets sent" preview rendering the literal payload object**, built by the same pure builder that produces the request body, so preview and payload cannot drift.
- Draft (sentiment/category/message/votes + the draft's `submission_id`) persists locally in DataStore; cleared on success. Send disabled while the draft is empty (no sentiment, no category, no trimmed text, no votes).
- Send confirmation is calm ("Thanks — sent."); failures keep the draft with a retry affordance. All strings in `strings.xml`.

## 7. Change 6 — Apple sign-in account note (amends spec §8.17/§10.4)

iOS widened the warning on 2026-08-28 (`a80ee43`): warning only on relay addresses under-warned. Adopt the two-tier note, shown under the Account row whenever the session's provider is Apple:

- Relay address (`…@privaterelay.appleid.com`): **"Signed in with a private relay email — this is a separate account from your web tasks."**
- Any other Apple sign-in: **"Signed in with Apple. If you use Google or GitHub on the web, that’s a different account and won’t show the same tasks."** (typographic U+2019 apostrophes — the string above is byte-faithful to iOS `SettingsView.swift:243`; copy from here or from that file, never re-type).

Also **correct spec §10.4's factual claim**: per the pinned PocketBase v0.39.10 source (`apis/record_auth_with_oauth2.go`), email-based auto-linking is built-in and unconditional — externalAuth match first, else verified-email match, else create. Distinct accounts arise only when verified emails genuinely differ, chiefly Apple private relay (verified but unique per app). The shipped iOS copy deliberately over-warns for the same-email case; mirror it anyway — parity of caution is the owner's call, already made.

## 8. Amendments to `docs/gsd-android-spec.md` (folded in by the plan's final task, after code is green)

§4.4 + §8.16 (envelope 2.1.0, seven stores, three-array cap) · §7.10 (add `deleted_tasks`, Room v2) · §8.20 (undo = trash restore; add the Trash surface) · §9.4/§9.6 (tombstone suppression rule) · §8.2 + Appendix C (corpus supersedes the vectors; the nine decisions) · §8.17 (Feedback row; two-tier Apple note) · §10.4 (auto-linking correction) · §6.4 (`gsd://trash` route) · Appendix B (feedback limits; envelope cap wording).

## 9. Out of scope

- Wire-model, sync-cursor, or OAuth-flow changes (none happened).
- Web's `--q3-ink`/`--q4-ink` text-ink derivation (documented divergence, owner decision).
- The dashboard "verdict" framing the web added in #519 (presentation-only; ranked below Do-first in the audit and not adopted by iOS either).
- Quiet-hours seed change (Android matches iOS; the web is the outlier).
- Play-submission owner steps (tracked in `docs/play/submission-checklist.md`, unaffected).

## 10. Acceptance criteria (summary — each maps to plan tasks)

1. Both cross-platform backup fixtures import losslessly on Android; an Android export validates against the web's `importPayloadSchema` (seven stores, string version, quadrant present) and follows the iOS sorted-keys convention.
2. Import cap rejects `tasks 4000 + archived 3001 + deleted 3000` and admits 10,000 total.
3. Deleting a task lands it in trash with a queued remote delete; restore within 30 days round-trips through sync as a create; purge removes only rows strictly older than the start-of-day cutoff; erase clears trash.
4. A pulled/SSE record for an archived-or-trashed id is dropped unless strictly newer than the tombstone, in which case it resurrects exactly once.
5. All 40 corpus cases pass against the byte-identical fixture.
6. The feedback payload test pins exactly 7 keys; the duplicate-400 collapses to success; the preview renders the same object that is sent.
7. Apple sign-ins always show the appropriate account note tier.
8. `./gradlew :core:model:test` stays sub-second; full `testDebugUnitTest` and instrumented migration/UI suites green; release build assembles.

*— end of parity specification —*
