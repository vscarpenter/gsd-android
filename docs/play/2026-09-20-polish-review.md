# Pre-production polish review (2026-09-20)

Scope: a polish and launch-readiness review of 0.9.0 (versionCode 9) before the production application.
No code changed in this review. Every finding below cites a file and line, and most were reproduced on a device.

## Method

- Built `:app:assembleDebug` from `main` at `1c1aa9c` and installed it on the `gsd-shot` emulator (API 36, 1080x1920, 420 dpi).
- The install upgraded an existing 0.7.0 debug install in place. The Room v1 to v2 migration ran cleanly and kept the seeded data.
- Walked every top-level surface in light mode, then dark mode, font scale 1.3 and 2.0, landscape, and a simulated 1280x800 dp tablet window.
- Exercised capture, the editor, swipe gestures, the long-press menu, select mode, Trash, and the command palette.
- Exercised the native surfaces too: the share target, the widget, launcher shortcuts, and `gsd://` deep links.
- Three read-only reviewers (accessibility, design, release readiness) read the code in parallel. I checked each of their claims against the code or the device before listing it here.
- Evidence labels: "live" means reproduced on the emulator. "code" means confirmed by reading the cited lines.

## Verdict

Not ready for a production launch. The core Matrix, capture flow, Dashboard, dark palette, splash, and launcher icon are in good shape.
Six defects put task data at risk, twenty more break visible behavior, and four items touch Play policy or listing accuracy.
The production application and its review take days, so the fix work can run in parallel with it.

## Data safety and sync (fix first)

The release-readiness pass found these by reading `:core:sync` and `:core:store`. I re-read every cited line.

Status, 2026-09-20: S1 is fixed on branch `fix/sync-dead-session-guard` (seven commits, not merged, not pushed).
The first red test reproduced the loss with no server involved: a null owner plus an empty listing deleted the synced task.
The engine now aborts without an owner and confirms the owner with the server before it trashes or deletes anything.
The server's guest response is still unverified, and it no longer matters for safety.

S2 is fixed on branch `fix/reminder-bookkeeping-lww`, stacked on the S1 branch (three commits, not merged, not pushed).
The receiver now records a posted reminder through `TaskStore.markReminderSent`, which never restamps `updatedAt` and queues no push.
A user-initiated snooze still restamps and syncs, because web and iOS both do.

S3 is fixed on branch `fix/account-switch-keeps-tasks`, stacked on the S2 branch (five commits, not merged, not pushed).
A JVM test reproduced the loss: the first run against the new account deleted both already-synced tasks.
An owner change now clears the pull cursor, "Keep my tasks" seeds the queue once, and an unanswered prompt comes back after a restart.
S4 to S6 remain open.

| # | Finding | Evidence |
|---|---|---|
| S1 | A dead session can make sync delete every synced task on the device. `ensureFreshToken()` clears the token on a 401, but `_signedIn` stays true and `runSync` still runs. Only `push()` checks for an owner. `pull()` and `reconcileDeletions()` then list as a guest. An empty listing passes `BoundedListing`, and every task without a queue row is hard-deleted with no Trash entry. The cursor never resets, so signing in again does not bring them back. iOS guards all three phases on the token, and the web guards its pull. One assumption is unverified: that PocketBase answers a guest listing with 200 and zero items, which is how a filter rule behaves. My request to the API was blocked by permissions. Settle it with one signed-out GET, or a JVM test with `ownerId = { null }` and an empty remote. | code, `sync/SyncCoordinator.kt:63-67`, `AuthManager.kt:69-81`, `SyncEngine.kt:84-89`, `:123`, `:197-208`, iOS `SyncEngine.swift:177`, `:234`, `:268`, web `pb-pull.ts:113-116` |
| S2 | A firing reminder restamps `updatedAt` and queues a full push. The receiver records `notificationSent` through `TaskStore.update`, which stamps the task and enqueues an UPDATE. That bookkeeping write then wins last-write-wins. Complete a task on the web at 8:55, let the phone remind at 9:00, and the task becomes incomplete everywhere. | code, `notifications/ReminderReceiver.kt:74-77`, `TaskStore.kt:36-44`, `SyncEngine.kt:96-103`, `:151-154` |
| S3 | "Keep my tasks" on an account switch deletes the kept tasks at the next sync. `confirmOwner` never re-enqueues them, so reconciliation sees them as absent from the new account and deletes them. The pull cursor never resets on sign-out, erase, or owner change, so the new account's older tasks never arrive. | code, `sync/SyncCoordinator.kt:189-195`, `SyncEngine.kt:113`, `LocalErase.kt:14-24` |
| S4 | After the New Task shortcut or `gsd://capture` runs once, the New Task sheet reopens on every return to the Matrix tab. The request counter is a `StateFlow` that never resets, and each new collector replays it. `openNew` replaces any open draft with a blank task. Rotation also re-runs the launch deep link, because `onCreate` handles the intent on every recreation. Rotating on Settings threw me to Matrix with a New Task sheet. | live (twice, plus rotation), `AppContainer.kt:93-101`, `matrix/MatrixScreen.kt:80-82`, `MainActivity.kt:100`, `editor/TaskEditorViewModel.kt:31-44` |
| S5 | Completing an already-completed recurring task spawns another instance. `setCompleted` never checks the prior state, so a stale notification action or a bulk Done over finished tasks duplicates the series. | code, `TaskStore.kt:51-68` |
| S6 | Any mutation of a task whose synced title passes 80 UTF-16 units crashes. Pull skips validation, every mutation validates the whole task, and the view model actions and receivers catch nothing. iOS counts graphemes, so a legal 80-character iOS title with one emoji is over the Android limit. | code, `TaskValidator.kt:31`, `matrix/MatrixViewModel.kt:54-59`, iOS `Validation.swift:64` |

## Broken behavior (fix before promoting to production)

| # | Finding | Evidence |
|---|---|---|
| 1 | The "..." overflow button on every task card does nothing. Its handler is an empty lambda. TalkBack announces it as "Task options". The menu opens only on long-press. | live, `matrix/TaskCard.kt:128` |
| 2 | The Trash screen draws every row over the solid red swipe background. "Restore" and "30 days left" measure about 1.1:1 contrast, and "Delete forever" is red on red (1.0:1). The row content sits at 72% alpha inside a `SwipeToDismissBox` whose background is always composed. | live, `browse/TrashScreen.kt:163-168` and `:238`, `matrix/SwipeTaskRow.kt:55-66` |
| 3 | Swipe-to-delete never shows the Undo snackbar (3 of 3 tries). Delete from the long-press menu shows it. Probable cause: `confirmValueChange` fires twice, the second `store.delete` returns null, and that null overwrites `_lastDeleted`, which cancels the snackbar coroutine. | live, `matrix/MatrixViewModel.kt` `delete()`, `matrix/SwipeTaskRow.kt:38-50`, `matrix/MatrixScreen.kt:227-239` |
| 4 | A `gsd://` deep link or widget tap does not navigate when the app is already running. Each one stacks another `MainActivity` (three deep after two tries), and the visible copy stays on Matrix. `MainActivity` has no `launchMode`, and a hidden instance consumes the shared `pendingRoute`. | live, `AndroidManifest.xml:15`, `MainActivity.kt:149`, `widget/GsdWidget.kt:72-74` |
| 5 | "Snooze 1 hour" on a reminder never fires again. The receiver sets `notificationSent = true`, `snooze()` does not reset it, and the planner returns null on that flag before it reads `snoozedUntil`. The iOS scheduler has no such gate on its snooze branch. | code, `ReminderPlanner.kt:24`, `ReminderReceiver.kt:76`, `TaskStore.kt:71-74`, iOS `LiveReminderScheduler.swift:29-31` |
| 6 | The editor has no date or time picker. The four presets are the only way to set a due date, and each resolves to midnight. With the default 15 minute offset a reminder fires at 11:45 PM the night before. A task due "Today" never fires. Spec 8.3 and 8.10 call for a Material date picker. | live and code, `editor/EditorDepthSections.kt:56-86` |
| 7 | The app never asks for the notification permission in the normal flow. Reminders default to on, so the "first enablement" ask never runs. Only a text row in Settings can request it. Spec 11.2 also names "first due date with reminder". | live and code, `settings/NotificationsGroup.kt:61-70`, `:120` |
| 8 | On Android 14 and later, exact alarms are denied by default. Settings shows "Exact alarms unavailable" as plain text with no action. The listing says reminders "arrive exactly on time". Add a row that opens `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`, or soften the listing. | live on API 36, `settings/NotificationsGroup.kt:126-128`, `docs/play/listing.md` |
| 9 | The share sheet draws its title under the status bar and camera cutout. The activity is opaque, so a blank default-grey page covers the sharing app. It ignores `EXTRA_SUBJECT`, so a shared Chrome page becomes "www.theverge.com 2026 9 18 android 17 review". It has no scroll container. | live, `share/ShareActivity.kt:46-48`, `:60-65`, `AndroidManifest.xml:33-35` |
| 10 | The text caret is invisible in dark mode in all 17 text fields. `BasicTextField` defaults to a black cursor and no call site sets `cursorBrush`. Black on the dark sunken fill measures 1.09:1. | live, 17 `BasicTextField` call sites, zero `cursorBrush` |
| 11 | In landscape and on tablets the content draws under the status bar. Only the compact `Scaffold` branch applies insets. The module has zero `WindowInsets` or `statusBarsPadding` calls. | live, `MainActivity.kt:202-231` |
| 12 | Editor chip rows do not wrap. At font scale 1.3 the "Next week" chip breaks onto two lines. At 2.0 it collapses into a one-letter-wide column about 200 dp tall, and "Monthly" does the same. The module has zero `FlowRow` calls. | live, `editor/EditorDepthSections.kt:67-84`, `:120-133` |
| 13 | Select mode shows no selected state on any card, and no indicator that the mode is on when the selection is empty. `TaskCard` has no `selected` parameter. The disc still says "Mark complete" while it toggles selection. | live, `matrix/MatrixScreen.kt:142-148`, `matrix/TaskCard.kt:67-74` |
| 14 | The tablet 2x2 grid has no card menu at all. `MatrixGrid` calls `TaskCard` without `onLongPress`, and the "..." button is dead (finding 1). Snooze, Duplicate, Share, Move to, and the timer are unreachable there. | code, `matrix/MatrixGrid.kt:104-111` |
| 37 | Outside the phone layout the screen has no paper background. `GsdTheme` draws no `Surface`, so the rail and drawer layouts show the platform window color. With the in-app theme set against the system theme, the "GSD" wordmark and the counts turn unreadable. | live, `theme/GsdTheme.kt:74-76`, `MainActivity.kt:202-231` |
| 38 | Status bar icons follow the system theme, not the in-app theme. Dark in-app on a light system gives dark icons on dark paper. `enableEdgeToEdge()` runs once with its automatic style. | live, `MainActivity.kt:98` |
| 39 | Export has no error handling and opens the file in "w" mode, which need not truncate. Overwriting a larger backup can leave trailing bytes that break the file. Import reads the whole file outside its `try`, parses on the main thread, and merges one transaction per task, so a failure leaves a partial import. | code, `settings/SettingsScreen.kt:72-80`, `:244-256`, `ImportApplier.kt:43-45` |
| 40 | Account deletion, erase, and import run in the composition scope. Rotating or leaving Settings cancels them midway, and the outcome message never appears. | code, `settings/AccountGroup.kt:46-49`, `settings/SettingsScreen.kt:217-222` |
| 41 | "Fetch titles for shared links" is a dead setting. Nothing reads the preference, and its footer warns about a network request that never happens. | code, `settings/SettingsScreen.kt:65`, `:113-116` |
| 42 | A restored or transferred install shares its `deviceId` with the old phone, because the id lives in the backed-up database. Each phone then drops the other's realtime events as echoes until the next pull. | code, `RoomSyncLocalStore.kt:59-64`, `Sse.kt:78` |

## Play policy and listing accuracy

| # | Finding | Evidence |
|---|---|---|
| 15 | The app has no privacy policy link. The only URLs in shipped code are the API base and the OAuth redirect. Play's User Data policy asks for the policy inside the app when the app handles personal data, and sync sends an email address and task content. Add a row under About that opens `https://gsdtaskmanager.com/privacy/`. | code, URL grep across `app/` and `core/` |
| 16 | Auto Backup is on. The manifest sets no `allowBackup`, `dataExtractionRules`, or `fullBackupContent`, and `dumpsys package` reports `ALLOW_BACKUP`. The task database uploads to the user's Google backup while Settings says tasks "never leave this device". Add data extraction rules that exclude cloud backup, or change the copy. On target 31 and later, `allowBackup="false"` alone does not stop device-to-device transfer. Exclude the token file and the device identity there (finding 42). | live (`dumpsys`), `AndroidManifest.xml:7-13`, `strings.xml` `settings_privacy_blurb` |
| 17 | `docs/play/data-safety.md` predates the feedback form and says a local-only user sends nothing. Feedback sends free text with no account. Check the Console form against that. The privacy page source never mentions Android, feedback, or backups, and it names only iPhone, iPad, and Mac. It is also the filed deletion URL. I did not fetch the live page. | code, `docs/play/data-safety.md:3-4`, `core/sync/.../FeedbackApi.kt`, `gsdtaskmanager.com/app/privacy/page.tsx:91` |
| 18 | `versionName` is 0.9.0. The checklist refers to "post-1.0 candidates", so decide whether production ships as 1.0.0. | `app/build.gradle.kts:23-24` |

## High-impact polish

| # | Finding | Evidence |
|---|---|---|
| 19 | Onboarding is a stub against spec 8.19. It has no brand mark, no axes diagram, no lock motif, no page indicator, no animation, and no filled primary button. The Skip and Next buttons move between pages because the whole column is centered. The capture legend wraps after a lone "!" on a 411 dp phone. The page index uses `remember`, so rotation restarts it. | live, `onboarding/OnboardingFlow.kt:37-69` |
| 20 | The Field Guide is one paragraph with no scroll container. It still says to sign in to sync "when it arrives", and sync shipped in phase 5. Spec 8.19 lists sections, the shorthand example, and a site link. About lacks the mark, tagline, site link, and credit the spec names. | live, `nav/GsdNavHost.kt:80-87`, `strings.xml:297` |
| 21 | Only Matrix has the brand lockup top bar. Browse, Dashboard, Settings, and every pushed screen start cold under the status bar with no title bar and no Up arrow. The bottom bar loses its selected state on child routes because it compares routes for equality. | live, `MainActivity.kt:190`, `:206`, `:221` |
| 22 | The Browse search field has no container, no icon, and no clear button. It reads as a label, and its touch target is 37 dp tall. | live, `browse/BrowseScreen.kt:180-191` |
| 23 | Material's baseline purple shows in the selected navigation label in both themes. The Material navigation items read `secondary`, and the theme leaves it unmapped. | live (full-resolution crop), `theme/GsdTheme.kt:21-73` |
| 24 | `MaterialTheme` receives no `shapes` or `typography`. Filter chips render as 8 dp rectangles (spec 5.5 says pills), menus at 4 dp, dialogs at 28 dp, and snackbars at 4 dp. The account-switch dialog falls back to default Material type. | live and code, `theme/GsdTheme.kt:75`, `MainActivity.kt:112-115` |
| 25 | Save is a quiet text button at the end of an eleven-section scroll. The half-expanded sheet hides it. Swiping the sheet down discards edits with no prompt, and Back collapses the sheet before it closes. | live, `editor/TaskEditorSheet.kt:172-178` |
| 26 | The command palette opens scrolled to the bottom of its list (2 of 2 tries). The smart views load one frame late and the keyed section header anchors the scroll. The palette also never focuses its query field. The module has zero `FocusRequester` calls. | live, `palette/CommandPalette.kt:43-75` |
| 27 | Reminder notifications use `android.R.drawable.ic_dialog_info` as the small icon. They set no color and no category. | code, `notifications/ReminderReceiver.kt:51` |
| 28 | Both launcher shortcuts show blank circles because `shortcuts.xml` sets no icons. The widget picker shows a generic preview, the label "GSD", and the title as the description. Widget rows are about 23 dp tall. | live, `res/xml/shortcuts.xml`, `res/xml/gsd_widget_info.xml`, `widget/GsdWidget.kt:95-100` |
| 29 | Settings: the theme radio sits about 2 dp from its label. Selected options append a " ✓" text glyph. "Archive now" looks like one of the day options. Quiet hours are fixed at 22:00 to 07:00 and print in 24-hour time. Action rows measure under 48 dp. Sign-in options are bare text links. | live, `settings/SettingsScreen.kt:97-107`, `:141`, `settings/NotificationsGroup.kt:81`, `:95`, `strings.xml:252` |
| 30 | Editor details: the tag chip is 48 dp tall. Subtask toggles are borderless sunken discs with no checkbox role or state. The estimate shows a bare "90" with no unit and no numeric keyboard, and its hint prints the raw limit. Due chips never show a selected state. "Add tag" and "Add subtask" have no field container. The tag row has no cap and no wrap. | live, `editor/TaskEditorSheet.kt:242-264`, `editor/EditorDepthSections.kt:74-76`, `:155-164`, `:213-221`, `strings.xml:79` |
| 31 | The long-press menu gives Delete no destructive color and gives Snooze and Move to no submenu cue. The delete-forever dialog has no title and does not name the task. | live, `matrix/TaskRowWithMenu.kt:62-78`, `browse/TrashScreen.kt:175-190` |
| 32 | Five surfaces use a bare line of text for their empty state: Dashboard, Archive, Trash, smart view lists, and sync history. Spec 5.10 defines the tile, icon, serif headline, and sentence. `view_empty_generic` reads "Nothing here". | live and code, `browse/TrashScreen.kt:132-135`, `browse/SmartViewListScreen.kt:104-111` |
| 33 | No list animates. The module has zero `animateItem` calls, so completing a task jumps it to its new position. Spec 5.8 asks for snappy list reorders. | code |
| 34 | The accent spine is a fixed 40 dp tall, so it covers only the top of cards that carry tags, metadata, or subtasks. | live, `matrix/TaskCard.kt:85-91` |
| 35 | Card titles clamp to two lines with an ellipsis at every font scale. At 2.0 a title reads "Renew passport befo...". Spec 5.4 says unlimited lines at accessibility scales, and `accessibility-audit.md` says the cap lifts. At 2.0 the bottom bar also splits "Dashboard" mid-word. | live, `matrix/TaskCard.kt:98` |
| 36 | A phone at 840 dp or wider in landscape gets the tablet drawer. With a display cutout on that side the drawer labels break mid-word ("Brows" then "e"). Medium width keeps a single stretched column with no maximum width. | live on a simulated window, `MainActivity.kt:216-231` |

## Copy

- `strings.xml` holds 13 em dashes in user-visible strings, which breaks the house voice rule. The lines are 173, 216 to 219, 230, 235, 255, 271, 290, 291, 293, and 297.
- Labels mix title case and sentence case in one screen. "Sync Now", "Sign Out", and "Delete Account" sit beside "Export tasks" and "Erase all data". One error string says "Erase All Data" while the button says "Erase all data".
- Two actions each have two names. Marking a task complete is "Complete" in the row menu and "Done" in the bulk bar. The irreversible delete is "Delete permanently" in Archive and "Delete forever" in Trash.
- Hardcoded English lives in Kotlin: the due labels (`TaskCard.kt:262-265`), the palette labels (`PaletteIndex.kt:6-17`), and the "1d" style option labels. Two fallback strings join them: "Import failed" (`SettingsScreen.kt:255`) and "Review link below" (`ShareCapture.kt`).
- Two places show a raw exception message to the user: `SettingsScreen.kt:255` and `ShareActivity.kt:146-148`.
- Plurals: `trash_days_left` prints "1 days left" and `bulk_delete_confirm` prints "Delete 1 tasks?".
- Four dead strings still describe shipped features as future work (`strings.xml:298-301`).
- "Completion by Quadrant" is title case beside "Time by quadrant". Smart view rows announce "Task options". "Today's Focus" uses the `AdsClick` icon, which shows a mouse pointer (`BrowseScreen.kt:83`).
- Quotes are straight in most strings and curly in `account_note_apple`.

## Accessibility

- TalkBack gets no name for any text field. Every dump showed unnamed `TextField` nodes, and the erase confirmation field is one of them.
- The module has zero `heading()`, `liveRegion`, `Role`, and `customActions` calls. Spec 13.3 promises custom actions on cards, and `accessibility-audit.md` says they exist.
- The quadrant picker and subtask toggles expose no selected or checked state. The sync chip always announces "Sync status". Stat cards read the value before its label. The trend chart has no description. The blocked badge reads "Blocked by 1" and then "1".
- Contrast: `ink3` on `paper` is 4.17:1 and `ink3` on `sunken` is 3.81:1. Ochre on its wash is 4.17:1 and ochre on `paper` is 4.47:1. The web already derives darker `q3` and `q4` text inks for this reason. Archive rows dim to 72% alpha, which lowers their text contrast further.

## What already works well

- The Matrix, capture bar, and live shorthand parsing look and feel finished. The quadrant chip and tag chips update as you type.
- The dark ramp is warm and consistent. Every accent has a tuned dark value.
- The completion rings card is a strong signature element, with correct single-node semantics.
- The splash, adaptive icon, and monochrome layer match spec 5.9. I saw no flash of the main screen before onboarding.
- The skeletons and the confetti match their spec parameters and respect the animator setting.
- The widget updates live from the store, in both themes.
- The 0.7.0 to 0.9.0 upgrade migrated cleanly, and logcat showed no crash during the whole session.

## Also worth a look

These came from the release-readiness pass. I confirmed the code but did not reproduce them.

- Alarm scheduling has no cap. One alarm per future-due task can pass the platform's per-app alarm limit after a large import, and `appScope` has no exception handler.
- Recurrence adds days in UTC. A due date stored at local midnight lands an hour early after a daylight-saving change, so a daily series reads a day early (`RecurrenceEngine.kt:13-17`).
- No receiver listens for time zone changes, so quiet-hours deferrals go stale after travel.
- The DataStore files have no corruption handler, and imported quiet hours go through `LocalTime.parse` unchecked (`NotificationSettingsStore.kt:45-46`).
- Every sync lists the whole remote collection twice (`SyncEngine.kt:125`, `:198`).
- The share target drops `EXTRA_TEXT` when it arrives as a `CharSequence` (`share/ShareActivity.kt:46-47`).
- R8 looks clean. The reviewer checked the 0.9.0 mapping output: serializers, Room, the worker, and the enums all keep what they need.
- The Console's "native code without debug symbols" warning is benign. It comes from two stripped AndroidX libraries.

## Suggested order

1. S1 to S6 first, each with a regression test. Start with S1: add a null-owner test that must delete nothing, then abort the run when no owner resolves.
2. Findings 1 to 5 are small, contained code fixes. Land them next with regression tests.
3. Findings 6 to 8 decide whether the reminder feature matches the listing. Add the date and time picker, the contextual permission ask, and the exact alarm row.
4. Findings 9 to 14 repair dark mode, large screens, large text, the share sheet, and select mode. Findings 37 to 42 cover theming outside the phone layout, import and export, and the dead setting.
5. Findings 15 to 18 close the policy gaps before the production review reads the listing. Update the privacy page in the same pass.
6. Work through the polish table, starting with onboarding, the Field Guide, and the shared top bar.
7. Re-run the accessibility audit on a device with TalkBack once the semantics land, and correct `accessibility-audit.md`.

## Emulator state after this review

- `gsd-shot` now runs the 0.9.0 debug build. Its seeded tasks are intact, and the Trash is empty.
- Font scale, night mode, display size, density, rotation, and the keyboard setting are back to their defaults.
- A "Today's Focus" widget remains on the emulator home screen. Drag it to Remove if you want the old home screen back.
