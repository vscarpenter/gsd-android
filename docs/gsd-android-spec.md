# GSD Task Manager — Native Android Product Specification

- **Date:** 2026-07-19
- **Status:** Draft for review
- **Author:** Vinny Carpenter (with Claude)
- **Purpose:** A self-contained product specification for building the GSD Task Manager as a **native Android app (Kotlin + Jetpack Compose)** for phones, tablets, and foldables. This document is written to be handed to a fresh Claude Code session in a new, empty repository (suggested name: `gsd-androidapp`). It assumes **no access to the iOS or web codebases** — every behavioral rule, data limit, protocol detail, and design token needed to build the app is inlined here.
- **Provenance:** GSD ships today as a web app (Next.js, gsdtaskmanager.com) and a native iOS/iPadOS/Mac Catalyst app (SwiftUI). This spec was distilled from the shipped iOS app (v2.2.0) and its behavior authority, and reconciled against the live implementation — where the original iOS spec and the shipped app disagree, **the shipped app is authoritative** and this document reflects it.

> **Reader note.** Where this spec says "matches iOS/web," the relevant behavior is described in full in this document; you do not need the other codebases. Enumerated values (recurrence types, archive tiers, snooze durations, field limits, wire field names, color values) are authoritative as written.

---

## 1. Overview & Vision

GSD ("Get Stuff Done") Task Manager is a **privacy-first, offline-first Eisenhower-matrix task manager**. Tasks are classified along two axes — **urgent** and **important** — into four quadrants, helping the user decide what to *do first*, *schedule*, *delegate*, or *eliminate*. The app answers one question well: *what do I work on next?*

The product's defining characteristics, which the Android app must preserve:

- **Privacy-first & offline-first.** All data lives on-device. The app is fully usable with no account and no network. Cloud sync is strictly **optional** and opt-in. This is a *correctness constraint*, not a preference.
- **Frictionless capture.** A persistent capture bar with a natural-language shorthand (`!`, `!!`, `*`, `#tag`) lets the user add and classify a task in one keystroke-light gesture.
- **Depth under a calm surface.** Behind the 2×2 grid sit recurrence, subtasks, dependency graphs, time tracking, analytics, and multi-device sync.
- **Editorial, calm aesthetic.** A serif display typeface for headings, generous spacing, restrained color — the four quadrant accents carry the only strong color in the app.

### Why native (not Flutter / not React Native / not a wrapped PWA)

The same reasoning that produced the native iOS app: a first-class platform citizen — instant launch, native gestures, home-screen widgets, share-sheet integration, exact-alarm reminders, TalkBack, predictive back — none of which a wrapper delivers convincingly. The Android app **reimagines interactions around Android idioms** (Material components restyled to GSD's tokens, bottom navigation, snackbars, notification actions, adaptive layouts) rather than transliterating iOS or web layouts. "Native, not ported" is a founding product principle.

### The GSD ecosystem this app joins

| Client | Platform | Status |
|---|---|---|
| GSD web app | gsdtaskmanager.com (Next.js + Dexie/IndexedDB) | shipped |
| GSD iOS app | iPhone / iPad / Mac Catalyst (SwiftUI + GRDB) | shipped, App Store |
| GSD MCP server | Claude Desktop (reads/writes the shared backend) | shipped |
| **GSD Android app** | **phones / tablets / foldables** | **this spec** |

All clients share one self-hosted **PocketBase** backend and one wire schema. A task captured on Android must round-trip losslessly through the web app, the iOS app, and the MCP server. Section 4 states the compatibility contract; treat it as non-negotiable.

### Success criteria

1. A user can capture, classify, edit, complete, and organize tasks entirely offline, in a UI that feels **designed for Android, not ported** — while still unmistakably GSD.
2. Signing in syncs tasks bidirectionally with the existing backend, the web app, the iOS app, and the user's other devices, with conflicts resolved deterministically (last-write-wins).
3. The app ships the full feature set: matrix, capture parser, recurrence, subtasks, dependencies, time tracking, snooze, archive, smart views, search + command palette, analytics dashboard, import/export, local reminders, undo-delete — plus Android-native surfaces (widget, share target, app shortcuts, notification actions).
4. The app passes Google Play review with an accurate Data Safety form.

---

## 2. Goals & Anti-Goals

### Goals

- Full behavioral parity with the shipped GSD apps, reimagined with Android **interaction patterns**.
- Bidirectional multi-device sync with the existing PocketBase backend, byte-compatible with the web and iOS clients.
- Native surfaces: a Glance home-screen widget, a share target, app shortcuts, deep links, reminder notifications with action buttons.
- **Phones and large screens (tablets / unfolded foldables) as co-equal first-class targets** with genuinely adaptive layouts — the Android analog of the iPhone/iPad co-equality.
- Play-Store-ready: accurate Data Safety declarations, modern target API, accessibility baseline.

### Anti-Goals (explicitly out of scope for this version)

- **No Wear OS app.** (Matches the iOS "no Watch app" anti-goal.)
- **No Material You dynamic color.** Wallpaper-derived theming would dissolve the rationed quadrant-accent brand. The app ships its own fixed light/dark palettes. (A monochrome themed **launcher icon** layer is the one exception — it's a launcher convention, not app theming; see §12.5.)
- **No changes to the existing backend or MCP server.** The app consumes the existing PocketBase `tasks` collection as-is.
- **No real-time collaborative editing / shared task lists.** Sync is single-user, multi-device.
- **No streaks, badges, points, or gamification.** GSD measures progress honestly; the iOS dashboard's streak tiles were deliberately removed by owner decision. Do not add them here.
- **No analytics/tracking SDKs.** Local-only by default; any diagnostics would be opt-in (none at launch).

---

## 3. Platforms & Technical Foundation

- **Min SDK:** **31** (Android 12). Rationale: native splash-screen API, exact-alarm permission model, mandatory notification channels, and broad 2026 device coverage. **Target SDK: latest stable.**
- **Devices:** phones, tablets, and foldables. Layouts adapt by **window size class** (compact / medium / expanded); tablets and unfolded foldables get a true 2×2 matrix, drag-and-drop, and hardware-keyboard shortcuts. Support portrait and landscape everywhere; no locked orientations.
- **Distribution:** Google Play (plus sideloadable APK builds for testing).
- **Application ID:** `dev.vinny.gsd` (matches the iOS bundle id family; final say is the owner's at Play listing time).
- **Languages:** English at launch; never hard-block localization (all UI strings in `strings.xml` resources, no concatenated sentences).

### Recommended technical foundation (implementation guidance, not product requirement)

- **Language / UI:** Kotlin, **Jetpack Compose** throughout, Material 3 components restyled with GSD tokens (§5). Compose Navigation with `material3-adaptive` for size-class scaffolding. Edge-to-edge; predictive back enabled.
- **Local store: Room (SQLite)** as the on-device source of truth. Rationale: the app requires a hand-rolled REST + SSE last-write-wins sync engine (§9); Room gives explicit row control and `Flow`-based change observation that maps 1:1 to the web app's Dexie + sync-queue model and the iOS app's GRDB store. Versioned migrations from day one.
- **Networking / PocketBase client:** there is **no official PocketBase Kotlin SDK** worth depending on for this narrow use. Build a small client: REST over **OkHttp + kotlinx.serialization**, realtime over OkHttp SSE (`EventSource`), OAuth via **Custom Tabs** (`androidx.browser`). This is net-new, higher-risk code and gets its own milestone and test surface (§15 Phase 5, §13.6).
- **Dates:** `java.time` (available since API 26). All wire timestamps are ISO-8601 strings with timezone offset (§4.3).
- **Module structure** — the layered architecture is the design; the dependency direction is enforced by Gradle module boundaries, mirroring the iOS `GSDKit` package that made that app testable:

| Module | Responsibility | Depends on |
|---|---|---|
| `:core:model` | Pure Kotlin/JVM domain — Task, Quadrant, capture parser, recurrence, dependency graph (BFS), filtering, validation, analytics, import/export. **Zero Android dependencies** — unit-tested on the JVM in milliseconds. | *nothing* |
| `:core:store` | Room persistence — database + versioned migrations, per-entity repositories over `Flow`, and the single **TaskStore** mutation path that stamps `updatedAt` via an injected clock and enqueues sync. | `:core:model` + Room |
| `:core:sync` | PocketBase REST + SSE sync — a pure **SyncEngine** (pull / push / deletion-reconcile / LWW, fully unit-tested against recorded fixtures), OAuth2-PKCE auth, JWT handling, wire mappers. | `:core:model` + `:core:store` |
| `:app` | Compose UI, widget (Glance), share target, notifications, deep links, composition root. Thin. | all of the above |

- **Concurrency:** Kotlin coroutines + `Flow`. The sync engine serializes its runs (one sync at a time; concurrent triggers coalesce).
- **DI:** a **manual composition root** in the `Application`/root activity (constructor injection, no Hilt/Koin). The iOS app wires everything explicitly in one `init()`; match that simplicity. One database, shared repositories handed to both the TaskStore and the SyncEngine so a pulled write reaches UI observers and a local mutation reaches the sync drain.
- **Background:** WorkManager for the periodic maintenance job (auto-archive sweep + opportunistic sync, §11.5); `AlarmManager` exact alarms for reminders (§11.2).
- **Token storage:** Android **Keystore-encrypted** storage (encrypt the PocketBase JWT with a Keystore key; persist ciphertext in DataStore). Never plain SharedPreferences, never logged.
- **The fast feedback loop is `:core:model` + `:core:sync` JVM tests, not the emulator.** `./gradlew :core:model:test` must stay sub-second-fast and cover all pure logic. Instrumented/emulator tests are for Room migrations and UI smoke only.

### Engineering standards

Adopt the same agentic coding standards file the iOS repo uses (`coding-standards.md` — spec-driven development, TDD red/green/refactor, conventional commits, small functions, no speculative abstraction). Copy it into the new repo unchanged; it is platform-agnostic.

---

## 4. Compatibility Contract (non-negotiable)

Everything in this section is shared with the web app, the iOS app, and the MCP server. Any deviation breaks cross-device sync silently. When in doubt, this section wins over convenience.

### 4.1 IDs

- Task and entity IDs are **URL-safe nanoid-style strings**. Generate with the nanoid default alphabet (`A-Za-z0-9_-`).
- Lengths in practice: tasks use nanoid **21** (the default); time entries **8**; smart views **12**; minimum accepted ID length anywhere is **4**. Built-in smart views use fixed human-readable IDs (§8.13).
- IDs are generated **client-side** and never re-issued; they are the cross-client join key (`task_id` on the wire).

### 4.2 The wire schema

The PocketBase `tasks` collection schema in §9.1 — snake_case field names, flattened `time_entries`, `client_updated_at` as the LWW key — must be implemented exactly. The Android app must not add, rename, or repurpose wire fields.

### 4.3 Timestamps

- Wire/export timestamps are **ISO-8601 strings with timezone offset and millisecond precision** (e.g. `2026-07-19T14:03:22.114-05:00`); internally store epoch millis or `Instant`.
- **LWW comparisons are at millisecond precision** on `client_updated_at` (§9.3).

### 4.4 Import/export JSON

> Amended 2026-08-29 — see `docs/2026-08-29-cross-platform-parity-spec.md`

The export document (§8.16) is a shared interchange format across all clients: a seven-store, versioned envelope — `version` (string `"2.1.0"`), `exportedAt`, `tasks`, `archivedTasks`, `deletedTasks`, `smartViews`, `notificationSettings`, `archiveSettings`, `appPreferences`. Same camelCase Task shape, same lenient-import rules, and the same absent-key-means-untouched semantics across clients.

### 4.5 Auth providers

The backend has **Google, GitHub, and Sign in with Apple** OAuth providers configured. Android must offer **all three** — an iOS-first user whose PocketBase account is keyed to their Apple identity must be able to reach the same data from Android. All three run through the same PocketBase web-redirect OAuth flow (§10); no Play-services-specific sign-in path.

### 4.6 MCP server coexistence

The MCP server (Claude Desktop) reads/writes the same backend with ~20 tools (list/create/update/complete/delete/bulk-update tasks, analytics queries). It needs nothing from Android except wire-schema fidelity. Tasks created on Android appear to Claude Desktop and vice versa, mediated purely by sync.

### 4.7 Device-local fields

> Amended 2026-09-20: see `docs/play/2026-09-20-polish-review.md`, finding S2

`notificationSent`, `lastNotificationAt`, and `snoozedUntil` are **device-local**: they are pushed on the wire but **never taken from the remote on pull/merge** — each device manages its own reminder state (§9.4). Preserving this rule is what keeps a snooze on the phone from silencing a reminder on the tablet, and vice versa.

**Reminder bookkeeping never touches the LWW key.** When a reminder posts, the app records `notificationSent` and `lastNotificationAt` through `TaskStore.markReminderSent`, a single column update. It does not restamp `updatedAt` and it enqueues no push, which matches the web client. It is the one local write outside the enqueue-before-write rule, and it is safe because a device that already holds the task never takes these fields from the wire. iOS reads them only when it first creates its local copy, by which time the fire time has usually passed. A restamp here let a fired reminder overwrite a newer edit from another device. A user-initiated snooze is different: it restamps and syncs on all three clients.

---

## 5. Design System

GSD's brand is **Editorial · Calm · Focused** — magazine-like restraint, a serif display face, and color rationed to the four quadrant accents. Everything below was extracted from the shipped iOS app and web brand; hex values are authoritative. The Android app expresses this through restyled Material 3 components: **no dynamic color, no tonal-elevation tinting, no Material baseline purple anywhere.**

**Anti-references** (what GSD must never look like): dense corporate-SaaS control panels; neon/glassy/gradient-heavy "productivity" aesthetics; playful/cartoonish; gamified streak-driven dopamine loops.

### 5.1 Color — quadrant accents (the only strong color in the app)

| Quadrant | Name | Light | Dark |
|---|---|---|---|
| Q1 `urgent-important` — **Do First** | Rust | `#B23A2E` | `#E0705F` |
| Q2 `not-urgent-important` — **Schedule** | Tide | `#2C6680` | `#6FAACB` |
| Q3 `urgent-not-important` — **Delegate** | Ochre | `#8A6A22` | `#CFB266` |
| Q4 `not-urgent-not-important` — **Eliminate** | Slate | `#6F685F` | `#A9A096` |

**Quadrant washes** — hand-tuned opaque tints (NOT alpha-derived), used behind tag chips and the selected cell of the editor's 2×2 quadrant picker:

| Quadrant | Wash light | Wash dark |
|---|---|---|
| Q1 Rust | `#F4E4E0` | `#3A211D` |
| Q2 Tide | `#E1ECF1` | `#173039` |
| Q3 Ochre | `#F0E9D8` | `#322B17` |
| Q4 Slate | `#ECE9E3` | `#2A2620` |

The one place accent **alpha** is used: the editor 2×2 picker's unselected cell border = accent at 35% opacity (selected = accent at 100% + wash fill).

### 5.2 Color — the "warm paper" neutral ramp

| Token | Purpose | Light | Dark |
|---|---|---|---|
| `paper` | page background | `#F4F1E9` | `#17150F` |
| `sunken` | inset fills, progress tracks, resting chips | `#ECE7DC` | `#100E0A` |
| `surface` | raised cards, sheets, capture bar | `#FFFFFF` | `#221E17` |
| `surface2` | secondary surface, circular buttons | `#FBF9F3` | `#1B1812` |
| `hairline` | separators, card borders | `#E3DDD0` | `#322D24` |
| `hairlineStrong` | stronger borders, unselected rings | `#D8D1C1` | `#423B2F` |
| `ink` | primary text | `#211E1A` | `#F1ECE2` |
| `ink2` | secondary text | `#6E6760` | `#A79F92` |
| `ink3` | tertiary text, quiet icons | `#797368` | `#948A79` |
| `inkOnAccent` | glyph on a filled accent | `#FFFFFF` | `#17150F` |
| `shadow` | warm-tinted card shadow ink | `#282116` | `#000000` |

### 5.3 Color — functional

> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, findings 10 and 23

| Token | Purpose | Light | Dark |
|---|---|---|---|
| `success` | completion, 100% progress | `#3E7D52` | `#6FB07F` |
| `alert` | overdue, delete, destructive | `#B23A2E` | `#E0705F` |
| `alertWash` | field behind alert text | `#F4E4E0` | `#3A211D` |
| `tint` | the single interactive tint (links, actions, the text caret in every field) = Tide | `#2C6680` | `#6FAACB` |

Sync-status semantics: error = `alert`; soft health warning = **Ochre** ("attention, not alarm"); pending/progress = default ink.

**Material 3 `ColorScheme` mapping** (so stock components inherit correctly): `primary = tint`, `background = paper`, `surface = surface`, `surfaceVariant = sunken`, `outline = hairlineStrong`, `outlineVariant = hairline`, `error = alert`, `onBackground/onSurface = ink`, `onSurfaceVariant = ink2`, `onPrimary = inkOnAccent`. Set **tonal elevation to 0** everywhere (no elevation tinting) and expose the full GSD token set via a `CompositionLocal` alongside `MaterialTheme`. `secondary` and `tertiary` = `tint`; `primaryContainer` and `tertiaryContainer` = `tideWash` (the date and time pickers' selected fields); `errorContainer` = `alertWash`; every surface and container role maps to a GSD neutral. An unmapped role keeps Material's baseline purple, which is how `secondary` once colored the selected navigation label. **Shapes and type:** `MaterialTheme` also takes the §5.5 shape scale (chips full pills; menus and snackbars 12dp; dialogs, sheets, and pickers 26dp) and the §5.4 type scale (Newsreader for display and headline roles, the system sans for the rest, dialog titles in Card title), so stock components match the hand-built ones.

Theme preference: **System / Light / Dark** (user-selectable in Settings; follows system by default).

### 5.4 Typography

> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, findings 21 and 35

Exactly **two typefaces**:

- **Newsreader** (serif; bundled — it's the web app's display face, free on Google Fonts, SIL OFL; its own fallback chain is "New York"/Georgia, which is what iOS uses). Display roles only. Use the variable font; weights regular–semibold.
- **System sans (Roboto / OEM default)** for everything functional. **Never body copy in serif.**
- Monospace (system mono) appears only when rendering the capture shorthand tokens themselves (onboarding, help: `!!`, `*`, `#tag`).

| Role | Face | Size / weight | Used for |
|---|---|---|---|
| Large Title | Newsreader | 34sp / semibold | top-app-bar expanded titles |
| Title | Newsreader | 28sp / semibold | sheet titles ("New Task"), onboarding |
| Quadrant Title | Newsreader | 20sp / semibold | quadrant headers (in the quadrant pigment), empty-state headlines |
| Card title | Sans | 17sp / semibold | task titles |
| Body | Sans | 17sp / regular | editor fields, body copy |
| Subhead | Sans | 15sp / regular | card description preview |
| Metadata | Sans | 13sp / regular | card meta row, tag chips |
| Caption | Sans | 12sp / regular, caps | section labels |

Dashboard stat numerals are Newsreader. Use `sp` everywhere (respect user font scale; test at 2× / non-linear scaling). Tabular (monospaced) digits for counts, timers, and subtask progress. Card title wraps to 2 lines (unlimited at accessibility scales, meaning font scale 1.5 and up: the steps Android 14 added above the old 1.3 maximum); description 2 lines; metadata row 1 line. Bottom-bar labels stay on one line and end in an ellipsis rather than break mid-word.

Brand lockup in the top app bar: 24dp app mark + "GSD" (sans, bold, ink) + "·" (ink3) + screen name (sans, semibold, ink2, one line). Every screen has this bar, at least 56dp tall. Matrix adds its actions (§6.1). A pushed screen (a smart view, Archive, Trash, the smart view editor, Sync History, Feedback, the Field Guide) shows an Up arrow ("Navigate up") in place of the mark, Archive and Trash carry their buttons in the bar, and a smart view's bar shows the view's name. TalkBack reads the lockup as one heading and skips the dot.

### 5.5 Shape & spacing

Corner radii (all continuous/squircle-feeling; Compose `RoundedCornerShape` is fine):

- `tile` = 8dp (small tiles, capture-token chips) · `small` = 12dp · `input` = 16dp (text inputs, stat cards) · `card` = 22dp (cards, grouped panels) · `sheet` = 26dp (bottom sheets)
- Chips, buttons, and the capture bar are **full pills** (capsule).
- Card accent spine: 3dp wide, 1.5dp corner radius. Progress tracks: capsule.

Spacing is a strict **4dp grid**: 4 hairline gaps · 8 chip padding · 12 in-card rhythm · 16 card padding · 20 screen inset · 24 section gap · **32 between quadrants**. Board panels (2×2 grid) gap at 16.

**Hit targets ≥ 48dp** (Android's floor; iOS used 44pt — go with the platform's stricter 48).

**Cards:** `surface` fill + 1dp `hairline` border + one soft shadow (`shadow` at 10% opacity, ~10dp blur, y-offset 4dp). Borders do the heavy lifting; no glassmorphism, no glow, no tonal tinting.

### 5.6 Task card anatomy

> Amended 2026-09-20: see `docs/play/2026-09-20-polish-review.md`, findings 1 and 48
> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, finding 34

Row layout (top-aligned, 12dp gap), left → right:

1. **Accent spine** — 3dp-wide rounded bar in the quadrant accent, running the full height of the card's content. This is how a task wears its quadrant.
2. **Content column** (6dp vertical rhythm):
   - **Title** — card-title style, `ink`; completed = `ink3` + strikethrough.
   - **Description preview** — subhead, `ink2` (2 lines, `ink3` when completed or a bare URL).
   - **Tag chips** — `#tag` capsules: quadrant **wash** background + quadrant **accent** text.
   - **Metadata row** — 13sp, `ink3`, single line: due date (calendar icon; *today* = Tide semibold; *overdue* = `alert` + warning icon) · recurrence repeat icon · blocked (`lock` + count, ink2) · blocking (`arrow-circle` + count) · running timer (pulsing dot + live `HH:MM:SS`) or tracked total (`clock` + `Xh Ym`) · snoozed (`bedtime` icon + remaining).
   - **Subtask progress** — 84×6dp capsule track (`sunken`) with accent fill (`success` green at 100%) + `done/total` label.
3. **Trailing controls:** overflow menu (`⋯`, ink3, ≥48dp target) + the **completion disc** — 28dp circle in a 48dp target; incomplete = 2dp `ink3` ring; complete = accent-filled + `inkOnAccent` check. Tapping the disc toggles completion (never opens the editor). The overflow button opens the §8.1 context menu. A card drawn without that menu (Trash, Archive) shows no overflow button. Those two screens cannot complete a task, so their cards show no completion disc either.

Blocked tasks show the lock badge but are **not** dimmed (an earlier whole-card dim was removed as too punishing). Completed tasks sort below incomplete within a quadrant and render dimmed/struck.

### 5.7 Iconography

Use **Material Symbols** (outlined style, consistent weight) as the nearest equivalents of the iOS SF Symbols. Canonical concept → icon mapping:

| Concept | Material Symbol |
|---|---|
| Q1 Do First | `local_fire_department` |
| Q2 Schedule | `calendar_month` |
| Q3 Delegate | `group` |
| Q4 Eliminate | `delete` |
| Recurrence | `repeat` |
| Overdue | `warning` |
| Due date | `event` |
| Blocked by | `lock` |
| Blocking | `arrow_circle_right` |
| Tracked time | `schedule` |
| Snoozed | `bedtime` |
| Complete/check | `check` / `check_circle` |
| Overflow | `more_horiz` |
| Matrix (nav) | `grid_view` |
| Browse (nav) | `filter_list` |
| Dashboard (nav) | `bar_chart` |
| Settings (nav) | `settings` |
| Archive | `inventory_2` |
| Search | `search` |
| Sync progress | `sync` |
| Sync problem | `sync_problem` |
| Pin / Unpin | `keep` / `keep_off` |
| Edit | `edit` |
| Share | `share` |
| Duplicate | `content_copy` |
| Undo | `undo` |
| Account | `account_circle` |

Built-in smart-view icons: Today's Focus `target`-style crosshair (`adjust`), This Week `calendar_month`, Overdue Backlog `warning`, No Deadline `event_busy`, Recently Added `auto_awesome`, This Week's Wins `verified`, All Completed `check_circle`, Recurring `repeat`, Ready to Work `bolt`. Smart-view icons render in `ink2` graphite by default; only views with quadrant meaning take color (Today's Focus = Rust, This Week & Ready to Work = Tide, Overdue = `alert`, This Week's Wins = Ochre).

### 5.8 Motion & haptics

> Amended 2026-09-21: see `docs/play/2026-09-20-polish-review.md`, findings 50 and 51
> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, finding 33

Motion is intentional and sparse; **all non-essential animation is suppressed when the system reports animations disabled** (animator duration scale 0 / "Remove animations" accessibility setting — check `ValueAnimator.areAnimatorsEnabled()`).

- **Confetti on completion** — the one celebration. Parameters (feel reference from iOS): **160 particles**, 1.6s duration, gravity 700, launch speed 150–460 with an upward bias (−220 vertical offset), particle size 5–10dp, opacity fading 1→0, origin around the card/center (normalized x 0.3–0.7). **Colors: the four quadrant pigments + success green** — never generic rainbow. Draw on a Compose `Canvas` driven by a frame clock; skip entirely under reduced motion.
- **Running-timer dot:** 7dp dot pulsing opacity 1↔0.35, ~0.85s ease-in-out, repeating; static under reduced motion.
- **Count changes** roll numerically (Compose `AnimatedContent`-style numeric transition) on quadrant headers.
- **Snackbars/toasts** slide+fade from bottom (fade-only under reduced motion). Sheet expansions and list reorders use default snappy springs. Every lazy list row (Matrix list and grid, smart views, Browse results and view rows, Archive, Trash, sync history) slides to its new place and fades in or out on the default `animateItem` spring, and the Matrix headers and empty prompts move with the rows. Under reduced motion rows snap into place.
- **Haptics:** exactly one. A success haptic on complete and on uncomplete, sent through `LocalHapticFeedback` as `HapticFeedbackType.Confirm`, which Compose maps to `HapticFeedbackConstants.CONFIRM`. A test can replace that local and count the calls. Resist adding more.

### 5.9 App icon & brand mark

The brand mark is a **2×2 grid of four rounded pigment tiles with a white checkmark on the top-left (Rust) tile** — no letterforms, no gradients.

- **Adaptive icon:** foreground layer = the four-tile cluster + check (tile corner radius ≈ 23% of tile side; 2×2 with a gutter ≈ 10% of tile side; check stroke ≈ 14% of tile side, round caps, path rising left-to-right); background layer = paper `#F4F1E9`. Keep the cluster inside the adaptive-icon safe zone (66dp of 108dp canvas).
- **Monochrome layer** (themed icons, Android 13+): the single-color mark (tiles + check as silhouette). This is expected launcher citizenship, not dynamic-color adoption.
- Tile pigments (light values): TL Rust `#B23A2E` · TR Tide `#2C6680` · BL Ochre `#8A6A22` · BR Slate `#6F685F`.
- **Splash screen** (Android 12+ SplashScreen API): the mark on `paper` (`#F4F1E9` light / `#17150F` dark).
- In-app 24dp mark: same art, transparent background (used in the top-app-bar lockup).

### 5.10 Key component looks

> Amended 2026-09-06 — see `docs/2026-09-06-cross-platform-parity-spec.md`
> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, finding 32

- **Capture bar:** a pinned pill — `surface` fill, `hairline` border, the soft card shadow — sitting on `paper`. Placeholder: `Capture a task…  (!!  *  #tag)` in `ink3`. Trailing **quadrant chip** (see §8.2). Parsed `#tag` chips appear beneath in neutral `sunken`/`ink2` (they only take quadrant color once on a card). A quiet "Details" text button in `tint` opens the full editor.
- **Quadrant header:** a fixed 26dp icon column (shared left edge) with the accent-colored quadrant symbol, the serif 20sp title in the pigment, and a trailing live active-count in monospaced digits (`ink2`).
- **Empty states:** 60dp `sunken` rounded tile + 28dp icon (`ink3`; tinted only to reassure — e.g. green check for "Nothing overdue"), serif headline, one `ink2` sentence (max width ~280dp), at most one `tint` action. In-context quadrant empties use a dashed `hairlineStrong` border (dash 5), radius 22dp, with tailored copy: Q1 *"No fires right now."* · Q2 *"Nothing scheduled yet."* · Q3 *"Nothing to hand off."* · Q4 *"Nothing to drop."* Every other empty surface uses the one shared component, which TalkBack reads as a single node, worded as on iOS:
  - Dashboard (`bar_chart`): *"No stats yet"* · "Complete a few tasks and your trends will appear."
  - Archive (`inventory_2`): *"Nothing archived yet"* · "Completed tasks you archive will live here." A search that matches nothing shows the search-miss state instead.
  - Trash (`delete_outline`): *"Trash is empty"* · "Deleted tasks stay here for 30 days, then they're gone."
  - Smart views: Overdue Backlog (`check_circle` in `success`) *"Nothing overdue"* · "You're all caught up."; This Week's Wins (`emoji_events`) *"No wins yet this week"* · "Completed tasks from the last 7 days show here."; any other view (its own icon) *"Nothing here yet"* · `Tasks matching "<view name>" will appear here.`
  - Sync history (`history`): *"No syncs yet"* · "Each sync from this device shows here, with what it sent and received."
  - Search miss (`search`): `No tasks match "<query>"` · "Try a different word, tag, or quadrant."
- **Sync status chip:** quiet-until-it-matters — **hidden entirely** when idle, healthy, and nothing pending; spinner while syncing (static sync glyph under reduced motion); pending count with sync glyph; `sync_problem` in `alert` on error, in Ochre for soft health warnings.
- **Status badge:** a small capsule beside a title — `GsdType.caption` semibold, single line, 8dp × 3dp padding, pill shape. **Two styles only**, by design (color is rationed, so only overdue earns the alert pigment): *neutral* — `ink2` on `sunken`; *alert* — `alert` on `alertWash`. Used by the Dashboard's upcoming rows (§8.15).
- **Loading skeletons:** first-frame silhouettes shown while a surface's task state is still `null` (§8.1, §8.13, §8.15) — never a spinner, never a minimum display time. Built from one primitive, `SkeletonBlock(height, width = null, shape = GsdShapes.small)`: a `sunken`-filled rounded rectangle that fills the available width when `width` is null. `Modifier.skeletonShimmer()` sweeps **one** light band across the whole composition left→right — band width 50 % of the container, a horizontal gradient `transparent → surface @ 75 % → transparent` (the raised `surface` tone, not white: a white band reads as glare on warm paper and vanishes in dark mode), **1600 ms** linear sweep with a **600 ms** delay before each pass (including the first, so layout settles before anything moves), repeating. The band is masked to the placeholder shapes (`CompositingStrategy.Offscreen` + `BlendMode.SrcIn`) so it never streaks across gaps, and it draws only — it never intercepts touch. Apply it **once**, to the outermost skeleton container. Every skeleton is **one** semantics node (`clearAndSetSemantics`) with `contentDescription` "Loading". Under reduced motion the band is not drawn at all — the skeleton is a static silhouette (§13.3).
- **Onboarding:** 4 paged, skippable, editorial screens on `paper` (§8.19).

---

## 6. Information Architecture & Navigation

The app adapts by **window size class**, the Android analog of the iPhone/iPad split.

### 6.1 Compact width (phones)

> Amended 2026-09-06 — see `docs/2026-09-06-cross-platform-parity-spec.md`
> Amended 2026-09-20: see `docs/play/2026-09-20-polish-review.md`, finding 43
> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, finding 21 and the follow-up to finding 43

A **bottom navigation bar** with four destinations:

1. **Matrix** — the primary surface: a pinned **capture bar** at top, then the four quadrants as a **vertical stack of quadrant sections** (each a header with live count + its task rows; four quadrants can't be shown legibly at once on a phone). Pull-to-refresh triggers a manual sync when signed in. Top app bar: brand lockup, search/palette button, show-completed toggle, select-mode toggle, sync status chip.
2. **Browse** — a **search field** at the very top ("Search all tasks", §8.14), then an **Archive** row, then Smart Views in sections: **Pinned**, **Built-in**, **Custom**, plus a "+" to create a custom view. Each row shows a live match count. Row swipes: pin/unpin (leading); edit/delete (trailing, custom views only). While the search field holds a non-whitespace query the results list **replaces** the whole Browse list; the field is not a width-gated affordance — Browse is one composable at every size class (§6.2), so the search applies to the rail and drawer layouts too.
3. **Dashboard** — analytics (§8.15).
4. **Settings** — §8.17.

Archive is deliberately *inside* Browse, not a fifth destination.

Tapping a destination keeps one copy of it on the back stack, and the tab being left keeps its own stack. Tapping Matrix always lands on the Matrix root. A deep link (§6.4) or a palette row (§8.14) that names one of the four destinations behaves exactly like a tap on it. Any other route pushes over the current screen, and the Matrix tab still returns to Matrix from there. The selected tab is the one whose stack is showing, not the one whose route matches: Browse stays selected on a smart view, Archive, or Trash opened from it, and a screen a deep link pushes straight over Matrix counts as Matrix's. Only a tab's own stack is saved on a tab switch. A screen pushed straight over Matrix belongs to no tab and nothing would restore it, so a tab switch pops it without saving, and Navigation releases its view model.

### 6.2 Medium & expanded width (tablets, unfolded foldables, freeform windows)

> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, findings 11, 36, and 37

**Which layout.** Width under 600dp gets the bottom bar (§6.1); under 840dp the rail; 840dp and wider the drawer, but only with at least 480dp of height (`navLayoutFor`). A phone in landscape is wide but short, so it takes the rail, as Material's window classes advise. Every layout sits on `paper` in the in-app theme, and the rail and drawer layouts apply the safe-drawing insets (system bars, display cutout, and keyboard) that the phone's `Scaffold` applies, so nothing draws under the status bar. Rail and drawer labels stay on one line.

- **Medium:** a **navigation rail** (Matrix, Browse, Dashboard, Settings) with phone-style content, centered and at most 640dp wide.
- **Expanded:** a **permanent navigation drawer** mirroring the iPad sidebar — Matrix; Dashboard; a **Smart Views** section (pinned first, then built-ins, then custom, then "New Smart View"); a **Library** section (Archive, Settings) — beside a detail pane. **Matrix at expanded width is a true 2×2 grid** (Q1 top-left, Q2 top-right, Q3 bottom-left, Q4 bottom-right), each cell a header + scrollable list, with **drag-and-drop of task cards across quadrant boundaries** to reclassify (updates `urgent`/`important`). Smart-view rows show live counts; row context menu offers Pin/Unpin (+ Edit/Delete for custom).

### 6.3 Editor presentation

> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, finding 25

- **Compact:** a **modal bottom sheet** that opens fully expanded, with a header pinned above the scrolling sections: Cancel, the sheet's title, and a filled Save (Tide, `inkOnAccent` text; `sunken` while the title is empty). The half-open sheet used to hide Save, which sat at the end of an eleven-section scroll, and Back collapsed the sheet before closing it. Every way out (Cancel, Back, a swipe down, a tap outside) closes at once when the draft is untouched, and asks first when it has unsaved changes: "Discard changes?" · "Your edits to this task will be lost." · Keep editing / Discard (in `alert`). A save error shows under the header, beside Save.
- **Medium/expanded:** a centered dialog sheet (the board stays visible behind it).

### 6.4 Deep links

> Amended 2026-08-29 — see `docs/2026-08-29-cross-platform-parity-spec.md`
> Amended 2026-09-20: see `docs/play/2026-09-20-polish-review.md`, findings S4, 43, and 4
> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, S4 follow-up; `tasks/todo.md` K, quadrant scroll, and J, Recents replay

Register the **`gsd://` scheme** (intent filters on the main activity). Routes — identical to iOS so widgets, shortcuts, and notifications share one router:

| URL | Opens |
|---|---|
| `gsd://capture` | Matrix with the new-task editor open |
| `gsd://focus` | Matrix (Q1 focus) |
| `gsd://quadrant/<quadrant-id>` | Matrix scrolled to that quadrant (ids from §7.8) |
| `gsd://task/<id>` | that task's editor (fetch directly from the store; cold-start safe) |
| `gsd://smart-view/<id>` | that smart view's filtered list |
| `gsd://dashboard` · `gsd://settings` · `gsd://archive` · `gsd://trash` | those surfaces (`trash` — §8.20) |
| `gsd://oauth-callback?...` | reserved for the OAuth flow (§10); the router ignores it |

`gsd://quadrant/<id>` goes to Matrix and scrolls the phone's list so that quadrant's header is at the top, animated, or in one jump under reduced motion. A cold start's request waits for the first snapshot and the list's first layout. The expanded grid already shows all four quadrants, so there it only goes to Matrix.

A deep link arriving before the UI is ready is persisted and consumed on first composition (cold-start). A `task/<id>` link to a task that no longer exists shows a friendly "not found" state, never a blank screen.

A deep link runs once. The activity handles its launch intent only on a fresh start, when `savedInstanceState` is null. A rotation or any other recreation does not run the link again. Neither does a launch from Recents, which carries `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`: `singleTask` keeps the last link as the task's base intent, and after a reboot Recents starts the activity fresh with that link, a sign-in callback included. A link that reaches a running activity arrives through `onNewIntent`.

There is one `MainActivity`. Its launch mode is `singleTask`, so a link, a widget tap, or a reminder tap that reaches a running app goes to that activity. It never stacks a second copy. Any page above the activity in its task closes first, and that includes the sign-in Custom Tab (§10.2). A tap on the launcher icon closes such a page too. A user who leaves the sign-in tab and comes back through the icon starts sign-in again. The two static shortcuts (§6.5) are the exception. The system launches them with `NEW_TASK` and `CLEAR_TASK`, so each one restarts the task and arrives as a fresh launch.

### 6.5 App shortcuts (launcher long-press)

> Amended 2026-09-21: see `docs/play/2026-09-20-polish-review.md`, finding 46

Two static shortcuts: **"New Task"** → `gsd://capture` and **"Today's Focus"** → `gsd://smart-view/today-focus`. Each has an adaptive icon, a Rust glyph (a plus; the crosshair) on paper, since without one the launcher drew blank circles (review finding 28). The system gives a static shortcut `NEW_TASK`, `CLEAR_TASK`, and `TASK_ON_HOME`, and the app cannot change that. A shortcut fired while GSD is on screen therefore destroys the running `MainActivity` and starts a fresh one, which discards an open draft. For a moment both copies exist, and the old one is paused before the new one is created. That ordering is what the §6.7 RESUMED gates rely on: the fresh copy takes the route, and the paused copy takes nothing.

### 6.6 Hardware keyboard shortcuts

> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, S4 follow-up; `tasks/todo.md` K, Ctrl+1–4

For tablets/DeX/Chromebooks: **Ctrl+K** command palette · **Ctrl+N** new task · **Ctrl+F** search (palette) · **Ctrl+1–4** jump to quadrant. Escape dismisses sheets.

Every key goes through the §6.4 router, so pressed on any tab it switches to Matrix and acts at once. Before, a key pressed off Matrix waited for the next visit there. Ctrl+N behaves as `gsd://capture`, Ctrl+1–4 as `gsd://quadrant/<id>` for Q1–Q4, and Ctrl+K and Ctrl+F open the palette on Matrix. That palette route is keyboard only: no `gsd://` link opens it.

### 6.7 State & system behavior

> Amended 2026-09-20: see `docs/play/2026-09-20-polish-review.md`, findings S4 and 45
> Amended 2026-09-21: see `docs/play/2026-09-20-polish-review.md`, finding 46
> Amended 2026-09-24: `tasks/todo.md` J, a first-launch deep link lost behind onboarding, and the snackbar under the keyboard

- **Requests are one-shot.** A "new task" or "open palette" request (deep links, app shortcuts, the §6.6 keys) is an event, delivered exactly once. Only a Matrix screen that is RESUMED may take it (`ConsumeWhileResumed`), and only a RESUMED `MainActivity` takes a pending deep-link route. STARTED is not enough: a paused activity is still STARTED, and a shortcut's `CLEAR_TASK` (§6.5) leaves the old copy paused while the fresh one is created. Returning to the Matrix tab never replays one, and an activity under another never claims one. A link that arrives while a dialog covers GSD waits until GSD resumes. A request sent before the Matrix screen composes waits for it. A new-task request that arrives while the editor is open is dropped, because the open draft wins. Two requests with equal payloads are still two requests: each send is its own object, so one that arrives while a handler is still running is delivered when it returns.
- Persist (DataStore, mirroring iOS `@AppStorage`): `showCompleted`, theme preference, `hasOnboarded`, `fetchShareTitles`, plus the pending deep link. Tab/drawer selection and scroll positions are **not** persisted across process death (parity with shipped iOS; standard Compose saved-instance-state within a session is fine).
- **Predictive back** enabled; **edge-to-edge** rendering with proper inset handling. The snackbars on Matrix, Browse, and a smart view sit above the keyboard (`imePadding`), so an Undo offered from search results stays visible. The phone layout consumes the scaffold's padding, so that lift counts only the keyboard above the bottom bar. The status and navigation bar icons follow the **in-app** theme, re-applied whenever it changes, so a dark in-app theme on a light system draws light icons (review finding 38).
- First run shows the onboarding flow (§8.19), skippable, gated on `hasOnboarded`. Until that flag loads, the activity draws only paper: neither onboarding nor the nav host. A deep link that arrives with the first launch waits for onboarding to finish, then runs. Reading the flag as "done" before it loaded let the nav host take the link for a moment, and onboarding then replaced it.

---

## 7. Data Model

All entities must be representable in the Room store, in the JSON import/export format (§8.16), and (for the syncable subset) in the PocketBase collection (§9.1).

### 7.1 IDs (authoritative)

- Alphabet (64 URL-safe chars): `0-9 A-Z a-z - _` (nanoid's URL-safe set). Uniform-random selection.
- Lengths: **tasks & subtasks 21** (the web canonical), **time entries 8**, **smart views 12**. Minimum accepted anywhere: **4** (IDs are opaque join keys; other clients emit varying lengths and all must round-trip).
- Built-in smart views use fixed readable IDs (§8.13). IDs are generated client-side, once, and never changed.

### 7.2 Task (the core entity)

| Field | Type | Required | Default | Constraints / Notes |
|---|---|---|---|---|
| `id` | String | yes | generated | ≥ 4 chars, unique, URL-safe |
| `title` | String | yes | — | 1–80 chars |
| `description` | String | no | `""` | 0–600 chars |
| `urgent` | Bool | yes | — | one axis of the matrix |
| `important` | Bool | yes | — | the other axis |
| `quadrant` | enum | derived | — | **pure function** of the two flags (§7.8); persisted as an indexed column, **never encoded in domain/export JSON** |
| `completed` | Bool | yes | `false` | |
| `completedAt` | Date? | no | — | set on complete; cleared on un-complete |
| `createdAt` | Date | yes | now | |
| `updatedAt` | Date | yes | now | bumped on every mutation **by the store** (injected clock); drives sync LWW |
| `dueDate` | Date? | no | — | |
| `recurrence` | enum | yes | `none` | `none` / `daily` / `weekly` / `monthly` — **no "yearly"** |
| `tags` | [String] | yes | `[]` | 0–20 items; each 1–30 chars; stored lowercase |
| `subtasks` | [Subtask] | yes | `[]` | 0–50 items |
| `dependencies` | [String] | yes | `[]` | 0–50 task IDs that must complete first |
| `parentTaskId` | String? | no | — | recurring-instance lineage. **Device-local: has no wire column, never synced.** |
| `notifyBefore` | Int? | no | — | minutes before `dueDate`; ≥ 0 (0 = "at time of event") |
| `notificationEnabled` | Bool | yes | `true` | per-task reminder switch |
| `notificationSent` | Bool | yes | `false` | **device-local** (§4.7) |
| `lastNotificationAt` | Date? | no | — | **device-local** |
| `snoozedUntil` | Date? | no | — | **device-local**; reminders suppressed until then; max 365 days out |
| `estimatedMinutes` | Int? | no | — | 1–10080 (7 days); a stored/received `0` means "unset" |
| `timeSpent` | Int? | no | — | total tracked whole minutes, **calculated** from `timeEntries` |
| `timeEntries` | [TimeEntry] | yes | `[]` | 0–1000 items |

**Subtask:** `{ id, title (1–100 chars), completed }`.
**TimeEntry (rich local form):** `{ id, startedAt, endedAt? (nil while running), notes? (0–200 chars) }`. The wire form is a lossy `{id, startedAt, minutes}` (§9.2).

### 7.3 JSON encoding rules (domain / export / store)

- camelCase keys; **`quadrant` is never encoded** (always recomputed from the flags).
- Dates: ISO-8601 UTC with **millisecond precision**, `T` separator, `Z` suffix (`2026-06-15T08:30:00.500Z`).
- `null` optionals are **omitted**, not written as `null`. Empty strings encode as `""`, empty arrays as `[]`.
- **Decoding is lenient:** required fields are only `id, title, urgent, important, createdAt, updatedAt`; everything else defaults (`description ""`, `completed false`, `recurrence none`, collections `[]`, `notificationEnabled true`, `notificationSent false`). Unknown keys are ignored (old exports may contain e.g. `vectorClock`). A task failing required-field decode or validation is skipped and counted, never aborts the batch.

### 7.4 NotificationSettings (singleton)

`enabled` (default true) · `defaultReminder` minutes (default **15**; presets 15/30/60/120/1440) · `soundEnabled` (default true) · `quietHoursStart`/`quietHoursEnd` (`"HH:mm"` local; UI default 22:00–07:00 when enabled) · `permissionAsked` (default false) · `updatedAt`.

### 7.5 ArchiveSettings (singleton)

`enabled` (default **false**) · `archiveAfterDays` ∈ {**30, 60, 90**} (default 30).

### 7.6 SmartView

`id` (built-ins fixed; custom 12-char nanoid) · `name` (1–60 chars) · `icon` (icon token; custom views choose from 10 curated icons) · `criteria` (FilterCriteria, §7.9) · `isBuiltIn` (read-only when true) · `createdAt` · `updatedAt`.

### 7.7 AppPreferences (singleton) + lightweight prefs

`pinnedSmartViewIds` ([String], ordered, max **5**). Preference storage (DataStore): `showCompleted`, theme (`system`/`light`/`dark`), `hasOnboarded`, `fetchShareTitles` (default false).

### 7.8 Quadrant derivation (authoritative)

| `urgent` | `important` | quadrant id | Title | Intent | Accent |
|---|---|---|---|---|---|
| true | true | `urgent-important` | **Do First** (Q1) | crises, deadlines | Rust |
| false | true | `not-urgent-important` | **Schedule** (Q2) | growth, planning | Tide |
| true | false | `urgent-not-important` | **Delegate** (Q3) | interruptions | Ochre |
| false | false | `not-urgent-not-important` | **Eliminate** (Q4) | distractions | Slate |

Canonical display/iteration order: Q1, Q2, Q3, Q4. Never let the persisted quadrant drift from the flags.

### 7.9 FilterCriteria (powers smart views, filters, search)

> Amended 2026-09-06 — see `docs/2026-09-06-cross-platform-parity-spec.md`

A predicate bundle; all present criteria are **ANDed**. camelCase-Codable:

| Field | Type | Meaning |
|---|---|---|
| `quadrants` | [quadrant-id] | include only these (empty = all) |
| `status` | `all` \| `active` \| `completed` | |
| `tags` | [String] | task must contain **all** listed tags |
| `dueDateRange` | { start?, end? } | inclusive bounds |
| `overdue` | Bool | incomplete AND `dueDate` < today |
| `dueToday` | Bool | incomplete AND due today |
| `dueThisWeek` | Bool | incomplete AND due in [today, today+7) |
| `noDueDate` | Bool | no `dueDate` |
| `recurrence` | [recurrence] | include only these kinds |
| `recentlyAdded` | Bool | `createdAt` within last 7 days |
| `recentlyCompleted` | Bool | completed AND `completedAt` within last 7 days |
| `readyToWork` | Bool | incomplete AND no incomplete blocking dependency |
| `searchQuery` | String | case-insensitive substring across title, description, tags, **and subtask titles** |

`searchQuery` is **trimmed before matching**, and a whitespace-only query matches everything (it behaves exactly like an absent query) — both siblings trim, and without it a stray space silently emptied every list. The trimmed query is one substring: `"foo bar"` matches only a literal `"foo bar"` run inside one field, never two separate tokens. Both sides are lowercased; there is **no diacritic folding** (`e` does not match `é`).

`readyToWork` needs the full task set (to resolve blocker completion).

### 7.10 Room tables

> Amended 2026-08-29 — see `docs/2026-08-29-cross-platform-parity-spec.md`

Mirror the separation used by both sibling clients (Dexie schema v16 / GRDB v6): **`tasks`**, **`archivedTasks`** (same shape + `archivedAt`, a separate table), **`deletedTasks`** (same shape + `deletedAt`, indexed on `deletedAt` — the 30-day trash, §8.20), **`smartViews`**, **`notificationSettings`**, **`archiveSettings`**, **`appPreferences`**, **`syncQueue`** (§9.5), **`syncMetadata`** (cursor + device identity), **`syncHistory`** (§9.7). Collections (`tags`, `subtasks`, `dependencies`, `timeEntries`) are **JSON TEXT columns** (§7.3 encoding), not child tables — matching Dexie/PocketBase/GRDB shapes. Index `quadrant`, `completed`, `updatedAt`, `dueDate`. Versioned Room migrations from day one; a row with an unrecognized enum degrades gracefully (`recurrence` → `none`) rather than failing the fetch. The schema is at **Room v2** — `MIGRATION_1_2` adds `deleted_tasks`, the first real (non-fresh-install) migration, covered by an instrumented migration test.

### 7.11 Validation

> Amended 2026-09-20: see `docs/play/2026-09-20-polish-review.md`, finding S6

Validation has two levels (`TaskValidator`).

- **`validate`** applies where the user types, and on import: the editor, capture, share, and bulk Add tag. It checks the §7.2 limits. Failures are typed errors surfaced as human copy, never silent truncation (except explicitly clamped share-capture input, §12.2).
- **`normalize`** applies on every write path, including the ones above. It trims, lowercases, and deduplicates tags and drops empty ones, turns estimate 0 into unset, and caps a snooze at 365 days. It never rejects.

A state change to a stored task only normalizes. That covers complete and reopen, the quadrant move, snooze, the timers, Duplicate, a recurrence spawn, and bulk Move, Remove tag, and Set due. A synced task can break an Android limit. Text limits count UTF-16 units here and on the web, while iOS counts characters (Appendix B). Pull stores such a task as is, and every state change on it must still work. Editing it still validates, so the editor asks the user to shorten it first, as the web does.

---

## 8. Feature Specifications

Each feature lists **Behavior** (the rule — shared across all GSD clients, authoritative) and **Android reimagining** (how it should feel). Where the original iOS spec and the shipped app diverged, this section reflects the shipped app.

### 8.1 The Matrix

> Amended 2026-09-06 — see `docs/2026-09-06-cross-platform-parity-spec.md`
> Amended 2026-09-20: see `docs/play/2026-09-20-polish-review.md`, findings 1, 2, 3, and 48
> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, findings 13, 14, and 31, and `tasks/todo.md` K, the two-button trailing swipe

**Behavior.** Tasks group into the four quadrants (§7.8). Within a quadrant, incomplete tasks sort above completed; completed tasks render dimmed with strikethrough. A global **show-completed** toggle hides/reveals completed tasks. Quadrant headers show live active counts.

**Android reimagining.**
- **Compact:** vertical stack of quadrant sections (§6.1). Reclassify via the row menu ("Move to…") — no cross-quadrant drag in a single column.
- **Expanded:** true 2×2 grid; **drag a card across quadrant boundaries** to reclassify.
- **Row interactions (both):**
  - **Swipe right (leading)** → complete/uncomplete (full-swipe commits), with the success haptic + confetti on complete.
  - **Swipe left (trailing)** → reveal **Snooze** (quick-snooze **1 hour**) and **Delete**. A part swipe opens the two buttons behind the row, Delete (`alert`) and then Snooze (Slate) at the edge, 76dp each, and a tap on one runs it and closes the row. A full trailing swipe runs Snooze, the edge-most action, as on iOS; Delete takes a tap, then offers Undo. A row that cannot snooze (Trash) keeps one trailing action, which a full swipe runs.
  - A full swipe runs its action once, when the swipe settles. The row then slides back.
  - The swipe background (green check or red bin) is drawn only while a swipe is in progress. A row at rest paints nothing behind its content, so a dimmed row or a bare line of text never sits on it.
  - **Tap** → open editor. **Tap the completion disc** → toggle complete (never opens the editor).
  - **Long-press**, or a tap on the card's overflow button → context menu: Edit, Complete/Uncomplete, Start/Stop timer, Snooze (submenu of the six presets), Duplicate (a fresh copy with new IDs for the task and its subtasks, incomplete, no time entries), Share, Move to quadrant, Delete. Delete reads in `alert`. Snooze and Move to open submenus, so each carries a trailing chevron (auto-mirrored for right-to-left). On the expanded 2×2 grid a long press starts the cross-quadrant drag instead, so there the overflow button is the way to the menu. Grid cards carry the same menu and the same blocked/blocking badges as the phone list.
  - One view model, `TaskActionsViewModel`, owns these row operations. Every screen that draws a task row builds its menu from it. The menu is the same on Matrix, on a smart view list (§8.13), and in Browse search results (§8.14). Delete offers Undo (§8.20) on all three.
- **Multi-select:** a select mode (toolbar toggle or long-press-drag) with checkmarks and the bulk-action bar (§8.11). A card in select mode has no context menu: no overflow button, and a long press opens nothing. The card is one checkbox: it leads with a check mark (a Tide check circle and a 2dp Tide border when selected, an `ink3` ring when not), shows no completion disc, and does not swipe or drag. The toolbar toggle reads as on (Tide on `tideWash`) while the mode lasts, and the bulk bar shows as soon as the mode starts, reading "Tap tasks to select them" until the first pick.
- **Empty quadrants** show the dashed in-context prompt with tailored copy (§5.10) and an "add to this quadrant" affordance. A fully empty matrix shows "Capture your first task" with an example.
- **First frame.** `MatrixViewModel.sections` is `StateFlow<List<QuadrantSectionState>?>` whose initial value is **`null`** until Room's first emission; the flag never resets for the ViewModel's lifetime. Gate order: `null` → skeleton; loaded-and-empty → the dashed prompts; else content. "Loaded" means the observer delivered its **first snapshot, even an empty one** — before that, an empty list is indistinguishable from an empty board, which is why cold starts used to flash the four dashed prompts. The **capture bar stays live** through the skeleton (it does not depend on tasks); below it sit four quadrant-section silhouettes in Q1→Q4 order, each a header (26dp icon tile, 120×16dp title bar, 24×12dp count bar) plus **3, 2, 2, 1** card silhouettes — deliberately uneven, because four identical columns read as a loading pattern rather than a preview. Each card silhouette is a 3dp accent-colored spine plus a 14dp title bar and a 10dp metadata bar on a `surface` tile. At expanded width the same silhouettes fill the 2×2 grid cells. There is **no timer and no minimum display time**: if the first snapshot lands within a frame the skeleton is never visibly drawn. Its job is removing the false empty state, not showing a loader. Primitive and shimmer: §5.10.
- Lists must stay smooth with **hundreds of tasks** (`LazyColumn` keys + stable item contents).

Task card contents: §5.6 — title, description preview with tappable links, tag chips, subtask progress, blocked/blocking badges, relative due date ("in 3 days", "Due today" in Tide, overdue in alert), recurrence glyph, live timer/tracked time, snooze remaining.

### 8.2 Quick capture + the shorthand parser

> Amended 2026-08-29 — see `docs/2026-08-29-cross-platform-parity-spec.md`. The 40-case cross-platform corpus (`core/model/src/test/resources/fixtures/cross-platform/capture-parser-corpus.json`, byte-identical to `gsd-taskmanager`/`gsd-iosapp`/the MCP server) is **the** parser contract and supersedes Appendix C's nine vectors; where this section and the corpus ever disagree, the corpus wins.

**Behavior — parser grammar (authoritative, security-relevant):** a single text field accepts a title with inline shorthand parsed on submit:

| Token | Effect | Notes |
|---|---|---|
| `!!` | `urgent = true` AND `important = true` | word boundaries; takes precedence over `!` |
| `!` | `urgent = true` | word boundaries |
| `*` | `important = true` | word boundaries |
| `#tag` | adds `tag` (lowercased) | Unicode word characters (letters/marks/digits/underscore to start, `-` as continuation only — a leading hyphen does not parse as a tag); deduplicated case-insensitively; **capped at 20 by truncation** (excess tags drop, the capture is not rejected); over-length tags (>30 chars) drop but still strip from the title |
| `http(s)://…` URL | **moved from title to description** | sanitized (below); duplicate URLs collapse to one (first occurrence) |

After parsing, the cleaned title has tokens/URLs removed and whitespace collapsed. Repeated flag tokens (`!! !!`, `! !`) all strip in one pass. No flags → the task lands in **Eliminate (Q4)** unless a manual quadrant override is set.

**URL sanitization (replicate exactly):** accept only `http`/`https`; reject embedded credentials (`user:pass@`); require a valid hostname; reject URLs ≥ 2048 chars; strip trailing sentence punctuation (`, ; : . ! ? )`); extraction is **boundary-anchored** — a URL token is recognized at word boundaries, not greedily mid-word. Valid URLs append to the description on their own lines (newline-separated from existing text), stored as the **trimmed raw string** — never WHATWG-normalized. If removing URLs empties the title but ≥1 URL was found, title becomes **"Review link below"**. Invalid/unsafe URLs stay in the title untouched.

**Android reimagining.**
- The capture bar (§5.10) shows a **live preview**: the trailing **quadrant chip** rests as "**Auto**" (neutral grid glyph) and switches to the live quadrant name + symbol in its pigment as `!`/`*` are typed; tapping it cycles the manual override `Auto → Q1 → Q2 → Q3 → Q4 → Auto` (Tab cycles it on hardware keyboards). Detected `#tags` render as chips beneath.
- **Submit** (IME action) adds the task and keeps focus for rapid serial capture. Validation failure restores the raw text inline.
- **"Details"** opens the full editor pre-filled from the parsed draft (consuming it).
- Capture is also reachable from the share target (§12.2), the app shortcut, and the widget's deep link.

### 8.3 Task editor

> Amended 2026-09-06 — see `docs/2026-09-06-cross-platform-parity-spec.md`
> Amended 2026-09-20: see `docs/play/2026-09-20-polish-review.md`, finding 44
> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, findings 6 and 7
> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, finding 30, and `tasks/todo.md` J, `TaskStore.edit` validating untouched fields

**Behavior.** Full create/edit surface for every field; save validates §7.2 limits; save disabled while the title is empty. Editing `dueDate` or `notifyBefore` **resets reminder state** (`notificationSent = false`, clears `lastNotificationAt` and `snoozedUntil`) so the user gets re-notified.

**What Save writes.** Saving an existing task does not write the row the editor opened. The editor owns twelve fields: title, description, urgent, important, tags, due date, recurrence, subtasks, dependencies, reminder offset, estimate, and snooze. For each one, the user's value is written only where the user changed it. Everything else is the row as it is in the store at the moment of the write, read inside the same transaction (`TaskStore.edit` with `EditorRules.merge`). The draft the user sees is what validates (§7.11). The merged row is only normalized, like any state change to a stored task: a field the user never touched may have arrived by sync over a limit, since iOS counts characters and Android counts UTF-16 units, and it must not fail a save that shows the old, legal value. A completion, a timer, or a snooze that landed while the sheet was open survives the save. Where the user and another writer changed the same field, the user's save wins that field, which is ordinary last-write-wins.

If the task was deleted while the sheet was open, Save writes nothing. The sheet closes with the not-found toast ("That task no longer exists"), and the task stays in Trash.

**Android reimagining** — sections in order:
- **Title** (multiline-capable field).
- **Quadrant** — a 2×2 picker mirroring the matrix: each cell titled + accent-colored; selected = accent border + wash fill; unselected = accent border at 35% opacity.
- **Tags** — chip field: type + comma/enter commits; chips have ≥48dp remove targets; autocomplete from existing tags. Each tag is a 32dp input chip (quadrant wash, accent text, a close icon) inside a 48dp touch area, and a tap anywhere on it removes the tag. The chips wrap. At 20 tags the field gives way to "A task holds at most 20 tags.", and a tag being typed stops at 30 characters, so neither limit fails only at Save.
- **Notes** (description).
- **Links** — read-only tappable list auto-detected (http/https) from the notes.
- **Due date** — a date row ("Pick a date" when there is none) that opens the Material date picker, a time row that opens the Material time picker, and the four preset chips (§8.10). Picking a date keeps the time of day already set, as iOS's date picker does. The time picker opens on the time already set, or 9:00 for an all-day date. Its **All day** button clears the time. A preset chip shows as selected when the due date falls on the day it resolves to, at any time of day. The first edit that gives a task a due date with a reminder asks for the notification permission (§11.2).
- **Reminder** — visible only when a due date is set: **"None" first, then the seven presets `0, 5, 15, 30, 60, 120, 1440` plus any off-list stored value** (defaults to the global default reminder). The option list is `ReminderOptions.including(stored: Int?)` in `:core:model`: `null` or an already-present value → the presets alone; anything else → the presets **plus `stored`, sorted ascending**. A negative stored value is off-list too — it is rendered, never snapped; the validator, not the picker, owns range rules. **An off-list stored `notifyBefore` renders as its own option rather than snapping to a neighbour**, so opening and saving a task without touching the picker leaves `notifyBefore` byte-for-byte as stored (both siblings honor this; editing never silently rewrites the value). Labels come from one pure formula, `ReminderLabel.of(minutes)` in `:core:model` (a shape mapped to plural string resources in `:app`), in this order: `0` → "At time of event"; `1` → "1 minute before"; `< 60` **or** `minutes % 60 != 0` → "{n} minutes before"; `< 1440` → "{n/60} hour(s) before"; `minutes % 1440 != 0` → "{n/60} hours before"; else "{n/1440} day(s) before". Worked: 5 → "5 minutes before"; 45 → "45 minutes before"; 60 → "1 hour before"; 90 → "90 minutes before" (not "1.5 hours"); 120 → "2 hours before"; 1440 → "1 day before"; 2160 → "36 hours before"; 2880 → "2 days before". "None" writes `notifyBefore = null` and **does not** touch `notificationEnabled` — a declared divergence from the web (which also clears the flag) and iOS (which does both).
- **Repeat** — Never / Daily / Weekly / Monthly.
- **Subtasks** — inline checklist: add, toggle, drag-reorder, swipe-delete. Each row is one checkbox (role and state for TalkBack) with a Material checkbox in `success`, and the add field has the editor's field container. At 50 subtasks the field gives way to a note, and a subtask being typed stops at 100 characters.
- **Estimate** — minutes field (1–10080) + "Tracked *Xm* of *Ym*" readout; over-estimate renders in `alert`. The field opens the number keyboard, takes at most five digits, and shows its unit ("min") and, from an hour up, its length ("· 1h 30m"). The hint reads "How many minutes it should take".
- **Field look.** Every text field in the editor (title, notes, add tag, add subtask, estimate) sits in the same `sunken` container, at least 48dp tall.
- **Snooze** — menu of the six presets + clear (shows current snooze state).
- **Dependencies** — searchable task picker that runs the cycle check live (§8.8), disabling choices that would create a cycle, with an explanation; lists current blockers/blocked.
- New tasks reserve their ID at editor-open (so dependency edges made during creation are stable). Cancel/back discards with the standard predictive-back treatment.

### 8.4 Completion (+ celebration)

> Amended 2026-09-21: see `docs/play/2026-09-20-polish-review.md`, findings 50, 51, and 52

**Behavior.** Complete sets `completed = true`, `completedAt = now`; un-complete clears `completedAt`. Completing a **recurring** task also spawns the next instance (§8.5).

**Android reimagining.** Complete via leading swipe, disc tap, context menu, notification action (§11.4), or bulk bar. A swipe, a disc tap, or a menu item always gets the success haptic and **confetti** (§5.8, suppressed under reduced motion). The card animates to its dimmed/sorted position.

One `CompletionCelebration` owns the haptic and the confetti trigger. Matrix, a smart view list (§8.13), and Browse search results (§8.14) each remember one and draw the overlay. `taskRowActions` requires it, so a screen cannot build a task menu that forgets the celebration. It lives in the composition and not in a view model, because a view model outlives a tab switch and would replay the confetti on the way back. A reopen gets the haptic and no confetti (§5.8). Two paths in the list above do not celebrate. The notification action has no screen to draw on. The bulk bar does not celebrate on purpose: iOS's bulk bar calls `bulkComplete` with no haptic and no confetti, and the web has no bulk bar at all, so a burst here would be Android-only (review finding 52). Bulk Done is a batch edit, and its feedback is the result snackbar (§8.11).

### 8.5 Recurrence engine

> Amended 2026-09-20: see `docs/play/2026-09-20-polish-review.md`, finding S5
> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, "Also worth a look" (recurrence in UTC)

**Completion is a transition.** Only a change from open to completed spawns an instance. The store reads the task inside the write transaction, so a double-fired complete sees the first one's result. A call that asks for the state the task already has writes nothing. That means no instance, no new `completedAt`, no `updatedAt` restamp, and no queue row. A stale notification action and bulk Done over finished tasks both land here.

**Behavior (authoritative).** `recurrence ∈ {none, daily, weekly, monthly}`. When a recurring task is completed, immediately create a **new task instance**:
- New `id`, `createdAt`, `updatedAt`; `completed = false`.
- `parentTaskId` = the completed task's `id` — or, if that task was itself an instance, *its* `parentTaskId` (single-level lineage to the original). Lineage is device-local (§7.2).
- `dueDate` advanced from the **prior due date**: daily +1 day; weekly +7 days; monthly +1 calendar month with month-end clamping (Jan 31 → Feb 28/29). No due date → the instance has none. The math runs on the device's calendar in its current time zone, as iOS and the web do, so a task due at local midnight stays at local midnight across a daylight-saving change. UTC math made a daily series read a day early after the autumn change.
- **All subtasks reset to incomplete.** Reminder state reset (`notificationSent = false`; `lastNotificationAt`, `snoozedUntil` cleared).
- The completed original remains as history; the new instance carries the recurrence forward. Schedule the instance's reminder at spawn time.

### 8.6 Subtasks

Up to 50 per task; title 1–100 chars; add/toggle/delete/reorder in the editor. Cards show read-only progress (`done/total` + bar; 100% = success green). Recurring completion resets them.

### 8.7 Snooze

**Behavior.** Snoozing sets `snoozedUntil = now + duration`; reminders are suppressed while snoozed and the card shows remaining time. Presets (authoritative): **15 minutes, 30 minutes, 1 hour, 3 hours, Tomorrow (+1 day), Next week (+7 days)**; max 1 year. Quick-snooze from swipe = **1 hour**. `snoozedUntil` is device-local.

**Android reimagining.** Snooze from trailing swipe (1 hour), the context-menu preset submenu, the editor, or the reminder notification's Snooze action; reschedule the alarm accordingly (§11).

### 8.8 Dependencies (blocking graph)

**Behavior (authoritative).** Up to 50 dependency IDs per task. **Cycle prevention via BFS:** before adding `B` as a dependency of `A`, walk from `B` over the dependency graph; if `A` is reachable, reject. Self-reference always rejected; every dependency ID must exist. Queries: blocking tasks, uncompleted blockers, isBlocked, blocked tasks (reverse), ready tasks (incomplete with no uncompleted blockers — powers "Ready to Work"). **On task delete, remove its ID from every other task's `dependencies` first.**

**Android reimagining.** Cards surface "Blocked by N" (lock) / "Blocking N" badges; tapping reveals the related tasks. Blocked tasks are excluded from Ready to Work but **not** visually dimmed.

### 8.9 Time tracking

**Behavior.** **Start** creates `{id, startedAt: now}`; only **one running entry per task** (second start rejected). **Stop** sets `endedAt = now` and recalculates `timeSpent` = Σ floor((endedAt − startedAt)/60) whole minutes over completed entries. Formatting: `< 1m` → "< 1m"; under an hour → "Xm"; else "Xh Ym" ("Xh" when no remainder).

**Android reimagining.** Start/Stop from the context menu and editor; a running task's card shows the pulsing dot + live `HH:MM:SS` (ticking once per second, only while visible). No foreground service, no Live-Activity analog — the timer is data, not a process.

### 8.10 Due dates & presets

**Behavior (authoritative).** Presets resolve in the device's local time zone: **None** → unset; **Today** → today; **This week** → the **Friday of the current week** (Saturday/Sunday → *next* Friday); **Next week** → the **Monday of next week** (strictly after today).

**Time of day (an Android addition).** A due date at local midnight is *all day*. That is what the presets write, and it is all iOS and the web can write: both pick a date only, iOS at start of day. Android's editor can also set a time, so a reminder can fire before a meeting at 3 PM rather than at 11:45 PM the night before. The time rides the same `dueDate` instant, so nothing changes on the wire. iOS shows the date and keeps the time on its own date edits. The web shows the date.

**Android reimagining.** Preset chips + a Material date picker and time picker; cards show relative formatting ("in 3 days", "Due today" emphasized in Tide, overdue in alert).

### 8.11 Bulk actions (multi-select)

> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, finding 13

**Behavior.** Operations (authoritative): **complete**, **move to quadrant**, **add tags**, **remove tags**, **set due date**, **delete** — each applies per-task validation, enqueues sync per task, and reports partial failures. Delete confirms first. The Archive screen's bulk bar offers **restore** and **delete permanently** (confirmed: "This can't be undone.").

**Android reimagining.** Select mode on matrix (compact + grid), smart-view lists, and archive: checkmarks, a selection count, and a **bottom action bar**. The Matrix card's selected look is in §8.1. Undo (snackbar) follows destructive actions where applicable (§8.20).

### 8.12 Archive

**Behavior.** Completed tasks can move to the separate archive store (full task shape + `archivedAt`). **Manual:** archive a completed task; restore to active; delete permanently. **Auto:** when enabled, completed tasks with `completedAt` older than `archiveAfterDays` (30/60/90) move automatically — swept on launch and by the periodic maintenance job. **Archive transitions sync as active-record transitions:** archiving enqueues a *delete* of the remote active record; restoring enqueues a *create/update*. Archived tasks themselves never sync (§9.4's reconcile ignores the archive table).

**Android reimagining.** Archive screen (Browse → Archive / drawer Library): dimmed (≈72% opacity) read-only cards; swipe to Restore (leading) or Delete-permanently (trailing, confirmed); search field; multi-select bulk bar; a toolbar menu exposing the auto-archive toggle + 30/60/90 picker + "Archive now".

### 8.13 Smart Views & filters

> Amended 2026-09-06 — see `docs/2026-09-06-cross-platform-parity-spec.md`
> Amended 2026-09-20: see `docs/play/2026-09-20-polish-review.md`, finding 48
> Amended 2026-09-21: see `docs/play/2026-09-20-polish-review.md`, finding 49

**Behavior.** A SmartView bundles FilterCriteria with a name/icon. **Nine built-ins** ship, read-only, with these exact IDs:

| ID | Name | Criteria |
|---|---|---|
| `today-focus` | Today's Focus | Q1, active |
| `this-week` | This Week | active, due this week |
| `overdue` | Overdue Backlog | active, overdue |
| `no-deadline` | No Deadline | active, no due date |
| `recently-added` | Recently Added | active, created ≤ 7 days ago |
| `weeks-wins` | This Week's Wins | completed, completed ≤ 7 days ago |
| `all-completed` | All Completed | completed, all time |
| `recurring` | Recurring Tasks | active, recurrence ∈ {daily, weekly, monthly} |
| `ready-to-work` | Ready to Work | active, no uncompleted blocker |

Users create/edit/delete **custom** views (name ≤ 60 chars; icon from 10 curated options; the full criteria editor: status segmented control, quadrant multi-select, tags, due-date predicate toggles, date range, recurrence kinds, ready-to-work, search text) and **pin up to 5** views (ordered; pinned surface first everywhere). Import and export bound the custom-view store — count, id, name, icon, and criteria sizes — per §8.16 and Appendix B.

**Ordering.** Every filtered list — a smart view's list *and* the Browse root search results (§8.14) — renders through one shared rule, `TaskOrdering.forList(tasks, status)` in `:core:model` (adopted from iOS's `TaskFilter`; before this, Android's smart-view list rendered in store order with no defined sort):

- `status == COMPLETED` → `completedAt` **descending, nulls last**; ties by `createdAt` descending.
- otherwise → `dueDate` **ascending, dated tasks before undated**; ties (same due date, or both undated) by `createdAt` descending — newest first.

**Android reimagining.** Browse tab sections on compact; drawer section on expanded (§6). Each view shows a live count; opening one shows the flat filtered list (§8.1 rows, with the full §8.1 menu and both §8.1 swipes as Matrix builds them: leading toggles completion, trailing deletes and offers Undo) with `.searchable`-style in-list search layered onto the criteria; view-aware empty states (Overdue → green check "Nothing overdue"; Wins → trophy; search-miss message; generic otherwise). The **search-miss** empty state is one shared composable with one pair of strings, rendered byte-identically here and in the Browse root search (iOS renders them the same way in both places): title `No tasks match "{query}"` (straight double quotes, the query interpolated), message "Try a different word, tag, or quadrant." It uses the §5.10 empty-state anatomy, with a 28dp search icon in `ink3`.

**First frame.** `BrowseViewModel.tasks` is `StateFlow<List<Task>?>` with an initial value of **`null`** until Room's first emission, and the smart-view list gates on it exactly as the Matrix does (§8.1): `null` → skeleton; loaded-and-empty → the view-aware empty state; else rows. The list skeleton is six rows in a `surface` container (`GsdShapes.small`, 16dp side margins, 20dp top), each row a 22dp `sunken` circle plus two bars — 14dp tall × **210dp on even rows / 150dp on odd** and 10dp × 96dp — with 16dp × 14dp row padding and 1dp `hairline` dividers inset 50dp from the left. Archive, Trash, and Settings get no skeleton — parity with iOS, and the web's Archive has none either.

### 8.14 Search & command palette

> Amended 2026-09-06 — see `docs/2026-09-06-cross-platform-parity-spec.md`
> Amended 2026-09-20: see `docs/play/2026-09-20-polish-review.md`, finding 48
> Amended 2026-09-21: see `docs/play/2026-09-20-polish-review.md`, finding 49
> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, findings 22 and 26

**Behavior.** Full-text **case-insensitive substring** search across title, description, tags, and subtask titles, live-updating. The command palette matches both **tasks** and **commands** — substring matching (not fuzzy), sectioned results: **Tasks · Smart Views · Actions · Navigation**. Actions: New task, Toggle show completed, Toggle theme, **Send feedback** (navigates to the feedback screen, §8.17 — both siblings expose feedback from the palette). Navigation: Matrix, Dashboard, Archive, Settings, and each smart view.

**Browse root search.** A search field sits at the top of the Browse screen, above the Archive row (§6.1), placeholder **"Search all tasks"**, using the same field as the in-view and archive searches: a `sunken` container (`GsdShapes.input`) at least 48dp tall, a leading `search` icon in `ink3`, the `GsdType.subhead` text with an `ink3` hint, the keyboard's Search action, and a trailing clear button ("Clear search") while the field holds text. The bare field it replaced read as a label and stood 37dp tall. It applies at **every** window size class — Browse is one composable and the bottom bar, rail, and drawer all route to it (iOS scopes its equivalent to iPhone only, because iPad and Catalyst replace the Browse tab with a sidebar; Android has no such split).

- **Swap rule.** `query = text.trim()`. Empty → the normal Browse list (Archive, Trash, Pinned, Built-in, Custom, New Smart View). Non-empty → a results list that **replaces the whole Browse list**.
- **No debounce, no minimum length, no throttle** — one non-whitespace character produces results, recomputed on every keystroke. A whitespace-only query counts as empty.
- **Nothing is persisted.** The field is plain `remember` state — not saved state, not a ViewModel field, not restored across process death; it clears when the screen leaves composition.
- **Semantics.** Results are `FilterCriteria(status = ACTIVE, searchQuery = query)` matched against the **full active task set** by the §7.9 rule — title, description, every tag, every subtask title, lowercased both sides, the whole trimmed query as one substring, no diacritic folding. Completed tasks are never returned. There is **no result cap**.
- **Order.** `TaskOrdering.forList` (§8.13), the same rule a smart-view list uses.
- **Rows and actions** are the smart-view list's composable and `TaskRowActions`: tap → editor sheet; completion disc → toggle with the usual confetti; leading swipe → toggle completion; trailing swipe → delete with Undo (§8.20); blocked/blocking badges computed over the **full** task set, not the filtered one.
- **Miss** shows the shared search-miss empty state (§8.13). Results show **no skeleton** — they exist only after the task set has loaded; Browse's composable takes the task list as an optional parameter defaulting to `null` (so previews and the Browse-only tests need not supply one), and a query typed before the first snapshot simply matches the empty set and shows the miss state.

**Android reimagining.** Invoked by the toolbar search button on any surface, or Ctrl+K / Ctrl+F on hardware keyboards. The palette opens with its query field focused and the keyboard up, at the top of its list, and a new query starts at the top of its results. Its rows carry no keys: the smart views arrive a frame after it opens, and a keyed section header used to hold the scroll where the first section had been, so the palette opened scrolled past them. Full-screen search surface on compact (keyboard-first); a centered palette dialog on expanded with arrow-key navigation + Enter. Archive keeps its own plain in-list search.

### 8.15 Analytics dashboard

> Amended 2026-09-06 — see `docs/2026-09-06-cross-platform-parity-spec.md`

**Behavior (authoritative metric set — computed from the current task set, on demand, off the main thread):**
- **Stat grid:** Active tasks, Completed tasks, Completion rate (completed ÷ total), total Tracked time. **No streaks, no badges — deliberately removed; do not add.**
- **Completion trend:** for the selected window (**7 / 30 / 90** days), per-day counts of tasks *created* vs *completed* (line/area chart).
- **Quadrant distribution:** active-task count per quadrant (donut/bar in the quadrant pigments).
- **Completion by quadrant:** `DashboardStats.quadrantStats` — **always four entries, Q1→Q4** (`Quadrant.entries` order), each a `QuadrantStat(quadrant, total, completed)` with `active = total - completed` and `completionRate = if (total == 0) 0f else completed / total`. **All-time, no window**: `total` counts every row in the quadrant, active or completed. The input is the active table only — archived and trashed rows never reach `Analytics.compute`, because the store observer reads `tasks`; that exclusion is by construction, matching iOS.
- **Top tags:** per-tag totals (bar).
- **Time by quadrant:** tracked minutes per quadrant.
- **Upcoming deadlines:** next due items (tap → editor). `upcoming` = active tasks with a due date whose **day** is on or after today, sorted by `dueDate` ascending with a `createdAt` **descending** tie-break (iOS has no explicit tie-break; Android adds one so the order is deterministic), `take(5)`. Day-based, not instant-based: a task due at 00:00 today is still upcoming at 09:00. Overdue tasks stay out of the list — the **overdue banner** (tap → Overdue Backlog view, shown when overdue > 0) owns them.
- **Deadline status.** `DeadlineStatus { OVERDUE, TODAY, UPCOMING }` with `of(due, now, zone)`: compare `due` and `now` as `LocalDate`s in the injected zone — before today → `OVERDUE`, equal → `TODAY`, else `UPCOMING`. Day-based so it agrees with `overdueCount`, `FilterCriteria.overdue`/`dueToday`, and the card's `dueLabel`, all of which already compare `LocalDate`s. Three states only — no "tomorrow", no "this week"; those stay the card's `dueLabel` vocabulary.
- **Percent formatting.** One helper, `Analytics.percent(rate) = "${(rate * 100f).roundToInt()}%"` — **rounded, never truncated** — used by the completion-rate stat card, the rings center, and the rings legend.
- **One clock, one zone.** Every date rule on the Dashboard derives from the single `now`/`zone` pair passed into `Analytics.compute`. The deadline badge is computed from `DashboardStats`, never from a second `Instant.now()` inside a composable.

**Android reimagining.** A stat-card grid (Newsreader numerals) + charts drawn in Compose (Canvas or a light chart lib) using quadrant pigments only; graceful empty state ("No stats yet") when there are no tasks.

**"Completion by Quadrant" rings card.** Positioned after the quadrant-distribution bars and before Top tags. Card chrome: `surface` fill, 1dp `hairline` border, `GsdShapes.card` (22dp), 18dp padding — the StatCard treatment at card radius; title "Completion by Quadrant" in `GsdType.quadrantTitle`, `ink`.

- **Geometry.** Outer size **196dp**, ring width **13dp**, gap **5dp**; ring `i` (0 = Q1, outermost) has diameter `196 − i × 36` → 196, 160, 124, 88. Each ring draws a full-circle track in the quadrant **wash**, then a value arc in the quadrant **accent**, round caps, starting at 12 o'clock and sweeping clockwise by `completionRate × growth × 360°`. All four on one `Canvas`.
- **Center.** The overall rate via `Analytics.percent(stats.completionRate)` in `GsdType.title` (Newsreader, `tnum`), `ink`, single line, auto-shrunk to fit the ~67dp hole; beneath it the literal lowercase "complete" in `GsdType.caption`, `ink3`.
- **Legend.** A 2×2 grid under the rings, 8dp spacing; each cell is an 11dp accent swatch (3dp radius), the quadrant title (`GsdType.subhead`, `ink2`), a spacer, then the value (`GsdType.subhead` semibold, `tnum`, `ink`). **The value is an em dash "—" (U+2014) when `total == 0`**, never "0%"; the ring still draws its track.
- **Motion.** `growth` animates 0→1 with an ease-out over **900 ms** on first composition. Under reduced motion (§13.3) `growth` is 1 from the first frame — no grow-in.
- **Accessibility.** The rings are **one** semantics node, "Completion rings, {P}% of all tasks complete". Each legend cell is its own node with `clearAndSetSemantics` — so its child text is replaced, not merged — reading "{Quadrant title}: {P}% complete", or "{Quadrant title}: no tasks" when `total == 0`. The spoken value comes from a pure `legendValue(stat)` helper, which is also what the JVM suite pins.

**Upcoming-deadline rows.** Each row: title (`GsdType.subhead`, `ink`, wraps) · badge · date (`GsdType.metadata`, `ink3`, single line, never wraps — the title yields). Date via `DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)` in the device zone. Rows are separated by 1dp `hairline` dividers, stand ≥48dp tall, and **tapping one opens the task editor** (a `TaskEditorSheet` hosted by the Dashboard, as the smart-view list already does). The badge follows `DeadlineStatus.of(dueDate, now, zone)` using the same `now`/`zone` as the stats, and is the §5.10 status capsule:

| Status | Badge |
|---|---|
| `TODAY` | "Today" — neutral capsule (`ink2` on `sunken`) |
| `OVERDUE` | "Overdue" — alert capsule (`alert` on `alertWash`). Kept so the `when` is exhaustive; not a live path, because `upcoming` starts at today |
| `UPCOMING` | no badge — the date alone says enough |

Completed tasks never appear here (`upcoming` is active-only), so they never carry a badge. The card is hidden when the list is empty.

**First frame.** The Dashboard collects the task observer with an initial value of **`null`** and gates on it exactly as the Matrix does (§8.1): `null` → skeleton; loaded-and-empty → "No stats yet"; else content. The skeleton is 20dp padding and 18dp spacing: a 2-column grid of **four 86dp** blocks (`GsdShapes.input`), then a **270dp** block and a **320dp** block (`GsdShapes.card`).

### 8.16 Import / export, erase

> Amended 2026-08-29 — see `docs/2026-08-29-cross-platform-parity-spec.md`
>
> Amended 2026-09-06 — see `docs/2026-09-06-cross-platform-parity-spec.md`
>
> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, findings 39 and 40

**Export, import, and erase finish once started.** They run in the app's process-wide scope, not the screen's, so a rotation or a tab switch cannot cancel them midway. Each reports its outcome once, in a snackbar the next time Settings is on screen. The snackbar sits over the Settings scroll, so it shows wherever the user has scrolled.

**Export — envelope 2.1.0.** A seven-store, versioned JSON document, pretty-printed (2-space indent) with **top-level keys sorted** (an Android/iOS exporter convention; the web emits insertion order — key order is not part of the import contract), dates ISO-8601 fractional-ms:

| Key | Content |
|---|---|
| `version` | the **string** `"2.1.0"` (not an int) |
| `exportedAt` | ISO-8601 ms |
| `tasks` | full camelCase Task records, **plus the derived `quadrant`** (§7.8; import still ignores it), rich `timeEntries`, null optionals omitted |
| `archivedTasks` | Task records + `archivedAt` (ISO) |
| `deletedTasks` | Task records + `deletedAt` (ISO) — the trash, §8.20 |
| `smartViews` | custom views only (`isBuiltIn: false`), **clipped to 100** — ordered `createdAt` ascending then id ascending, a deterministic clip. Android-authored rows satisfy the import bounds by construction (name ≤ 60, curated icons, validated criteria) and imported rows were bounded on the way in, so there is deliberately **no** export-side representability filter |
| `notificationSettings` | singleton, `id: "settings"` |
| `archiveSettings` | singleton, `id: "settings"` |
| `appPreferences` | singleton, `id: "preferences"` |

Via the system file-save flow (`ACTION_CREATE_DOCUMENT`) and the share sheet. The file opens in mode `wt`, which truncates: a provider may take plain `w` as an overwrite in place, and a shorter export written over a longer file then kept a stale tail. A failure says "Couldn't save the export" instead of passing in silence.

**Import — lenient, version-tolerant.** Order of guards: **10 MB UTF-8 byte cap** → structural decode → record-count cap → per-record lenient decode. **The count cap counts all three task-bearing arrays**: `tasks + archivedTasks + deletedTasks ≤ 10,000` (`smartViews` and the singletons are not counted). `version` accepts a string or a number, normalized to string; **absent is accepted and treated as `"1"`** so an old v1 export keeps importing. Every store key beyond `tasks` is **optional**, and **absent ≠ empty**: an absent key leaves the local store untouched in *both* modes; an empty array *present* in the payload clears that store in replace mode. Per-record failures skip-and-count rather than aborting the whole import — a deliberate Android leniency choice in the iOS style (the web's `importPayloadSchema` rejects the entire payload on any invalid record); reported as "imported N, skipped M". A task over a local limit (Appendix B) is a per-record failure too, so one iOS title with an emoji skips that task and nothing else. The whole import is **one transaction**: a failure part way leaves every store as it was. The file is read and parsed off the main thread; an unreadable file, a malformed one, and one over the caps each get a plain message, never an exception's text. Two modes, chosen after picking a file (`ACTION_OPEN_DOCUMENT`):

- **Replace** — `tasks`: stamp `updatedAt = now`, enqueue `update` for every imported ID **and `delete` for every existing ID not in the import**, then swap the table atomically. `archivedTasks`/`deletedTasks` (when present): clear then insert. The three singletons (when present): put — replace is the **only** mode that applies incoming settings.
- **Merge** — `tasks`: insert alongside existing; colliding IDs get regenerated (collision-checked against existing + imported + already-assigned), with `dependencies` **and `parentTaskId`** remapped through the complete ID map (forward references included); stamp `updatedAt = now`, enqueue `update` per task. `archivedTasks`/`deletedTasks`: insert; **on id collision the existing local row wins** (skip, no remap); an id already present in the imported or local `tasks` is skipped in either tombstone store — an id lives in one store, never both. `smartViews`: upsert by id, never accepting a payload row with `isBuiltIn: true`. The three singletons are **not applied** in merge — merge combines task lists and never adopts another device's configuration.
- Neither mode enqueues sync operations for `archivedTasks`, `deletedTasks`, `smartViews`, or the singletons — those stores never sync.

**Smart-view bounds on import.** The guard order above is unchanged; within `smartViews` each row is still decoded leniently, and a row is **skipped and counted in `skipped`** (smart-view skips used to be silent) when it fails to decode, carries `isBuiltIn: true`, or violates any bound in Appendix B — id 1–128, name 1–80 (the *import* bound; the editor still caps new names at 60), icon ≤ 64, `criteria.searchQuery` ≤ 600, `criteria.tags` ≤ 200 entries of 1–30 chars each (the count is deliberately not `MAX_TAGS` — a view filters over the whole vocabulary), `criteria.quadrants` and `criteria.recurrence` ≤ 4 entries. Rows are considered in document order against a **cap of 100** custom views: a row whose id already exists locally (merge mode) or was already accepted from this document **overwrites and consumes no cap room**; a genuinely new id spends one; new ids past the cap are skipped and counted. In replace mode the custom store is cleared first, so the first 100 valid rows land. `appPreferences.pinnedSmartViewIds` is truncated to the first **5** valid ids (each 1–128 chars); the singleton is otherwise applied exactly as before (replace mode only). None of this is ever fatal — an over-cap or malformed smart-view store must not abort the tasks, archive, trash, or settings travelling with it. Unknown keys on a smart-view row are **ignored**, a declared divergence: the web's `smartViewSchema` went `.strict()`, Android stays lenient. Android's `SmartView` has no `description` field, so the web's ≤ 600 bound on it is moot here.

Both cross-platform fixtures (`ios-backup-export.json`, `web-backup-2.1.0.json`, byte-identical copies at `core/model/src/test/resources/fixtures/cross-platform/`) round-trip through decode, count validation, and re-export/re-import in the JVM suite.

**Erase all data.** A guarded destructive flow: type **"RESET"** to confirm, with an export-first prompt. Clears tasks, archive, **trash**, custom views, sync queue, pins, archive settings, the pull cursor (§9.4), **and the saved feedback draft together with its submission id**; cancels reminders; **keeps theme, onboarding flags, and the share-title preference**. Both Erase All Data and *Delete account & erase everything* (§8.21) clear the feedback draft in the same step as the local wipe — a reset must not resurrect a draft. Clearing the trash on erase matches iOS (erase "must mean all of it"); the web's `resetEverything` currently omits `deletedTasks`, an upstream bug, not behavior Android copies. If signed in, it erases **everywhere**: all remote records are deleted first (§9.8); only after remote success does the local wipe proceed. If the remote step fails, nothing is erased, and Settings says so and asks the user to check the connection and try again.

### 8.17 Settings

> Amended 2026-08-29 — see `docs/2026-08-29-cross-platform-parity-spec.md`
>
> Amended 2026-09-06 — see `docs/2026-09-06-cross-platform-parity-spec.md`
>
> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, findings 20 and 29

Sections in order (Material list groups). Every tappable row is at least 48dp tall, and no option marks its choice with a text glyph such as " ✓":

1. **Appearance** — Theme (System/Light/Dark), three radio rows, each a whole 48dp target with the radio 12dp from its label; Show Completed Tasks.
2. **Sharing** — "Fetch titles for shared links" (default **off**; footer explains it performs a network fetch).
3. **Account** — signed out: Sign in with Google / Apple / GitHub as outlined pill buttons (the onboarding's, §8.19), not bare text links + a one-line privacy hint; signed in: account email, a **two-tier Apple note** when the session's provider is Apple (§10.4) — relay address (`…@privaterelay.appleid.com`): "Signed in with a private relay email — this is a separate account from your web tasks."; any other Apple sign-in: "Signed in with Apple. If you use Google or GitHub on the web, that’s a different account and won’t show the same tasks." — sync status ("Synced · N pending"), health message when unhealthy, **Sync Now**, **Sync History** (screen: totals + the ~50 most recent entries with status/counts/duration), **Sign Out**, **Delete Account…** (§8.21). There is deliberately **no** sync on/off toggle — signing in *is* enabling sync.
4. **Archive** — auto-archive toggle; "Archive after" 30/60/90 as chips ("30 days") with a selected state; **Archive Now** as its own row, apart from the day chips + status line.
5. **Notifications** — Enable Reminders; Default Reminder (15m/30m/1h/2h/1d) as chips in the editor's words ("15 minutes before") with a selected state; Sound; Quiet Hours toggle + from/to pickers (default 22:00–07:00): "Quiet from" and "Quiet until" rows print the time in the user's clock, 12 or 24 hours as the device is set, and each opens a time picker; system-permission status row with a contextual **Enable notifications** / **Open system settings** action.
6. **Data & Storage** — Export (prepare → share/save); Import (file picker → Merge/Replace choice); Erase All Data (§8.16).
7. **Feedback** — anonymous, opt-in, no auth header, no identifiers beyond the payload: sentiment toggle (up/down), category chips (idea/praise/gripe/bug), message field (≤1000 chars, live count near the limit), a roadmap vote checklist (Android's 8 slugs: `natural-language-dates, calendar-sync, task-templates, weekly-review, focus-timer, task-notes, shared-lists, wear-os`), and a **"What gets sent" preview** rendering the literal payload object, built by the same pure builder that produces the request body so preview and payload cannot drift. The draft persists locally (DataStore) and clears on success; Send is disabled while the draft is empty. Confirmation is calm ("Thanks — sent."). Wire contract: `POST https://api.vinny.io/api/collections/feedback/records`, no Authorization header, no cookies; exactly 7 snake_case keys (`submission_id`, `sentiment`, `category`, `message`, `votes`, `app_version`, `client_submitted_at` — limits in Appendix B), pinned by an exact-keys test. A 2xx is success; a 400 whose body has `data.submission_id.code == "validation_not_unique"` (a duplicate retry of the same draft) also collapses to success.

   **Failure is tiered, and every tier keeps the draft *and its submission id*** so a retry collapses server-side rather than double-filing:

   | Condition | Kind | Copy |
   |---|---|---|
   | Transport error (`IOException`) | `OFFLINE` | "Couldn't reach the server. Your draft is saved — try again when you're online." |
   | HTTP 429 — the PocketBase rate limit **or** the server hook's quota response | `RATE_LIMITED` | "Too many submissions right now. Your draft is saved — try again later." |
   | HTTP ≥ 500, including the hook's fail-closed 500 | `SERVER` | "The server had a problem. Your draft is saved — try again shortly." |
   | Any other non-2xx, including a 400 that is **not** the duplicate | `REJECTED` | "The server refused this submission. Your draft is saved." |

   Server-side facts, stated so nobody re-derives them from client behavior: the anonymous-feedback hook enforces 30 requests/minute, a 10,000-record aggregate quota, and **180-day retention**; `client_submitted_at` is an untrusted client clock kept for context only. The disclosure copy needs no change — it already states exactly what is sent, and retention is a server policy, not a payload field.

   **Declared absence.** The web's returning-user feedback nudge (a dismissible sentence under the Review stat rail, gated on tenure, completion count, and send/dismiss cooldowns) is **web-only by decision**. Android, like iOS, keeps **Settings and the command palette (§8.14) as its only feedback entry points**. Parity audits must not flag this.
8. **About** — version + build; privacy blurb; Privacy Policy → `https://gsdtaskmanager.com/privacy/`; Contact Support → `gsdapp@vinny.dev` (an email with the subject "GSD Task Manager Support"; with no email app, a toast says so); "How to use GSD" (the Field Guide, §8.19); "Show onboarding again". The mark, tagline, site link, and credit are in §8.19.

### 8.18 Task sharing (outbound)

Every card menu and the editor expose **Share**: a plain-text rendering via the system share sheet — line 1 title; line 2 quadrant title; then (if due) a medium-format date; then (if tagged) `#tag #tag …`; then a blank line + the description. No JSON, no links to servers.

### 8.19 Onboarding, Help & About

> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, findings 19 and 20, and `tasks/todo.md` K, onboarding sign-in

- **Onboarding:** 4 paged, skippable editorial screens on `paper` — **Welcome** (mark + tagline) → **The Matrix** (axes diagram) → **Capture** (shorthand legend in monospace) → **Privacy & sync** (lock motif; "no account required; your data stays on device"). Final page: "Start using GSD" (primary, ink-filled pill) + "Sign in with Google" / "Sign in with Apple" + a quiet sync hint. No slide under reduced motion: Next changes the page at once, and a swipe still follows the finger (§13.3).
  - **Layout, as on iOS.** Each page is a hero, a serif title, and one sentence, centered, scrolling when a large font outgrows the page. The heroes: the 92dp mark; a 2×2 of quadrant-wash tiles with "More important →" and "More urgent →" axes; a sample capture line ("Call my wife !! #family") over a four-row token legend; a lock in a 92dp `sunken` circle. The copy: "Get the right things done." · "Four quadrants, one decision." · "Capture in one line." · "Yours, and only yours." Skip sits fixed at the top right and the ink pill ("Next", then "Start using GSD") fixed at the bottom, so neither moves between pages. Page dots sit above the pill (TalkBack reads "Page 2 of 4"). The column is capped at 560dp and keeps clear of the system bars. The page lives in the pager's saved state, so a rotation keeps it.
  - **Sign-in.** A sign-in button starts that provider's sign-in (§10.2) and then ends onboarding, so the user comes back to Matrix whether or not they finish in the browser. If sign-in cannot start, a toast says so. The hint reads "To sync with the web app and your other devices, use the same email you use there."
- **Help ("Field Guide"):** a static reference sheet — the board & capture bar; the four quadrants with intent blurbs; quick-add syntax with the example `!! ship the deck #work #q2`; keyboard shortcuts (shown only when a hardware keyboard is relevant); editing/completing/drag; cloud sync (optional); privacy; a link to gsdtaskmanager.com. It scrolls, under the "How to use GSD" title. Each section is a quiet icon and an uppercase label over a card. The shortcuts show when the device reports a hardware keyboard or the screen is at least 600dp wide. Its copy describes shipped sync, with no "when it arrives".
- **About:** mark, tagline "Get the right things done.", version, site link, maker's credit. In Settings (§8.17) it opens with the 56dp mark, the tagline, one sentence ("GSD turns the Eisenhower matrix into a calm place to decide what matters, then do it."), and "Version X (build N)", then the §8.17 rows, a gsdtaskmanager.com row, and "Crafted by Vinny Carpenter" with the name linking to vinny.dev.

### 8.20 Undo delete & Trash

> Amended 2026-08-29 — see `docs/2026-08-29-cross-platform-parity-spec.md`
> Amended 2026-09-20: see `docs/play/2026-09-20-polish-review.md`, finding 48 (review of #18)
> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, finding 31

**Behavior.** Deleting an active task moves the full record to the trash store with `deletedAt = now`, rather than hard-deleting it. The remote `delete` is enqueued *before* the local move, in the same transaction — the enqueue-before-write invariant, extended; if the enqueue fails, the delete aborts. The store invariant is now three-state: a task id lives in exactly one of `tasks`, `archivedTasks`, `deletedTasks`.

- **Retention: 30 days**, using a **start-of-day (local) cutoff 30 days back** — following iOS; the web uses a rolling `now − 30 d` instant instead, an accepted per-client divergence. Purge deletes strictly older than the cutoff (a row whose `deletedAt` sits exactly on it is kept — strict `<`), running on launch and on the periodic maintenance sweep. Purge enqueues nothing.
- **Restore** re-activates the record **with its original timestamps preserved** — a faithful undo, matching both siblings — and **enqueues `create`**, safe because the remote record was already deleted at trash time (no LWW contest). Reminders for the restored task are rescheduled.
- **Delete forever** (single row) and **Empty trash** enqueue nothing — the remote record was already deleted when the task entered the trash.
- Trash contents **never sync** (like the archive): a remote delete arriving via SSE/reconcile removes the local active row directly, without creating a local trash entry — only the deleting device holds the recoverable copy.
- Archiving is unaffected — a separate store; archive is for completed history, trash for deletions. Restoring from trash returns to active, never to archive.
- Erase All Data clears the trash too (§8.16).

**Android reimagining.** The **undo snackbar stays** as the everyday surface and is now a *view over trash*: delete shows `Deleted "<title>"` + **Undo** (~6 s; extended ~12 s when TalkBack is active), exactly as before, but Undo now calls trash-restore instead of re-creating from an in-memory snapshot — nothing is lost when the snackbar times out anymore. A **Trash screen** lives in Browse as a sibling row below Archive (icon `delete_outline`, live count): dimmed read-only rows showing time remaining ("29 days left", `ink3`; final day in `alert`), swipe to Restore (leading) / Delete forever (trailing, confirmed by a dialog titled `Delete "<title>" forever?` over "This can't be undone."), a multi-select bulk bar (Restore / Delete forever), and a toolbar "Empty trash" action (confirmed). Deep link `gsd://trash` (§6.4). Empty state: "Trash is empty" with the §5.10 treatment. No blocking confirmation dialogs for single deletes; calm undo instead. Bulk delete and archive/trash-permanent-delete still confirm (irreversible at scale). The offer also ends when its screen leaves: a tab switch does not bring the same offer back on the next visit. A second delete replaces the first offer.

### 8.21 Account switching, account deletion, recovery

> Amended 2026-09-06 — see `docs/2026-09-06-cross-platform-parity-spec.md`
>
> Amended 2026-09-20: see `docs/play/2026-09-20-polish-review.md`, finding S3
>
> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, follow-up S3 and finding 40

- **Account switch:** when a sign-in resolves to a **different** account than the last known owner *and* local active tasks exist, prompt: **"Different account"** — *Keep my tasks* (merge: local tasks seed-upload into the new account), *Start fresh (erase tasks on this device)* (destructive), *Cancel* (signs back out). Archived tasks never trigger the prompt (they don't sync).
  - **What the decision does.** `SyncCoordinator.confirmOwner` reads the previous owner, erases local data for *Start fresh*, runs the pure `OwnerChange.adopt` in `:core:sync`, and records the new owner last. A different owner **clears the pull cursor**, so the new account is listed in full. *Keep my tasks* also **seeds the queue once**: every active task with no pending or failed row gets one `UPDATE` row carrying the task as stored, with no restamp. Those rows make reconciliation `PROTECT` the tasks until the push lands them, and last-write-wins still decides against anything already in the new account. A same-owner or first sign-in changes nothing, so a task deleted on another device while this one was signed out still deletes here.
  - **The prompt survives a restart.** The token is saved before the prompt is answered. `SyncCoordinator.start` compares the token's user id with the last confirmed owner. When they differ and active tasks exist, it raises the prompt again and stays signed out, so nothing syncs before the user decides. The email and provider of an unconfirmed sign-in are saved as a *pending sign-in* before the decision, so the rebuilt prompt keeps both, and Settings shows the email and the right account note once the user answers. Answering clears the pending sign-in.
  - **An unfinished change with nothing to ask about finishes on the next launch.** When the token's user differs from the last confirmed owner and no active tasks exist, `start` clears the pull cursor and records the owner before it signs in. That covers a "Start fresh" that died after its erase. It also covers a first sign-in that died before it recorded its owner. Without a recorded owner, a later switch to another account would find nothing to compare with and would never ask.
- **Delete account:** confirmation offering *Delete & keep tasks on this device* or *Delete & erase everything* (+ Cancel), warning it's permanent and removes every synced task. The deletion itself is **route-first with a bounded fallback**, orchestrated by a pure `AccountDeleter` in `:core:sync` over the `SyncRemote` plus a small `AccountRemote` port (`deleteAccountViaRoute()`, `deleteUser(id)`), so the whole outcome matrix runs on JVM fakes:
  1. **Preferred:** `DELETE /api/gsd/account` with the auth header. **`204`** means the server erased every task and the user in one transaction — proceed to sign-out (and local erase, if that option was chosen). **Only a `404` falls back** (the route is not deployed — true of `api.vinny.io` today, so the fallback is the live production path). Any other failure, **including a transport error, stops with nothing changed locally.**
  2. **Fallback:** up to **3 passes** of *enumerate* (under §9.4's bounded, fail-closed listing rules) → delete each record with the existing ~100 ms throttle. The **user record (`DELETE /api/collections/users/records/{id}`) is deleted only after a complete listing comes back empty**, then the token is cleared. Retrying is therefore always safe: the user record is never deleted before the task wipe finishes.
  3. **The outcome is surfaced, always** — the call used to be wrapped in a silent `runCatching`. The outcome line renders **outside the signed-in branch** of the Account group, so it survives the sign-out that a successful deletion performs and the user still sees what happened: listing failed before any delete → "Couldn't list your synced tasks, so nothing was deleted. Check your connection and try again."; some tasks deleted then auth rejected → "Some synced tasks were deleted, but your account is still there. Sign in again, then run Delete account once more."; some tasks deleted then any other failure, or three passes exhausted → "Some synced tasks were deleted, but your account is still there. Try again to finish deleting it."; cloud deleted but the local erase threw → "Cloud account deleted, but local erase was incomplete. Use Erase All Data to retry local cleanup." Success keeps the unchanged sign-out flow. *Delete & erase everything* also clears the saved feedback draft (§8.16).
- **Database-unavailable recovery:** if the local DB cannot open even after recovery attempts, show a calm full-screen fallback — "Couldn't open your tasks", reassurance (data not deleted; restart; free storage), Contact Support — and **never** start sync or observation against a broken store.

---

## 9. Sync & Backend

Sync is **optional and opt-in**. With no account the app is fully functional offline and nothing leaves the device. Signed in, tasks sync bidirectionally with the self-hosted **PocketBase** at **`https://api.vinny.io`**, shared with the web app, the iOS app, and the MCP server.

> Build a minimal PocketBase client (REST + SSE) — treat it as net-new, higher-risk code with its own recorded-fixture test suite. Everything below is the **shipped, live-verified protocol** (it corrects a few details of the original iOS spec; where they differ, this is authoritative).

**Auth header quirk (applies to every authenticated request, including realtime subscribe and auth-refresh):** the header is the **raw JWT** — `Authorization: <token>` with **no `Bearer ` prefix**.

### 9.1 The `tasks` collection (authoritative wire model)

snake_case fields. The collection exists; read/write these exact names:

| Field | Type | Notes |
|---|---|---|
| `id` | string | the PocketBase **record** id (15 chars, server-generated) — distinct from the task's own id. Send `""` (or omit) on create; read the real id from the response and key PATCH/DELETE on it |
| `task_id` | string | the app's Task `id` — the cross-client join key; unique per owner |
| `owner` | string | the authenticated user id (the JWT's `id` claim). Server API rule scopes rows to `owner = @request.auth.id` |
| `title`, `description` | string | |
| `urgent`, `important` | bool | |
| `quadrant` | string | **write** the §7.8 id (`urgent-important` …); **ignore on read** — always recompute from the flags |
| `due_date`, `completed_at`, `last_notification_at`, `snoozed_until` | string | ISO-8601 ms (`T`/`Z`) or `""` for nil |
| `completed` | bool | |
| `recurrence` | string | `none`/`daily`/`weekly`/`monthly` |
| `tags` | json [string] | |
| `subtasks` | json [{id,title,completed}] | |
| `dependencies` | json [string] | |
| `notification_enabled`, `notification_sent` | bool | device-local semantics (§4.7) |
| `notify_before`, `estimated_minutes` | number/null | omit when nil. **PocketBase returns absent numbers as `0`** — treat `estimated_minutes == 0` as unset |
| `time_spent` | number | `timeSpent ?? 0` |
| `time_entries` | json | flattened `{id, startedAt, minutes}` — note `startedAt` stays **camelCase** inside the otherwise snake_case record |
| `client_updated_at` | string | ISO-8601 ms — **the LWW key** |
| `client_created_at` | string | ISO-8601 ms |
| `device_id` | string | originating device, for realtime echo filtering |
| `created`, `updated` | string | PocketBase system autodates, **space-separated** form (`2026-06-10 12:00:00.123Z`). Never encode them on write. `updated` is **the pull cursor** (completeness); it is **never** used for conflict decisions |

**`parentTaskId` has no wire column** — recurrence lineage stays device-local.

Decode defensively: only `task_id` is required; every other field tolerates absent/empty/null. Skip a record with an unparseable `client_updated_at`. Date parsing is lenient (normalize the space separator to `T`; accept whole-second and fractional forms); date *writing* is always fractional-ms `T`/`Z`, and nil dates write `""`.

### 9.2 The task mapper (local ⇄ wire)

**Push (local → wire):** map camelCase → snake_case per the table; `updatedAt → client_updated_at`, `createdAt → client_created_at`; flatten each time entry to `minutes = max(0, floor((endedAt − startedAt)/60))` — a **running** entry (nil `endedAt`) flattens to `minutes = 0`; `notes`/`endedAt` are dropped (the wire form is lossy).

**Pull (wire → local):**
- No local copy → reconstruct everything from the wire; time entries get `endedAt = startedAt + minutes×60`, `notes = nil`; `parentTaskId = nil`.
- Local copy exists (remote won LWW) → remote wins for synced fields, but **preserve from local:** `parentTaskId`, `notificationSent`, `lastNotificationAt`, `snoozedUntil`, **and `timeSpent` + `timeEntries`** (prefer the rich local copy over the lossy wire form).
- `quadrant` is always recomputed from `urgent`/`important`.

### 9.3 Conflict resolution — last-write-wins

Compare `client_updated_at` vs local `updatedAt` as **millisecond integers**: remote newer → take remote; local newer → keep local; **equal or unparseable → no-op** (never overwrite). The same guard protects both directions: on push, a queued item is **skipped** when the remote's `client_updated_at` is newer than the queued payload's `updatedAt` (the next pull delivers the remote version).

### 9.4 Pull, cursor, and deletion reconciliation

> Amended 2026-08-29 — see `docs/2026-08-29-cross-platform-parity-spec.md`
>
> Amended 2026-09-06 — see `docs/2026-09-06-cross-platform-parity-spec.md`
>
> Amended 2026-09-20: see `docs/play/2026-09-20-polish-review.md`, finding S1

- **Endpoint:** `GET /api/collections/tasks/records?page={n}&perPage=200&sort=updated,id&filter=updated >= "{cursor}"` (filter URL-encoded; page through **all** pages until `page ≥ totalPages`; response envelope `{page, perPage, totalItems, totalPages, items}`). No owner filter is sent — the server's API rule scopes rows. The decoded page is `TaskListPage(page, totalPages, totalItems, items, updatedStamps)` — `totalItems` sits **directly after `totalPages`** and defaults to `items.size`, which is what lets the bounded-listing rules below read it on every walk.
- **Cursor:** the max applied **server `updated`** stamp, stored in the PocketBase space-separated form; first sync uses `"1970-01-01 00:00:00.000Z"`. After a successful sync, advance to `maxApplied − 5 seconds` (the overlap rewind that catches boundary writes). A record whose `updated` is unparseable still applies but doesn't advance the cursor. An owner change clears the cursor (§8.21), and so does a local erase, in the same transaction as the data (§8.16). The cursor means "everything up to here is on this device", so it never outlives the tasks it describes. Nothing else clears it.
- **Apply loop:** per record — validate; skip malformed (never abort the pull); apply LWW (§9.3); on upsert preserve device-local fields (§9.2). Count remote-won overwrites as `conflictsResolved`.
- **Tombstone suppression (archive + trash).** Before the LWW upsert, check whether the record's `task_id` is in `archivedTasks` or `deletedTasks`. If so, compare the remote `client_updated_at` against that row's `archivedAt`/`deletedAt` as millisecond instants: remote **strictly newer** → the remote edit wins — remove the tombstone row and apply the record as a normal active upsert (deliberate cross-device resurrection: the user edited it elsewhere after archiving/deleting here); otherwise (older, equal, either timestamp missing or unparseable) → **drop the record, fail closed** — never resurrect on bad data. Without this guard a remote record for a locally-archived-or-trashed task would resurrect as an active duplicate. This mirrors the web's `isRemoteNewerThanArchive` guard, extended to both tombstone stores; iOS's pull/SSE paths do not yet have it (sibling backlog item, not Android's to fix).
- **Bounded, fail-closed listings.** Every full-listing walk — the pull loop, the remote index, remote erase (§9.8), and the account-deletion fallback (§8.21) — goes through one `BoundedListing.collect(...)` with `MAX_REMOTE_TASKS = 10,000` (page size stays 200). It **throws `RemoteListingIncomplete` and never returns partial data** when: `totalItems` exceeds the cap; the returned `page` is not the one requested; `totalItems` changes between pages; a page comes back empty before the last page; the materialized item count exceeds the cap or, at the end, differs from `totalItems`; or the walk sees a **duplicate `task_id`**. Consequences: the pull aborts and the run reports `error`; **deletion reconciliation is skipped for that run** — a partial or over-cap enumeration may never drive deletion; remote erase and account deletion abort before touching anything. The stable tie-break `sort=updated,id` exists so paging cannot reshuffle rows mid-walk. One consequence worth naming: a remote record that fails lenient decode is dropped from `items` but still counted in `totalItems`, so the final `all.size != expectedTotal` check trips — correctly, fail-closed — on **every** listing until that row is repaired or deleted server-side; there is no client-side retry that fixes it.
- **Deletion reconciliation** (runs **last**, after push, destructive, and **only after a complete listing**): fetch a fresh full remote index (`task_id` set) and classify every local **active** task that is absent from it by the state of its sync-queue rows — the three-verdict rule:

  | Queue state for the task | Verdict | Effect |
  |---|---|---|
  | any **pending** row | `PROTECT` | keep it — edit-beats-delete under LWW |
  | no pending, any **failed** row | `ABANDON` | **move the task to Trash** with `deletedAt = now`, delete all of its queue rows in the same transaction, enqueue nothing |
  | neither | `APPLY` | delete the local row — no trash entry (§8.20's remote-delete rule stands) |

  `ABANDON` trashes where `APPLY` does not because an abandoned task's content **never reached the server**: the local copy is the only copy, and the retry-exhausted queue row was shielding it forever (or would eventually have let it vanish). Trash keeps it recoverable for 30 days. The port is `SyncLocalStore.abandonToTrash(id, deletedAtMs)`; the Room transaction itself lives in `:core:store` as `GsdDatabase.abandonToTrash(id, deletedAtMs)` over `TaskEntity.toDeleted`, with `RoomSyncLocalStore` delegating to it — the store owns its own transaction boundary. The archive and trash tables are otherwise never touched. All of this is safe only because of the **enqueue-before-write invariant**: every local mutation enqueues its sync operation *before* the row becomes visible.

  **Confirmed owner only.** The server's list rule filters rows, so a guest request answers 200 with zero items. An empty listing therefore proves nothing about the account by itself. `runSync` resolves the owner once and throws `NotSignedIn` before pull, push, or reconciliation when there is none; the result carries `notSignedIn = true`. Before the first `ABANDON` or `APPLY` verdict, reconciliation calls `SyncRemote.confirmIdentity(ownerId)`, which `PocketBaseApi` implements with `POST /api/collections/users/auth-refresh` and a record-id comparison. That endpoint needs a valid token whatever the collection rules say. A rejection, a different record id (`IdentityMismatch`), or a rate limit aborts the run with an error and changes nothing. `PROTECT` changes nothing, so a run with nothing to reconcile makes no extra request. A confirmed owner with an empty remote still applies the deletions.

### 9.5 Push & the sync queue

> Amended 2026-09-06 — see `docs/2026-09-06-cross-platform-parity-spec.md`
> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, "Also worth a look" (two full listings per run)

Every local mutation (create/update/delete/archive/restore/import/bulk) enqueues a queue row: `{ id, taskId, operation: create|update|delete, timestamp (ms), retryCount, payload (full Task JSON for create/update; null for delete), status: pending|failed, lastError?, lastAttemptAt?, failedAt? }`.

Push algorithm:
1. **Bulk-fetch the remote index once per push** (list since epoch, through the bounded fail-closed walk of §9.4). A run with nothing queued takes no index, so it lists the whole collection only once, for reconciliation: map `task_id → (recordId, client_updated_at)` — avoids per-item lookups and 429s. If the walk throws `RemoteListingIncomplete`, the push does not proceed on partial data.
2. Drain pending items oldest-first, each gated by backoff (`retryCount` → wait **5s / 10s / 30s / 60s / 300s**).
3. **A malformed remote version never authorizes an overwrite.** For a create/update whose task *does* have a record in the remote index, if that record's `client_updated_at` is missing or unparseable (`PbDates.parse` → `null`), the item is **skipped for this drain** — `bumpQueueRetry(…, "Remote task version is missing or malformed", …)`, counting toward the five-failure threshold like any other error — and **no write is issued**. Previously this case fell through to `updateTask`. A missing version may protect or quarantine data; it may not license clobbering it.
4. Apply the **LWW payload guard** (§9.3); then create (`POST /api/collections/tasks/records`, `id:""`) / update (`PATCH …/{recordId}`) / delete (`DELETE …/{recordId}`, 204). Record a newly-created `recordId` into the live index so a same-drain delete finds it.
5. **Throttle ~100 ms between operations.** On HTTP **429, abort the entire push loop** immediately (items stay pending for the next cycle).
6. Other errors bump `retryCount` + `lastError`; at **5 failures** the item becomes `failed` — kept, surfaced for retry, never dropped. Failed items auto-requeue (reset to pending) on **launch, foreground, network-regained, and manual sync** — deliberately *not* on the periodic cadence or post-mutation pushes, so a poison item can't hammer a rejecting server every two minutes.

### 9.6 Realtime (SSE) + cadence safety net

> Amended 2026-08-29 — see `docs/2026-08-29-cross-platform-parity-spec.md`

Protocol (live-verified):
1. `GET /api/realtime` with `Accept: text/event-stream`, no auth, no timeout. Frames use `id:`/`event:`/`data:` lines (tolerate no space after the colon; `:` comment lines are heartbeats; an event is delimited by a blank line; multiple `data:` lines join with `\n`).
2. First frame: `event:PB_CONNECT`, `data:{"clientId":"…"}` — extract the clientId (missing → reconnect).
3. Subscribe: `POST /api/realtime` with the bare-JWT auth header, JSON body `{"clientId":"<id>","subscriptions":["tasks"]}` → 204.
4. Task events arrive as `event:tasks`, `data:{"record":{…full record…},"action":"create"|"update"|"delete"}`.

Apply rules: enforce owner **fail-closed** (non-empty `record.owner` must equal the current JWT's user id, else drop). For create/update: **echo-filter** — drop when `record.device_id` equals this device's id; drop when a local **delete** is pending for that `task_id` (don't resurrect); apply the same **tombstone-suppression** check as the pull apply loop (§9.4) — an archived/deleted `task_id` resurrects only when the incoming `client_updated_at` is strictly newer than the tombstone's `archivedAt`/`deletedAt`; else LWW-upsert with device-local preservation. For delete: drop when *any* queue item is pending for that task (deletes carry the last writer's `device_id`, so they are **not** echo-filtered — the queue check is the guard); else delete locally (no trash entry is created for a remote delete — §8.20).

Lifecycle: SSE runs **foreground only**. On stream end/error: run a full sync (catch missed events), then reconnect with exponential backoff 1s → cap 30s (healthy stream resets it). **Safety net:** a full sync every **120 s** while active, plus on launch, foreground, network-regained, and a **1.5 s debounced push** after each local mutation. One sync at a time — concurrent triggers coalesce.

### 9.7 Sync history & health

- Record every attempt: `{ id, timestamp, status: success|error|conflict|partial, pushedCount, pulledCount, conflictsResolved, failedCount?, errorMessage?, duration?, deviceId, triggeredBy: user|auto }`. Precedence: error > partial (some pushes failed) > success; remote-won LWW counts as informational conflict, not failure. `triggeredBy = user` only for manual syncs. **Prune to the 500 most recent** after each insert; the history screen shows ~50.
- **Health evaluation** (priority order): offline → session expired (token expiry ≤ now) → failed items exist → stale queue (oldest pending > 1 hour) → OK. Surface as the quiet status chip (§5.10) + a human message in Settings; never alarming.

### 9.8 Device identity & remote erase

> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, finding 42

- On first run generate a stable `deviceId` (UUID) + a human `deviceName` (model); persist locally. `deviceId` populates `device_id` on pushes (echo filtering) and sync history.
- **Moved data gets its own id.** The id lives in the database, and a phone-to-phone transfer (§13.4) copies the database. A marker file in `noBackupFilesDir`, which no transfer copies, says this install already has its own id. Without it, `SyncCoordinator.start` replaces the stored id before anything reads it, and writes the marker. Two phones with one id each dropped the other's realtime events as echoes until the next pull.
- **Erase-all-remote** (used by erase-everywhere and account deletion): list the full remote index fresh, `DELETE` every record (~100 ms throttle), and only clear the local queue after **all** deletes succeed. A dead session throws (so callers refuse to wipe local data on a false "success"); signed-out is a silent no-op.

---

## 10. Authentication

### 10.1 Providers

**Google, GitHub, and Sign in with Apple** — all already configured on the PocketBase backend and all delivered through PocketBase's **web OAuth flow** (no Google Play sign-in SDK, no provider SDKs). Onboarding offers Apple + Google; Settings offers all three (parity with iOS).

### 10.2 The flow (OAuth2 + PKCE via Custom Tabs)

> Amended 2026-09-20: see `docs/play/2026-09-20-polish-review.md`, finding 4

1. `GET /api/collections/users/auth-methods` → `oauth2.providers[]`, each `{name, displayName, state, authURL, codeVerifier, codeChallenge, codeChallengeMethod}`. **PocketBase generates the PKCE material per attempt** — hold it locally, never cache it.
2. Build the authorization URL: the provider's `authURL` already ends with `redirect_uri=` — append the percent-encoded redirect URI.
3. **Redirect URI: `https://api.vinny.io/ios-oauth-redirect/`** — a server-side bounce page that re-emits the provider callback to the **`gsd://` custom scheme**. This bounce is what makes Apple work (Apple posts `response_mode=form_post` to an HTTPS URL; the page forwards `code`/`state` to the app scheme). Android registers an intent filter for the `gsd` scheme and receives the same bounce. *(Despite the "ios-" name it is client-agnostic; see Open Questions §17.)*
4. Launch the URL in a **Custom Tab**; the `gsd://` callback re-enters the app with `code` + `state` query params. A Custom Tab runs in GSD's task, so the callback closes it and reaches the one `MainActivity` through `onNewIntent` (§6.4). A device with no Custom Tabs provider opens a full browser in its own task, and that browser stays open.
5. **Validate `state`** against the provider's issued state (mismatch → abort).
6. `POST /api/collections/users/auth-with-oauth2` with JSON `{"provider","code","codeVerifier","redirectURL"}` → `{token, record:{id, email, …}}`.
7. Persist the token (Keystore-encrypted, §3). The JWT's `id` claim is the `owner` for pushed records; a token without an `id` claim fails fast.

### 10.3 Token lifecycle

> Amended 2026-09-20: see `docs/play/2026-09-20-polish-review.md`, finding S1
> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, follow-up S1

- PocketBase JWTs live ~14 days; there is **no refresh token**. When the token expires within a **7-day skew**, call `POST /api/collections/users/auth-refresh` (bare-JWT auth header, no body) → fresh `{token, record}`.
- A **transient** refresh failure (offline, 5xx) falls back to the still-valid stored token — offline-first means network failures **never** sign the user out. Clear the stored token **only** on a definitive 401/403.
- Decode the JWT payload locally (base64url, no signature verification) for `id` and `exp` only.
- **Session end.** A definitive rejection clears the token, and the next run reports `notSignedIn`. So does a 401 or 403 from any call in a run, the identity check (§9.4) included: the server can revoke a token long before it enters the 7-day window, and the refresh never sees that one. The coordinator then clears the token, so a restart cannot sign back in to the dead session, and sets `signedIn = false` and `sessionExpired = true`, and Settings shows "Session expired. Sign in again to keep syncing." above the sign-in options. Signing in again clears the flag. A dead session never syncs as a guest (§9.4).

### 10.4 Identity caveats

> Amended 2026-08-29 — see `docs/2026-08-29-cross-platform-parity-spec.md`

- **Cross-provider identity — corrected.** Per the pinned PocketBase v0.39.10 source (`apis/record_auth_with_oauth2.go`), email-based auto-linking is **built-in and unconditional**: on sign-in, PocketBase matches an existing `externalAuth` (provider + providerId) first, else falls back to `FindAuthRecordByEmail` on the provider-verified email, else creates a new user. There is no admin-UI setting to flip. A user who signs in with Google on the web and Apple on Android converges to the **same** account whenever both providers report the same verified email — distinct data spaces arise only when the verified emails genuinely differ, chiefly Apple private relay (verified, but unique per app). Surface provider choice clearly regardless; recommend users pick the same provider everywhere as the simplest mental model.
- **Apple private relay:** an `…@privaterelay.appleid.com` email means Apple relay — show the two-tier account note in Settings (§8.17); it deliberately over-warns for the same-email case, mirroring the shipped iOS copy. It also implies no email-based convergence with other providers, since the relay address is unique per app.
- Account switching semantics: §8.21.

---

## 11. Notifications & Background (a redesign, not a port)

iOS pre-schedules every reminder with the system notification center; Android's analog is **exact alarms**. Same user-visible outcome: reminders fire on time with no polling.

### 11.1 Scheduling model (behavior shared with iOS)

> Amended 2026-09-20: see `docs/play/2026-09-20-polish-review.md`, finding 5
> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, "Also worth a look" (alarm cap)

- A task with a `dueDate`, `notificationEnabled == true`, and a reminder offset (`notifyBefore`, else the global default) gets a reminder at `dueDate − offset` — unless `snoozedUntil` is in the future, in which case the fire time **is** `snoozedUntil`. That holds after the first reminder posted too: a snooze asks for another reminder, so `notificationSent` does not block it. A posted reminder with no future snooze does not fire again.
- **Reschedule** whenever `dueDate`, `notifyBefore`, `notificationEnabled`, snooze, or completion changes; **cancel** on complete, delete, or disable. Editing `dueDate`/`notifyBefore` resets reminder state (§8.3). Recurring spawns schedule their own reminder at spawn time.
- **Quiet hours:** a fire time inside the quiet window defers to the window's end.
- One stable identity per task (`task-<id>`) so reschedules replace, never stack.
- **Resync on any data change:** remote pulls and SSE writes bypass local mutation hooks, so a debounced (~1 s) full reminder resync runs off the store's change stream — reminders stay correct no matter which device edited the task. A resync that throws is logged and the stream keeps running, so the next change still reschedules.
- **At most 400 alarms at once.** Android refuses more than 500 concurrent alarms per app and throws when an app passes the limit. A resync schedules the soonest 400 fire times (equal times break by task id) and cancels the rest. A later resync schedules the next ones, and every posted reminder triggers one. iOS keeps the soonest 64 pending notifications on its own, so it has no rule here.

### 11.2 Android mechanics

> Amended 2026-09-20: see `docs/play/2026-09-20-polish-review.md`, finding 47
> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, findings 8 and 27, and "Also worth a look" (time zone changes)

- `AlarmManager.setExactAndAllowWhileIdle` for fire times, with the **`SCHEDULE_EXACT_ALARM`** permission (user-grantable special access, denied by default on Android 14 and later). If exact alarms are not permitted, degrade gracefully to inexact windows and say so in Settings — never silently fail. Settings then shows "Exact alarms are off, so reminders may arrive a few minutes late." and an **Allow exact alarms** row that opens the system's page for GSD (`ACTION_REQUEST_SCHEDULE_EXACT_ALARM`). Settings re-reads both permissions each time it resumes, so the rows go away as soon as the user comes back with the permission granted.
- A `BroadcastReceiver` posts the notification at alarm time: title = task title, body = description (if any), tap → `gsd://task/<id>`. Small icon: the launcher's monochrome tile cluster (`ic_notification`), tinted with Tide (`tint`), category `reminder`. A post that fails is logged, never thrown. The tap is that link and nothing else, so the router goes to Matrix first and the task opens from any tab (§6.4).
- **Notification channels:** *Reminders* (default importance, sound) and *Reminders (silent)*; the in-app **Sound** toggle selects the channel (channel sound is user-owned on Android — the two-channel split is the honest mapping).
- **Runtime permission:** `POST_NOTIFICATIONS` requested **contextually** (first reminder enablement / first due date with reminder), never at cold launch. Reminders default to on, so the Settings switch alone never asked. The editor asks once, the first time an edit gives a task a due date with a reminder or changes the reminder on a dated task, while reminders are on and the permission is neither granted nor asked before (`ReminderPermission.shouldAsk` in `:core:model`), as iOS asks when a reminder is picked. Track `permissionAsked`; reflect OS state in Settings with a path to system settings when denied.
- **Reboot / update / system changes:** alarms die on reboot — a `BOOT_COMPLETED` (+ `MY_PACKAGE_REPLACED`) receiver reschedules everything from the store. The same receiver also takes `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` (a grant turns every inexact alarm exact), `TIMEZONE_CHANGED`, and `TIME_SET`, because a new zone or clock moves quiet-hours deferrals. A reschedule that fails is logged, never thrown.
- Mark `notificationSent`/`lastNotificationAt` when a reminder posts (device-local, §4.7), through `TaskStore.markReminderSent` so the write never restamps `updatedAt` or queues a push.

### 11.3 Badges

Android has no numeric app-icon badge API; the notification channel's badge dot is the platform behavior. The iOS-style due-soon badge count has **no Android analog** — accepted delta.

### 11.4 Notification actions (Android-native win)

> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, follow-up S6

Reminder notifications carry two action buttons: **Complete** (full completion semantics — recurrence spawn, sync enqueue) and **Snooze 1 hour**. Both act through the store without opening the app and dismiss/update the notification. A write that fails is logged and never thrown, because a throw there would crash the process from a notification button. The notification then stays up, so the user can try again or open the task.

### 11.5 Background work

One periodic **WorkManager** job (~hourly, battery-friendly constraints): auto-archive sweep (§8.12) + an **opportunistic sync** when signed in (Android can do what iOS couldn't reliably — data is fresh on next open). Reminders never depend on background work; they're pre-scheduled alarms.

---

## 12. Android-Native Surfaces

### 12.1 Home-screen widget (Glance)

> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, finding 28

**Today's Focus** — the one widget (parity with shipped iOS):
- Sizes: small (~2×2; up to 3 tasks) and medium (~4×2; up to 5), "+N more" overflow, "All clear" empty state, "Today's Focus" header with the crosshair glyph. Each task row is 36dp tall (they were about 23dp), and the widget shows as many rows as its height fits, one at least and five at most (`widgetRows`): three at the usual two-cell height, five once resized to about 254dp (resizable to 360dp).
- Picker: the label "Today's Focus" (the receiver's own label, not the app's "GSD"), the description "Your Do First tasks, a tap away on the home screen.", and a preview layout drawn in the widget's own tokens and dark palette, with the header and three sample rows.
- Content: the Q1-active task list (title + optional due). Styled with GSD tokens (paper/surface, ink, Rust accents) in both light/dark.
- Data: a precomputed snapshot pushed by the app on every relevant data change (Glance is push-updated — no timeline needed).
- Taps: widget body → `gsd://smart-view/today-focus`; each row → `gsd://task/<id>`.

### 12.2 Share target (the Share-Extension analog)

> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, findings 9 and 41, and "Also worth a look" (styled shared text)

An `ACTION_SEND` (`text/plain`) share target: sharing a URL or text from any app opens a compact **"Add to GSD"** compose sheet. The activity's window is see-through (`Theme.Gsd.Share`) and the sheet is a Material bottom sheet that scrolls, so it opens over the sharing app, clear of the status bar and the keyboard, in the in-app theme. `EXTRA_TEXT` and `EXTRA_SUBJECT` are read as `CharSequence`, because some apps share styled text and `getStringExtra` drops it.
- Editable **Title** — prefilled from shared text; a bare URL takes the sharing app's `EXTRA_SUBJECT` as its title when that is present and is not itself an address (browsers put the page title there, as iOS's share sheet carries it), and otherwise derives a readable title from the URL (host/path words). Empty → **"Review link below"**. Clamped to 80 UTF-16 units, never cutting a surrogate pair in half.
- **Quadrant picker** defaulting to **Eliminate (Q4)**.
- Comma-separated **tags** field with live chips (normalized: trim/lowercase/dedupe/≤30 chars/max 20).
- Read-only **Link** section: URLs sanitized by the §8.2 rules land in the description (newline-joined, clamped to 600).
- **Add** writes directly through the store (same process — no outbox dance needed) and enqueues sync; errors keep the sheet open, with a validation message as written or plain copy for anything else. Optional post-save title enrichment (fetch the page title) only when the "Fetch titles for shared links" setting is on and the title is still URL-derived. It follows iOS: the address is the first line of the description and must be `https`; the fetch reads at most 64 KB with an 8-second timeout; `og:title` wins over `<title>` (`PageTitleParser` in `:core:model`, the same cases as iOS's tests); and the title is re-checked inside the write, so a title the user edited meanwhile is never overwritten. The task appears at once; the new title lands when the fetch returns, or never if it fails.

### 12.3 App shortcuts & assistant surface

- Static launcher shortcuts: §6.5.
- There is **no good Android analog** of iOS App Intents/Siri/Spotlight in 2026 (Assistant App Actions are deprecated); skip rather than ship a broken voice surface. The MCP server already provides the AI/automation path against the shared backend. Revisit if a stable Android assistant-integration API lands.

### 12.4 System integration baseline

Predictive back; edge-to-edge; per-app language support wiring (English at launch); direct-share is out of scope.

### 12.5 Launcher icon

The adaptive icon (§5.9): pigment-tile foreground, paper background, monochrome layer for themed-icon launchers.

---

## 13. Non-Functional Requirements

### 13.1 Offline-first (a correctness constraint)

Fully functional with no network and no account. All reads/writes hit the local store synchronously-fast; sync is a background reconciliation layer, never on the critical path of a user action. Optimistic UI everywhere; failures surface as calm, undoable errors. **UI copy must never imply data leaves the device when it doesn't.**

### 13.2 Performance

Cold start to interactive quickly (baseline profiles); the matrix smooth with hundreds of tasks; sync, analytics, and import/export off the main thread; the widget reads a snapshot, not the database.

### 13.3 Accessibility (baseline — required)

> Amended 2026-09-06 — see `docs/2026-09-06-cross-platform-parity-spec.md`
> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, finding 12

- **TalkBack:** every interactive element labeled; cards expose a coherent reading order (title, quadrant, due, status) and **custom accessibility actions** for complete/snooze/edit/delete (swipe gestures are not the only path). Composite decorations collapse to a single node rather than reading their parts: a loading skeleton is one node labelled "Loading" (§5.10); the completion rings are one node ("Completion rings, {P}% of all tasks complete"); each rings legend cell is its own node reading "{Quadrant title}: {P}% complete" or "{Quadrant title}: no tasks" (§8.15).
- **Font scale:** `sp` everywhere; layouts reflow at 2× / non-linear scaling; no clipped fixed-height text. Every row of chips or tags wraps onto more lines (a `FlowRow`) instead of squeezing its last chips into narrow columns: the editor's due, repeat, and tag chips, card and capture tags, the share sheet, feedback, the smart view editor, Dashboard's range chips, and the bulk tag dialog.
- **Reduced motion:** confetti and non-essential animation suppressed when animations are disabled (§5.8) — one gate, `ValueAnimator.areAnimatorsEnabled()`, for all of it. It also suppresses the **rings grow-in** (`growth` is 1 from the first frame, §8.15) and the **skeleton shimmer** (the sweeping band is not drawn at all; the silhouette stays static, §5.10). Suppression means *no animation*, not a faster one.
- **Contrast:** the quadrant accents meet WCAG **AA** against their card backgrounds in both modes (the §5.1 values are pre-verified — don't "brighten" them).
- **Touch targets ≥ 48dp**; full keyboard operability on hardware keyboards.

### 13.4 Privacy & security

> Amended 2026-09-24: see `docs/play/2026-09-20-polish-review.md`, finding 16

Local-only by default; **no analytics/tracking**. Tokens Keystore-encrypted, never logged. **No cloud backup:** `allowBackup="false"`, and the data extraction rules exclude every domain from cloud backup, so the Settings line "Your tasks never leave this device unless you sign in to sync" holds. A phone-to-phone transfer is the user moving their own data, so it carries tasks and settings, but never the sign-in token (its Keystore key stays on the old phone), and the device id is replaced on arrival (§9.8). HTTPS only. The §8.2 URL sanitizer is security-relevant — replicate exactly. Logging is structured with secret/content masking: never log task content or tokens.

### 13.5 Error handling

Typed errors (not-found / validation / network / auth); human, actionable user copy; technical detail only in logs; sync errors always recoverable (queue persists; manual retry).

### 13.6 Testing & verification

- **JVM unit tests** (`:core:model`) for all pure logic — port the shared behavior as fixtures: capture-parser grammar (incl. URL sanitization edge cases), quadrant derivation, recurrence date math (month-end clamping), BFS cycle detection, the filter pipeline, time-spent calculation, analytics math, import merge/ID-remap (forward references), validation limits.
- **Sync engine tests** (`:core:sync`, JVM) against recorded PocketBase fixtures: LWW both directions (incl. equal-ms no-op), device-local preservation, deletion reconciliation with queue protection, cursor advance/5s rewind + space-form dates, 429 abort, backoff schedule, echo filtering, SSE frame parsing (PB_CONNECT, multi-line data, comment heartbeats), lossy time-entry round-trip.
- **Room migration tests** (instrumented) per schema version.
- **Compose UI tests** for the matrix, editor, and adaptive layouts; a launch smoke test per release.
- TDD (red → green → refactor) per the adopted coding standards. The JVM suite is the gate and must stay fast.

---

## 14. Google Play Requirements

- **Data Safety form (accurate to behavior):** local-only mode collects nothing and shares nothing. With optional sync: task content is stored on the developer's self-hosted backend, tied to an account identifier; encrypted in transit; user-deletable (in-app account deletion — which Play requires to be discoverable, and which §8.21 provides). No third-party sharing, no ads, no analytics SDKs.
- **Account deletion:** Play mandates an in-app path *and* a web-accessible deletion method for apps with account creation — the in-app flow exists (§8.21); the web deletion URL is an owner item (§17).
- **Permissions:** `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM` (with the Play Console declaration for exact-alarm use: user-facing task reminders), `RECEIVE_BOOT_COMPLETED`, `INTERNET`. Nothing else.
- **Target API:** current Play minimum (target the latest stable SDK).
- **Listing:** name "GSD — Get Stuff Done" (or match the App Store listing), privacy policy `https://gsdtaskmanager.com/privacy/`, phone + tablet screenshots (Play surfaces large-screen quality ratings — the 2×2 grid is the tablet hero shot).

---

## 15. Phased Roadmap

Each phase independently buildable and testable; sync remains the highest-risk phase — budget accordingly.

- **Phase 0 — Foundations.** Repo, Gradle modules (`:core:model` / `:core:store` / `:core:sync` / `:app`), theme system (§5 tokens, Newsreader, dark mode), Room schema v1 + migrations scaffold, the Task model + embedded types, ID generation, validation. JVM tests green.
- **Phase 1 — Core local app.** Matrix (compact stack + expanded 2×2), capture bar **with the full parser**, editor, complete/uncomplete + confetti + haptic, delete + undo snackbar, show-completed, theming. Pure offline. Parser/quadrant/validation fixtures ported.
- **Phase 2 — Task depth.** Due dates + presets, recurrence engine, subtasks, snooze, dependencies + BFS, time tracking.
- **Phase 3 — Organization & insight.** Archive (manual + auto), smart views (9 built-ins + custom + pinning), search + command palette, dashboard, import/export, onboarding/Help/About.
- **Phase 4 — Notifications.** Exact alarms, channels, runtime permission flow, quiet hours, boot rescheduling, notification actions, the WorkManager maintenance job (sans sync).
- **Phase 5 — Sync.** The PocketBase client (REST + SSE), Custom-Tabs OAuth + PKCE (all three providers), Keystore tokens, the LWW engine (pull/push/reconcile/queue/history/health), account switch/delete, erase-everywhere, opportunistic background sync. Live-backend validation early; recorded fixtures throughout.
- **Phase 6 — Native surfaces.** Glance widget, share target, app shortcuts, deep-link hardening.
- **Phase 7 — Play readiness.** Data Safety form, adaptive icon + listing assets, accessibility audit (TalkBack + 2× font + reduced motion), baseline profile, internal testing track.

> Phases 0–4 ship a genuinely useful local-only app — consistent with "sync is optional."

---

## 16. Open Questions & Risks

1. **The OAuth bounce page** (`https://api.vinny.io/ios-oauth-redirect/`) — verify it is client-agnostic (it forwards to the `gsd://` scheme, which Android also registers). If anything about it is iOS-conditional, add a parallel `android-oauth-redirect` (server-side, trivial). **Owner: backend. Blocking for Phase 5.**
2. **Cross-provider identity** — unchanged ecosystem-wide caveat (§10.4): provider mix-ups create separate data spaces. Mitigated by guidance copy; account linking remains a backend wishlist item.
3. **Web-accessible account deletion URL** — required for the Play Data Safety form (§14). **Owner: backend/site.**
4. **Exact-alarm permission UX** — if Play policy review pushes back on `SCHEDULE_EXACT_ALARM`, fall back to inexact alarms + copy honesty. Low risk for a task-reminder app; verify at submission.
5. **`gsd://` collision** — both GSD apps can't conflict on one device's scheme (Android and iOS namespaces are separate — non-issue — but keep route parity so shared docs/links behave identically).
6. **Newsreader rendering** — verify optical weight against iOS "New York" at the display sizes; adjust weight (regular→medium) if it reads thin on OLED dark.
7. **PocketBase page-size ceiling** — pulls page at 200; users beyond ~10k tasks are untested ecosystem-wide (import caps at 10k). Accepted.
8. **iOS ID-length quirk** — iOS emits some 12-char task IDs (an internal inconsistency); all clients must accept any URL-safe ID ≥ 4 chars. Android emits 21 and must not validate others' lengths.

---

## Appendix A — Authoritative enumerations (quick reference)

- **Quadrants:** `urgent-important` (Do First) · `not-urgent-important` (Schedule) · `urgent-not-important` (Delegate) · `not-urgent-not-important` (Eliminate). Order Q1→Q4.
- **Recurrence:** `none`, `daily`, `weekly`, `monthly` (no yearly).
- **Snooze presets:** 15 min, 30 min, 1 h, 3 h, Tomorrow (+1d), Next week (+7d); quick-snooze 1 h; max 365 days.
- **Reminder offsets (editor):** none, 0 (at time), 5, 15, 30, 60, 120, 1440 min. **Default-reminder presets (settings):** 15, 30, 60, 120, 1440 (default 15).
- **Due-date presets:** None · Today · This week (Friday; weekend → next Friday) · Next week (Monday).
- **Archive after:** 30 / 60 / 90 days (default 30).
- **Trend windows:** 7 / 30 / 90 days.
- **Sync:** cadence 120 s · mutation debounce 1.5 s · push throttle ~100 ms · backoff 5/10/30/60/300 s · max 5 retries then `failed` · pull `perPage` 200 · cursor rewind **5 s** · SSE reconnect 1 s → 30 s cap · history kept 500 / shown 50 · stale-queue threshold 1 h · token refresh skew 7 d.
- **Built-in smart-view IDs:** `today-focus`, `this-week`, `overdue`, `no-deadline`, `recently-added`, `weeks-wins`, `all-completed`, `recurring`, `ready-to-work`.

## Appendix B — Field limits

> Amended 2026-08-29 — see `docs/2026-08-29-cross-platform-parity-spec.md`
>
> Amended 2026-09-06 — see `docs/2026-09-06-cross-platform-parity-spec.md`
>
> Amended 2026-09-20: see `docs/play/2026-09-20-polish-review.md`, finding S6

Text limits count UTF-16 code units, as the web does (`String.length`). iOS counts characters, so one emoji is one there and two or more here. A title that is legal on iOS can arrive over the limit, and §7.11 says what happens then.

| Limit | Value |
|---|---|
| ID | ≥ 4 chars (emit 21; time entries 8; smart views 12) |
| Title | 1–80 chars |
| Description | 0–600 chars |
| Tag | 1–30 chars, max 20, lowercase |
| Subtask title | 1–100 chars, max 50 |
| Dependencies | max 50 |
| Time entries | max 1000; note ≤ 200 chars |
| Estimated minutes | 1–10080 (0 = unset) |
| Smart-view name | 1–60 chars **in the editor** (import accepts up to 80 — see the smart-view bounds below) |
| Pinned views | max 5 |
| Share-capture title | clamped to 80 chars |
| Import | counts `tasks + archivedTasks + deletedTasks` ≤ 10,000; ≤ 10 MB |
| Feedback `submission_id` | 1–64 chars |
| Feedback `message` | ≤ 1000 chars |
| Feedback `app_version` | ≤ 20 chars, clamped by truncation |
| Remote listing | ≤ 10,000 tasks per full walk (`BoundedListing.MAX_REMOTE_TASKS`), 200 per page — over the cap the walk throws rather than returning partial data (§9.4) |

**Smart-view bounds** (`SmartViewLimits`) — enforced on import, and the count on export (§8.16):

| Limit | Value | Applies to |
|---|---|---|
| `MAX_SMART_VIEWS` | **100** | custom views accepted per import, and emitted per export |
| Smart-view id | 1–128 chars | import |
| Smart-view name | 1–**80** chars | import (the editor still caps new names at 60) |
| Smart-view icon | ≤ 64 chars | import |
| `criteria.searchQuery` | ≤ 600 chars | import |
| `criteria.tags` | ≤ **200** entries, each 1–30 chars | import — the *count* is deliberately not the 20-tag task limit; a view filters over the whole vocabulary |
| `criteria.quadrants` / `criteria.recurrence` | ≤ 4 entries each | import |
| `appPreferences.pinnedSmartViewIds` | ≤ 5 entries, each 1–128 chars | import (truncated, not rejected) |

## Appendix C — Capture-parser test vectors (superseded)

> Amended 2026-08-29 — **superseded by the 40-case cross-platform corpus** (§8.2), `core/model/src/test/resources/fixtures/cross-platform/capture-parser-corpus.json`. The nine vectors below predate the settled Unicode-tag, duplicate-URL, repeated-flag, and boundary-URL decisions in §8.2 and are kept only as a historical sample — do not port them as fixtures; port the corpus.

| Input | Result |
|---|---|
| `Ship the deck !! #work` | title "Ship the deck", Q1, tags [work] |
| `Call mom !` | title "Call mom", urgent only → Q3 |
| `Plan trip *` | title "Plan trip", important only → Q2 |
| `Read newsletter` | Q4 (no flags) |
| `Check https://example.com/a then report !` | URL moved to description, title "Check then report", Q3 |
| `Read https://example.com/doc.` | URL captured **without** the trailing `.`, moved to description |
| `https://example.com` | title "Review link below", URL in description |
| `Fix login !!#auth #AUTH` | tags dedupe → [auth] |
| `Danger http://user:pass@evil.com` | URL rejected → left in title |

*— end of specification —*
