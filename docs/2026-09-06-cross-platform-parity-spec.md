# GSD Android — Cross-Platform Parity Specification (September 2026 round)

> **Status:** approved design 2026-09-06 (owner-approved scope: all four #527 sync rules, skeletons on Dashboard + smart-view lists + Matrix, both feedback polish items). Implementation plan: `tasks/parity-plan-2026-09-06.md`. Landed 2026-09-06 on branch feat/parity-2026-09 (commits 585a125..4a3d074); the §9 amendments are folded into docs/gsd-android-spec.md, which is the spec of record again — this document remains the dated record of why.
>
> **Precedence:** this document wins over `docs/gsd-android-spec.md` for every section it touches until the plan's final task folds the amendments back in (§9). Everything it does not touch stays governed by the main spec and by `docs/2026-08-29-cross-platform-parity-spec.md`.

## 0. Authority map (what changed where, since the August catch-up)

The August round (`docs/2026-08-29-cross-platform-parity-spec.md`, all twelve plan tasks landed 2026-08-29) brought Android level with the web and iOS as of that date. This round covers what the siblings shipped **after** it, audited claim-by-claim against the current trees on 2026-09-06.

| Source | Commit | What it is | Origin |
|---|---|---|---|
| iOS #16 | `18dd222` (2026-09-01) | Completion-by-quadrant rings, day-based `DeadlineStatus` + "Today" badge, Browse root search, first-frame skeletons | **iOS-originated** (adapted from ShipSwift, MIT); not a web port |
| iOS #12 / web #520 §2e | `715c28a` / `65f15b6` (2026-08-28) | Reminder picker renders an off-list stored `notifyBefore` instead of snapping it | Web ↔ iOS parity; the August audit under-read Android's editor |
| web #525 | `af0bbff` (2026-09-02) | Returning-user feedback nudge on the Review page | **Web-only by decision** (commit body: "iOS keeps Settings and the palette as its only surfaces") |
| web #527 | `dbc968b` (2026-09-04) | Security review: smart-view bounds in the backup schema, feedback abuse hook, transactional account-deletion route, bounded remote scans, three-verdict remote-absence rule, malformed-version push guard, erase completeness | Server + client contract hardening; iOS has not adopted any of it yet |

Primary sources, for anyone re-verifying a rule:

- iOS: `GSDKit/Sources/GSDModel/DeadlineStatus.swift`, `AnalyticsEngine.swift:43-75`, `AnalyticsSummary.swift:26`, `TaskFilter.swift:12-58`, `NotificationSettings.swift:15-24`, `GSDStore/TaskStore.swift:72,146-150`; `App/Dashboard/QuadrantCompletionRings.swift`, `DashboardView.swift:35-50,286-329`, `App/Support/StatusBadge.swift`, `TaskListSkeleton.swift`, `App/Effects/SkeletonShimmer.swift`, `App/Browse/SmartViewListView.swift:15-32,193-241`, `App/Editor/TaskEditorView.swift:391-451`.
- Web: `lib/constants/schema.ts:41-74`, `lib/schema.ts:143-229`, `lib/tasks/import-export.ts:108-146,357-`, `lib/sync/pb-sync-helpers.ts` (`fetchBoundedRemoteTasks`), `lib/sync/queue.ts` (`classifyRemoteDeletion`), `lib/sync/pb-push.ts:73-112`, `lib/sync/pb-account-deletion.ts`, `lib/reset-everything.ts`, `docker/pb_hooks/account_lifecycle.pb.js`, `docker/pb_hooks/feedback_controls.pb.js`.

## 1. Verified parity — explicitly **no work**

Audited 2026-09-06 so future sessions do not re-litigate them.

### 1.1 Still matching after the August round

- **Fixtures.** `capture-parser-corpus.json`, `ios-backup-export.json`, `web-backup-2.1.0.json` are byte-identical in all three repos (`diff -q` silent); the web's fixture directory has no commits since 2026-08-29.
- **Shared-contract sources.** No post-August change to the web's `lib/feedback/feedback-payload.ts`, `lib/capture-parser.ts`, `lib/sync/pb-auth.ts`, `app/globals.css`, nor to iOS's `TaskExport.swift`, `Validation.swift`, `Feedback.swift`, `CaptureParser.swift`, `AuthService.swift`, `Theme.swift`, `QuadrantStyle.swift`, `spec.md`. The only shared-contract edits are the two web schema files in #527 (§7 below).
- **Backup interop** (web #520 §2a–2c, iOS #14): numeric `version`, derived `quadrant`, three-array import cap — all landed on Android 2026-08-29.
- **Feedback contract** (web #516, iOS #15), **capture-parser corpus** (web #524, iOS #11), **Apple sign-in note** (web #520 §2d): landed 2026-08-29.
- **Quiet hours + sound** (web #522): the web only added UI for behavior Android already ships; Android seeds `22:00`/`07:00` like iOS, the web's `08:00` remains the outlier. No change.
- **Task-create owner check** (web #527 §5a, `account_lifecycle.pb.js`): the server now rejects a task whose `owner` does not resolve. Android writes `owner` = the JWT's user id on every push (`WireMapper.push`). No change.

### 1.2 Deliberate one-sided features (do not port, do not flag)

- **Web-only:** streaks; the Review-page **verdict + stat rail** (#519 — ruled out in August, still not adopted by iOS); the **returning-user feedback nudge** (#525 — declared web-only; this round records the absence in the Android spec, §6.4).
- **iOS/Android-only:** bulk multi-select; custom smart views with pinning.
- **Web mechanisms Android does not mirror:** the sign-in ownership guard over three stores (#527 §5f — Android detects an account switch from the persisted `lastOwnerId`, not by scanning rows); the Sentry-safe-key trim (#527 §5i — Android has no crash reporting); the pre-parse JSON structural walker for smart views (#527 §5g — a JS-runtime defense; Android's 10 MB UTF-8 byte cap runs before the kotlinx parser and is the equivalent guard); the web's read-path "built-ins only when > 100 stored" (unreachable once the import cap holds).
- **iOS motion polish not ported:** the stat-card digit morph (`contentTransition(.numericText())`). No behavior.
- **iOS scoping not mirrored:** iOS's Browse search is iPhone-only because iPad/Catalyst replace the Browse tab with a sidebar. Android's Browse screen is one composable at every width (bottom bar, rail, and drawer all route to it), so the search applies everywhere.

### 1.3 Pre-existing Android gaps this audit surfaced

These are not new sibling features; they are places where the iOS or web rule exposed an Android divergence from its own spec. They are folded into the changes below rather than tracked separately.

| Gap | Where | Fixed by |
|---|---|---|
| Dashboard "upcoming" is instant-based (`!due.isBefore(now)`), so a task due earlier today vanishes from both upcoming and overdue | `core/model/.../Analytics.kt:38-41` | §2.1 |
| Upcoming rows are inert titles; §8.15 already says "tap → editor" and the siblings show a date | `app/.../dashboard/DashboardScreen.kt:147-155` | §2.3 |
| Completion-rate stat truncates (`.toInt()`) where both siblings round | `DashboardScreen.kt:103` | §2.1 |
| Editor shows **"None"** for an off-list stored reminder (45, 90, 2880, or anything synced) | `app/.../editor/EditorDepthSections.kt:100-101` | §5 |
| Push overwrites the remote record when its `client_updated_at` is unparseable (`remoteStamp == null` falls through to `updateTask`) | `core/sync/.../SyncEngine.kt:157-166` | §8.2 |
| Palette has no "Send feedback" action; both siblings have one | `app/.../palette/PaletteIndex.kt:6-10` | §6.1 |
| Erase All Data leaves the feedback draft and its submission id in DataStore | `store/LocalErase.kt`, `settings/SettingsScreen.kt:223` | §6.3 |
| Smart-view rows dropped on import are not counted in `skipped` | `Interchange.kt:128` | §7.2 |

---

## 2. Change 1 — Dashboard: per-quadrant completion, day-based deadlines, live upcoming rows

Amends §8.15 (and the §5.10 card vocabulary). Source: iOS #16.

### 2.1 Analytics additions (`:core:model`, pure)

**`QuadrantStat`** — `data class QuadrantStat(val quadrant: Quadrant, val total: Int, val completed: Int)` with `active = total - completed` and `completionRate: Float = if (total == 0) 0f else completed.toFloat() / total`. `DashboardStats.quadrantStats: List<QuadrantStat>` is **always four entries, Q1→Q4** (`Quadrant.entries` order). Input is the active table only — archived and trashed rows never reach `Analytics.compute` because `TaskStore.observeTasks()` reads `tasks`; that exclusion is by construction, same as iOS. **All-time**, no window: `total` counts every row in the quadrant, active or completed.

**`DeadlineStatus`** — `enum class DeadlineStatus { OVERDUE, TODAY, UPCOMING }` with `fun of(due: Instant, now: Instant, zone: ZoneId)`:

```
dueDay = due.atZone(zone).toLocalDate(); today = now.atZone(zone).toLocalDate()
dueDay < today  → OVERDUE
dueDay == today → TODAY
else            → UPCOMING
```

Day-based in the injected zone so it agrees with `overdueCount`, `FilterCriteria.overdue`/`dueToday`, and `TaskCard.dueLabel`, all of which already compare `LocalDate`s. Only three states — no "tomorrow", no "this week" (those stay the card's `dueLabel` vocabulary).

**Upcoming list fix** — `upcoming` = active tasks with a due date whose `dueDay >= today` (was `!due.isBefore(now)`), sorted by `dueDate` ascending, **tie-break `createdAt` descending** (iOS has no explicit tie-break; Android adds one so the order is deterministic), `take(5)`. Overdue tasks stay out of this list; the overdue banner owns them.

**Percent formatting** — one helper `Analytics.percent(rate: Float): String = "${(rate * 100f).roundToInt()}%"` (round half up; iOS uses `Double.rounded()` — half away from zero — identical for non-negative rates). Used by the stat card (replacing the truncating `.toInt()`), the rings center, and the legend.

**Single clock and zone.** Every date rule on the Dashboard is computed from the one `now`/`zone` pair passed into `Analytics.compute` — the badge is derived from `DashboardStats`, never from a second `Instant.now()` in the composable (iOS's two-calendar caveat, closed by construction).

### 2.2 "Completion by Quadrant" rings card (`:app`)

Position: after the quadrant-distribution bars, before Top tags (iOS order). Card chrome: `surface` fill, 1dp `hairline` border, `GsdShapes.card` (22dp), 18dp padding — the StatCard treatment at card radius. Title "Completion by Quadrant" in `GsdType.quadrantTitle`, `ink`.

Geometry (dp, iOS's points): outer size **196**, ring width **13**, gap **5**; ring `i` (0 = Q1, outermost) diameter = `196 − i × 36` → 196, 160, 124, 88. Each ring: a full-circle track stroked in the quadrant **wash**, then a value arc in the quadrant **accent**, round caps, starting at 12 o'clock and sweeping clockwise by `completionRate × growth × 360°`. Drawn on one `Canvas`.

Center: the overall rate via `Analytics.percent(stats.completionRate)` in `GsdType.title` (Newsreader, `tnum`), `ink`, single line, auto-shrink to fit the ~67dp hole; beneath it the literal lowercase "complete" in `GsdType.caption`, `ink3`.

Legend: a 2×2 grid under the rings, 8dp spacing; each cell = 11dp square swatch (3dp radius) in the accent, quadrant title (`GsdType.subhead`, `ink2`), spacer, value (`GsdType.subhead` semibold, `tnum`, `ink`). **Value is an em dash `—` (U+2014) when `total == 0`**, never "0%". The ring itself still draws its track.

Motion: `growth` animates 0→1 with an ease-out over **900 ms** on first composition. When `ValueAnimator.areAnimatorsEnabled()` is false (the same gate `ConfettiOverlay` uses), `growth` is 1 from the first frame — no grow-in.

Accessibility: the rings are **one** semantics node, `contentDescription = "Completion rings, {P}% of all tasks complete"`; each legend cell is its own node, `"{Quadrant title}: {P}% complete"` or `"{Quadrant title}: no tasks"` when `total == 0`. Strings in `strings.xml`.

### 2.3 Upcoming-deadline rows

Each row: title (`GsdType.subhead`, `ink`, wraps) · badge · date (`GsdType.metadata`, `ink3`, single line, never wraps — the title yields). Date via `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)` in the device zone (iOS's `.date` style). Rows are separated by 1dp `hairline` dividers, ≥48dp tall, and **tapping opens the task editor** (a `TaskEditorSheet` hosted by the Dashboard, as `SmartViewListScreen` already does).

Badge by `DeadlineStatus.of(task.dueDate, now, zone)` — the same `now`/`zone` as the stats:

| Status | Badge |
|---|---|
| `TODAY` | "Today" — neutral capsule: `ink2` text on `sunken` fill |
| `OVERDUE` | "Overdue" — alert capsule: `alert` text on `alertWash`. Kept so the `when` is exhaustive; not a live path because `upcoming` starts at today |
| `UPCOMING` | no badge — the date alone says enough |

Capsule anatomy (`StatusBadge` composable, reusable): `GsdType.caption` semibold, single line, padding 8dp × 3dp, pill shape. Two styles only, by design — "color is rationed, so only overdue earns the alert pigment."

Completed tasks never appear here (`upcoming` is active-only), so they never get a badge. The card is hidden when the list is empty (unchanged).

---

## 3. Change 2 — Browse root search

Amends §6.1, §8.13, §8.14. Source: iOS #16.

### 3.1 Surface

A search field at the top of the Browse screen, above the Archive row, placeholder **"Search all tasks"** (`browse_search_hint`), `testTag("browse_search")`, using the same plain-field treatment as the in-view and archive searches (`GsdType.subhead`, `ink3` hint). Applies at **every width** (§1.2).

Swap rule: `query = text.trim()`. Empty → the normal Browse list (Archive, Trash, Pinned, Built-in, Custom, New Smart View). Non-empty → a results list **replaces the whole Browse list**. No debounce, no minimum length, no throttle — one non-whitespace character produces results, recomputed on every keystroke. Whitespace-only is empty. Nothing is persisted; the field clears when the screen leaves composition.

### 3.2 Query semantics (exact)

Results = `FilterCriteria(status = ACTIVE, searchQuery = query)` matched against the full active task set — i.e. the existing `FilterCriteria.matches` rule: **title, description, every tag, every subtask title**; both sides lowercased; the whole trimmed query is one substring (no tokenization — `"foo bar"` matches only a literal `"foo bar"` run inside one field); **no diacritic folding** (`e` does not match `é`); completed tasks are never returned. No result cap.

One model change: `FilterCriteria.matches` **trims** `searchQuery` before matching, and a whitespace-only query matches everything (iOS `TaskFilter.swift:12` and the web's `matrix-view.ts` both trim). Pinned by a `FilterCriteriaTest` case. The in-view search benefits from the same rule.

### 3.3 Ordering (shared with smart-view lists)

Android's smart-view list currently renders in store order with no defined sort; iOS sorts every filtered list. This round adopts iOS's `TaskFilter` ordering as `TaskOrdering.forList(tasks, status)` in `:core:model` and applies it to **both** Browse search results and `ViewListContent`:

- `status == COMPLETED` → `completedAt` descending, nulls last; ties by `createdAt` descending.
- otherwise → `dueDate` ascending, **dated tasks before undated**; ties (same due date, or both undated) by `createdAt` descending (newest first).

### 3.4 Rows, actions, empty state

Rows are the same composable and `TaskRowActions` a smart-view list uses (tap → editor sheet; completion disc → toggle with the usual confetti; blocked/blocking badges computed over the **full** task set, not the filtered one).

Empty state (§5.10 anatomy — 60dp `sunken` tile, 28dp search icon in `ink3`, serif headline, one `ink2` sentence): title **`No tasks match "{query}"`** (straight double quotes, query interpolated), message **"Try a different word, tag, or quadrant."** The in-view search miss (`view_empty_search`, currently "No tasks match your search") adopts the **same two strings** — iOS renders them byte-identically in both places.

Search results show no skeleton (they exist only after the task set has loaded).

---

## 4. Change 3 — First-frame skeletons

Amends §5.10, §8.1, §8.13, §8.15, §13.3. Source: iOS #16 (Dashboard + smart-view list) and the web's `matrix-grid-skeleton.tsx` (Matrix; owner-approved extension beyond iOS, which deferred it).

### 4.1 The loaded flag

"Loaded" means the task observer has delivered its **first snapshot, even an empty one**. Before that, `tasks` is indistinguishable from an empty board, which is why cold starts flash a false empty state (Dashboard "No stats yet", a view's "Nothing here", the Matrix's four dashed prompts).

Android encoding: the gating surfaces hold `List<…>?` state whose initial value is **`null`** until Room's first emission — `MatrixViewModel.sections: StateFlow<List<QuadrantSectionState>?>` (initial `null`), `BrowseViewModel.tasks: StateFlow<List<Task>?>` (initial `null`, consumed by `SmartViewListScreen`), and `DashboardScreen` collecting `observeTasks()` with `initialValue = null`. The flag never resets within a ViewModel's lifetime (iOS: `hasLoadedTasks` never resets). No timer, **no minimum display time** — if the first snapshot lands within a frame the skeleton is never visibly drawn; its job is removing the false empty state, not showing a loader.

Pinned by an instrumented `:core:store` test: `observeTasks()` on an empty database emits an empty list as its first value (so the gate flips even with no rows).

### 4.2 Shared primitive (`app/.../ui/Skeleton.kt`)

- `SkeletonBlock(height, width = null, shape = GsdShapes.small)` — a `sunken`-filled rounded rectangle; `width == null` fills the available width.
- `Modifier.skeletonShimmer()` — one light band sweeping the **whole composition** left→right: width = 50 % of the container, a horizontal gradient `transparent → surface @ 75 % → transparent` (the raised `surface` tone, not white: "a white band reads as glare on warm paper and vanishes in dark mode"), **1600 ms** linear sweep then **600 ms** pause, repeating; masked to the placeholder shapes so it never streaks across gaps (draw with `CompositingStrategy.Offscreen` + `BlendMode.SrcIn`); starts after a ~100 ms grace so layout has settled; ignores touch. **When `ValueAnimator.areAnimatorsEnabled()` is false the band is not drawn at all** — the skeleton is a static silhouette.
- Every skeleton is **one** semantics node (`clearAndSetSemantics`) with `contentDescription = "Loading"` (`skeleton_loading`).

### 4.3 Where they show, and their silhouettes

Gate order on each surface: `null` → skeleton; loaded-and-empty → the existing empty state; else content.

- **Dashboard** (`tasks == null`): 20dp padding, 18dp spacing: a 2-column grid of **four 86dp** blocks (`GsdShapes.input`), then a **270dp** block and a **320dp** block (`GsdShapes.card`).
- **Smart-view list** (`ViewListContent`, `allTasks == null`): six rows in a `surface` container (`GsdShapes.small`, 16dp side margins, 20dp top): each row = 22dp `sunken` circle, then two bars — 14dp tall × **210dp on even rows / 150dp on odd**, and 10dp × 96dp — row padding 16dp × 14dp, 1dp `hairline` dividers inset 50dp from the left.
- **Matrix** (`sections == null`): the capture bar stays live (it does not depend on tasks); below it four quadrant-section silhouettes in Q1→Q4 order, each a header (26dp icon tile, 120×16dp title bar, 24×12dp count bar) and card silhouettes — **3, 2, 2, 1** cards per quadrant, the web's deliberately uneven counts ("four identical columns read as a loading pattern rather than a preview") — each card a 3dp accent-colored spine plus a 14dp title bar and a 10dp metadata bar on a `surface` tile. At expanded width the same silhouettes fill the 2×2 grid cells.

Browse search results, Archive, Trash, and Settings get no skeleton (parity with iOS; the web's Archive has none either).

---

## 5. Change 4 — Off-list reminder rendering

Amends §8.3. Source: iOS #12 (`NotificationSettings.reminderOptions(including:)` + `TaskEditorView` label formula) and web #520 §2e (`offListLabel`). Spec text both siblings carry: *"An off-list stored `notifyBefore` … renders as its own option rather than snapping — both clients honor this, so editing never silently rewrites the value."*

**Android keeps its superset of presets** — `None, 0, 5, 15, 30, 60, 120, 1440` — the divergence accepted in August. What changes is the off-list rule:

- `ReminderOptions.including(stored: Int?): List<Int>` in `:core:model`: presets `[0, 5, 15, 30, 60, 120, 1440]`; `stored == null` or already a preset → the presets; otherwise the presets **plus `stored`, sorted ascending**. A negative stored value is treated as off-list too (rendered, never snapped — the validator, not the picker, owns range rules).
- The picker offers "None" first, then one row per value from `including(draft.notifyBefore)`. The current label is the row matching `draft.notifyBefore`; `null` → "None". Opening and saving a task without touching the picker leaves `notifyBefore` byte-for-byte as stored.
- Labels come from one pure formula, `ReminderLabel.of(minutes)`, mapped to plural resources in `:app` (iOS's rule, exact and in this order):

```
0                              → "At time of event"
1                              → "1 minute before"
< 60, or minutes % 60 != 0     → "{n} minutes before"
< 1440                         → "{n/60} hour(s) before"      (1 → "1 hour before")
minutes % 1440 != 0            → "{n/60} hours before"
else                           → "{n/1440} day(s) before"     (1 → "1 day before")
```

Worked: 5 → "5 minutes before"; 45 → "45 minutes before"; 60 → "1 hour before"; 90 → "90 minutes before" (not "1.5 hours"); 120 → "2 hours before"; 1440 → "1 day before"; 2160 → "36 hours before"; 2880 → "2 days before". The seven fixed `reminder_*` strings collapse into `reminder_at_time`, plural `reminder_minutes_before`, plural `reminder_hours_before`, plural `reminder_days_before`.

Unchanged: Android's "None" writes `notifyBefore = null` and does not touch `notificationEnabled` (the web writes `notificationEnabled = false`; iOS does both). Out of scope for this round.

---

## 6. Change 5 — Feedback surfaces and erase completeness

Amends §8.14, §8.16, §8.17. Sources: iOS #15's palette action and outcome copy (`FeedbackClient.swift:37-72`, `FeedbackView.swift:209-220`), web #527 §5a (`feedback_controls.pb.js`) and §5h (`reset-everything.ts`).

### 6.1 Palette action

`PaletteAction.SEND_FEEDBACK("Send feedback")` joins New task / Toggle show completed / Toggle theme, matched by the same substring rule, and navigates to the existing `feedback` route. Both siblings expose it from the palette.

### 6.2 Outcome tiers

`FeedbackApi.Result.Failed` gains a kind; the screen shows the matching sentence (iOS copy, verbatim — product strings, not this document's prose) and keeps the draft **and its submission id** so a retry collapses server-side:

| Condition | Kind | Copy |
|---|---|---|
| Transport error (`IOException`) | `OFFLINE` | "Couldn't reach the server. Your draft is saved — try again when you're online." |
| HTTP 429 (PocketBase rate limit **or** the new quota hook's "Feedback capacity is temporarily full.") | `RATE_LIMITED` | "Too many submissions right now. Your draft is saved — try again later." |
| HTTP ≥ 500 (including the hook's fail-closed 500 "Anonymous feedback controls are not ready.") | `SERVER` | "The server had a problem. Your draft is saved — try again shortly." |
| Any other non-2xx, including a 400 that is **not** the `validation_not_unique` duplicate | `REJECTED` | "The server refused this submission. Your draft is saved." |

The duplicate-400-is-success rule is unchanged. Server-side facts worth knowing, not client behavior: the hook enforces 30 requests/minute, a 10,000-record aggregate quota, and **180-day retention** of feedback rows; `client_submitted_at` is "untrusted client clock for context only." Android's disclosure copy needs no change — it already states what is sent, and retention is a server policy.

### 6.3 Erase completeness

**Erase All Data** and **Delete account & erase everything** both clear the feedback draft and its submission id (`GsdPrefs.clearFeedbackDraft()`, which already exists) in the same step as `LocalErase.eraseAll`. Theme, onboarding, and the share-title preference stay untouched (§8.16). The web's `resetFeedbackState()` is the precedent: a reset must not resurrect a draft.

### 6.4 Declared absence

The returning-user nudge (web #525: one dismissible sentence under the Review stat rail, gated on 14 days' tenure, 10 completions over 3 days, 90-day send cooldown, 180-day dismiss cooldown) is **web-only by decision**. Android, like iOS, keeps Settings and the palette as its only feedback entry points. Parity audits must not flag it.

---

## 7. Change 6 — Smart-view bounds in the backup envelope

Amends §8.16 and Appendix B. Source: web #527 §5g (`SCHEMA_LIMITS`, `applySmartViews`, `collectExportableSmartViews`).

### 7.1 Limits (`SmartViewLimits`, `:core:model`)

| Limit | Value | Applies to |
|---|---|---|
| `MAX_SMART_VIEWS` | **100** | custom views accepted per import, and emitted per export |
| id length | 1–128 | import |
| name length | 1–**80** (import bound; the editor keeps §8.13's 60) | import |
| icon length | ≤ 64 | import |
| `criteria.searchQuery` | ≤ 600 | import |
| `criteria.tags` | ≤ **200** entries, each 1–30 (the tag length limit; the *count* is deliberately not `MAX_TAGS` — a view filters over the whole vocabulary) | import |
| `criteria.quadrants` / `criteria.recurrence` | ≤ 4 entries | import |
| `appPreferences.pinnedSmartViewIds` | ≤ 5 entries, each 1–128 | import |

Android's `SmartView` has no `description` field; the web's ≤ 600 bound is moot here. Unknown keys on a smart-view row are **ignored** (Android stays lenient where the web's `smartViewSchema` went `.strict()`) — a declared divergence.

### 7.2 Import

Order of guards is unchanged (10 MB byte cap → structural decode → three-array task cap → per-record leniency). Within `smartViews`:

1. Each row is decoded leniently as today; a row that fails to decode, has `isBuiltIn: true`, or **violates any bound in §7.1** is skipped **and counted in `skipped`** (today smart-view skips are silent).
2. **Cap:** rows are considered in document order; a row whose id already exists locally (merge mode) or was already accepted from this document **overwrites and consumes no cap room**; a genuinely new id counts against 100; new ids beyond the cap are skipped and counted. In replace mode the custom store is cleared first, so the first 100 valid rows land.
3. `pinnedSmartViewIds` on import: entries are truncated to the first **5** valid ids (length 1–128); the singleton is otherwise applied exactly as today (replace mode only).

Never fatal: an over-cap or malformed smart-view store must not abort the tasks, archive, trash, or settings travelling with it.

### 7.3 Export

`smartViews` emits at most **100** custom views, ordered by `createdAt` ascending then id ascending (deterministic clip). Android-authored rows always satisfy §7.1 by construction (name ≤ 60, curated icons, validated criteria) and imported rows were bounded on the way in, so no export-side representability filter is needed — stated so nobody adds one.

---

## 8. Change 7 — Sync hardening

Amends §8.21, §9.4, §9.5. Source: web #527 §5b–§5e. Owner decision 2026-09-06: adopt all four now; iOS carries them as a sibling backlog item.

### 8.1 Bounded remote scans, fail closed

`MAX_REMOTE_TASKS = 10_000`; page size stays 200. `TaskListPage` gains `totalItems`. Every full-listing walk — the pull loop, `fetchRemoteIndex`, `eraseRemote`, and the account-deletion fallback — goes through one `BoundedListing.collect(...)` that **throws `RemoteListingIncomplete`** (never returns partial data) when:

- `totalItems > 10_000`;
- the returned `page` is not the one requested;
- `totalItems` changes between pages;
- a page is empty before the last page;
- the materialized item count exceeds the cap or, at the end, differs from `totalItems`;
- the index sees a duplicate `task_id` (the web's `fetchRemoteTaskIndex` rule).

Consequences: pull aborts and the run reports `error`; **deletion reconciliation is skipped** for that run (a partial or over-cap enumeration may never drive deletion); erase-remote and account deletion abort before touching anything. Listing order gains a stable tie-break: `sort=updated,id`.

### 8.2 A malformed remote version never authorizes overwrite

In the push loop, for CREATE/UPDATE: when the remote index has a record for the task but its `client_updated_at` is **missing or unparseable** (`PbDates.parse` → `null`), the item is **skipped this drain** with `bumpQueueRetry(…, "Remote task version is missing or malformed", …)` — counting toward the five-failure threshold like any other error — and **no write is issued**. Today that case falls through to `updateTask`. A missing version may protect or quarantine data; it may not authorize an overwrite (the web's spec line, adopted verbatim).

### 8.3 Three-verdict remote-absence reconciliation

`reconcileDeletions` classifies each local **active** task that is absent from a complete remote index:

| Queue state for the task | Verdict | Effect |
|---|---|---|
| any **pending** row | `PROTECT` | keep (edit-beats-delete under LWW; unchanged) |
| no pending, any **failed** row | `ABANDON` | **move the task to Trash** with `deletedAt = now`, delete all its queue rows in the same transaction, enqueue nothing |
| neither | `APPLY` | delete the local row (unchanged; no trash entry — §8.20's remote-delete rule stands) |

Why `ABANDON` trashes where `APPLY` does not: an abandoned task's content **never reached the server** — the local copy is the only copy, and the retry-exhausted queue row was shielding it forever (or would have let it vanish). Trash keeps it recoverable for 30 days. New port `SyncLocalStore.abandonToTrash(id, deletedAtMs)`, implemented over `TaskEntity.toDeleted`.

### 8.4 Account deletion: server route first, bounded fallback

1. **Preferred:** `DELETE /api/gsd/account` with the auth header → `204` means the server erased every task and the user in one transaction; proceed to sign-out (and local erase if chosen). **Only a 404 falls back** (route not deployed — true of `api.vinny.io` today, so the fallback is the live production path); any other failure, including a transport error, stops with nothing changed locally.
2. **Fallback:** up to **3 passes** of enumerate (§8.1 rules) → delete each record with the existing 100 ms throttle. The **user record is deleted only after a complete listing returns empty**. Retrying is always safe: the user record is never deleted before the task wipe completes.
3. Outcome surfaced to the user (today the call is wrapped in a silent `runCatching`): a snackbar/dialog line — success: unchanged sign-out flow; listing failed before any delete: "Couldn't list your synced tasks, so nothing was deleted. Check your connection and try again."; some tasks deleted then auth rejected: "Some synced tasks were deleted, but your account is still there. Sign in again, then run Delete account once more."; some tasks deleted then any other failure or three passes exhausted: "Some synced tasks were deleted, but your account is still there. Try again to finish deleting it."; cloud deleted but local erase threw: "Cloud account deleted, but local erase was incomplete. Use Erase All Data to retry local cleanup."
4. The orchestration moves out of `SyncCoordinator` into a pure `AccountDeleter` in `:core:sync` over the `SyncRemote` + a small `AccountRemote` port (`deleteAccountViaRoute()`, `deleteUser(id)`), so the whole matrix runs on JVM fakes.

---

## 9. Amendments to `docs/gsd-android-spec.md` (folded in by the plan's final task, after code is green)

Mark each with `> Amended 2026-09-06 — see docs/2026-09-06-cross-platform-parity-spec.md`:

- **§5.10** — add the skeleton silhouettes and shimmer (§4.2–4.3) and the `StatusBadge` capsule (§2.3) to the component looks.
- **§6.1** — Browse gains the root search field above Archive (§3.1).
- **§7.9** — `searchQuery` is trimmed; whitespace-only matches all (§3.2).
- **§8.1** — Matrix shows the skeleton until the first snapshot (§4.3).
- **§8.3** — Reminder row: the eight presets plus any off-list stored value, labelled by the §5 formula; never snapped.
- **§8.13** — filtered lists use `TaskOrdering.forList` (§3.3); the search-miss copy (§3.4); the list skeleton (§4.3).
- **§8.14** — Browse root search semantics (§3); palette action "Send feedback" (§6.1).
- **§8.15** — `quadrantStats` + rings card; `DeadlineStatus`; upcoming rows with date, badge, tap → editor; day-based upcoming; rounded percent; dashboard skeleton (§2, §4).
- **§8.16** — smart-view bounds and skip-counting (§7); erase clears the feedback draft (§6.3).
- **§8.17** — feedback outcome tiers (§6.2); the declared absence of the nudge (§6.4).
- **§8.21** — account deletion route-first with bounded fallback and surfaced outcomes (§8.4).
- **§9.4** — bounded, fail-closed listings and the three-verdict reconciliation (§8.1, §8.3).
- **§9.5** — the malformed-remote-version guard (§8.2).
- **§13.3** — reduced motion also suppresses the ring grow-in and the shimmer (§2.2, §4.2).
- **Appendix B** — the §7.1 limits table.

## 10. Out of scope

- The web's Review verdict and stat rail (#519); the feedback nudge (#525) beyond recording its absence.
- The iOS stat-card digit morph; any change to Android's quadrant-distribution and time-by-quadrant bars (the web has bars, iOS has a donut — all three differ by design).
- Aligning Android's "None" reminder semantics with the web's `notificationEnabled = false`.
- The web's pre-parse smart-view structural walker, its read-path guard, and `.strict()` unknown-key rejection.
- The web's sign-in ownership guard over three stores; Sentry key trimming.
- Wire-model, cursor, OAuth, token, or SSE changes (none happened).
- Version bump / Play release of this round — an owner decision (a mid-window closed-test release is a legitimate "changes made" answer for the production questionnaire; see `tasks/todo.md`).

## 11. Acceptance criteria (each maps to plan tasks)

1. `Analytics.compute` yields four `QuadrantStat`s in Q1→Q4 order with `completed/total` rates, a rounded `percent`, and an `upcoming` list that includes a task due at 00:00 today when viewed at 09:00, sorted due-asc then created-desc, capped at 5. `DeadlineStatus.of` passes iOS's four cases (due yesterday noon / today 00:00 / today 23:00 / tomorrow 00:00 seen at 23:00) in `America/Chicago`.
2. The Dashboard renders the rings card with the em-dash legend value for an empty quadrant and the single-node accessibility label; upcoming rows show the date, a "Today" badge only for today's tasks, and open the editor on tap.
3. Browse shows results for a one-character query, replaces the list, matches subtask titles, excludes completed tasks, orders dated-before-undated then newest-first, and shows `No tasks match "q"` on a miss; `FilterCriteria` trims the query.
4. `ViewListContent` and Browse results share `TaskOrdering.forList`; a completed-status view orders by `completedAt` descending.
5. Dashboard, smart-view list, and Matrix show a skeleton (`contentDescription` "Loading") while the task state is `null` and the empty state only once loaded-and-empty; the shimmer is absent when animators are disabled; `observeTasks()` emits an empty first snapshot on an empty database.
6. `ReminderOptions.including` passes iOS's four cases (nil, on-list 60/1440, off-list 5/45/2880, zero-first); a task stored with `notifyBefore = 45` shows "45 minutes before" and survives an untouched save byte-identical.
7. The palette lists "Send feedback" and navigates to the feedback route; `FeedbackApi` maps IOException / 429 / ≥500 / other → OFFLINE / RATE_LIMITED / SERVER / REJECTED with the duplicate-400 still `Sent`; each kind renders its sentence and keeps the draft and submission id; Erase All Data and delete-and-erase clear the feedback draft.
8. Import skips-and-counts a smart view with an 81-char name, a 601-char search query, or 201 filter tags; admits 100 new views and skips the 101st while an overwrite at the cap succeeds; truncates pinned ids to 5; both cross-platform fixtures still round-trip. Export clips at 100 in `createdAt` order.
9. A listing whose `totalItems` exceeds 10,000, changes mid-walk, returns the wrong page, or contains a duplicate `task_id` throws `RemoteListingIncomplete`; that run reports `error` and deletes nothing locally.
10. A pending item whose remote record has an unparseable `client_updated_at` is not pushed, gets its retry bumped with the malformed-version message, and reaches `failed` after five such drains.
11. Remote-absence reconciliation keeps a task with a pending row, trashes (with `deletedAt = now`, queue rows removed, nothing enqueued) a task with only failed rows, and deletes a task with no rows.
12. Account deletion: 204 from the route → user signed out with no enumeration; 404 → fallback enumerates, deletes, and removes the user only after an empty complete listing; a listing failure before any delete leaves the account and reports the "nothing was deleted" copy; a mid-way auth rejection reports the sign-in-again copy; a transport error on the route stops with nothing changed.
13. `./gradlew :core:model:test` stays sub-second; `testDebugUnitTest`, `connectedAndroidTest`, and `:app:assembleDebug` are green; the spec amendments in §9 are folded in.

*— end of parity specification —*
