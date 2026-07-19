# GSD Android — progress

## Done
- **Phase 0** (foundations) — modules, domain model, validation, codec, Room v1, theme. All green.
- **Phase 1** (core local app) — matrix (compact + 2×2 drag), capture bar + parser, editor,
  completion/confetti/haptic, delete+undo, show-completed, adaptive nav. Tagged `v0.1.0-phase1`.
- **Phase 2** (task depth) — due presets, recurrence engine (month-end clamp, single-level lineage,
  transactional spawn-on-complete), BFS dependency graph, time tracking, snooze, editor depth
  sections, card badges/live timer, long-press context menu, share. Tagged `v0.2.0-phase2`.
- **Phase 3** (organization & insight) — FilterCriteria pipeline + nine built-ins, archive
  (store + screen + auto-sweep), smart views (custom CRUD + pins ≤5), Browse tab + filtered lists +
  criteria editor, command palette (Ctrl+K/F/N), multi-select bulk bar, analytics dashboard,
  import/export (sorted-keys envelope, merge ID-remap, replace-all) + RESET-guarded local erase,
  full local Settings, 4-page onboarding, Field Guide. Tagged `v0.3.0-phase3`.

**Phase 3 exit gate:** `:core:model:test` (~80 tests) + `:app:testDebugUnitTest` (7) +
`:core:store:connectedAndroidTest` (21) + `:app:connectedAndroidTest` (41) — all green.
Visual smoke: onboarding + Browse verified on emulator.

## Resuming From Here
- **Next:** Phase 4 — notifications (`tasks/plan.md` outline): exact alarms + `SCHEDULE_EXACT_ALARM`,
  two channels, contextual POST_NOTIFICATIONS, quiet hours, boot rescheduling, notification actions
  (Complete / Snooze 1h), reminder resync off the store change stream, WorkManager maintenance job
  (which should also run `ArchiveStore.sweep` — currently manual/launch only). Re-plan at task level first.
- **Test device:** AVD `gsd-test` (API 36, 320×640 profile).
- **Known deferrals** (`tasks/implementation-notes.md`): trailing-swipe two-button reveal; Ctrl+1–4
  quadrant jump; editor subtask drag-reorder (buttons/none yet — add/toggle/delete only); smart-view
  dateRange UI (criteria supports it); expanded-width palette dialog styling; per-view bulk bar
  (matrix only today).
- **Blockers:** none for Phase 4. Pre-Phase-5 backend checklist in `tasks/plan.md`.
