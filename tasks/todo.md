# GSD Android — progress

## Done
### Phase 0 (foundations) — complete, tagged in history
- Repo + four-module scaffold (AGP 9.3, Kotlin 2.4, Compose BOM 2026.06) · NanoId · Quadrant ·
  domain types · TaskValidator · TaskJson codec · Room v1 (all nine §7.10 tables) + TaskStore ·
  GsdTheme tokens/type/shapes.

### Phase 1 (core local app) — complete
- [x] Capture parser + URL sanitizer (Appendix C fixtures)
- [x] Composition root, DataStore prefs, adaptive navigation (bottom bar / rail ≥600dp / drawer ≥840dp)
- [x] Compact matrix: quadrant sections, §5.6 task cards, dashed empty prompts
- [x] Capture bar with live quadrant preview + override cycle, IME serial capture
- [x] Editor sheet: title, 2×2 quadrant picker, tag chips, notes, auto-detected links
- [x] Completion: disc + leading swipe, one CONFIRM haptic, §5.8 confetti (reduced-motion aware)
- [x] Delete-immediately + undo snackbar (6 s / 12 s TalkBack), LWW-safe restore
- [x] Show-completed toggle + brand lockup top bar (Canvas four-tile mark)
- [x] Expanded 2×2 grid with cross-quadrant drag-and-drop; Quadrant enum now carries its flags

**Phase 1 exit gate:** `:core:model:test` (41) + `:app:testDebugUnitTest` (3) +
`:core:store:connectedAndroidTest` (6) + `:app:connectedAndroidTest` (21) — all green.
Manual smoke on emulator: capture "Ship the deck !!" lands in Do First with cleared field. ✔

## Resuming From Here
- **Next:** Phase 2 — task depth (`tasks/plan.md` outline): due dates + presets, recurrence engine,
  subtasks UI, snooze, dependencies + BFS, time tracking. Re-plan at task level before starting.
- **Test device:** AVD `gsd-test` (API 36 google_apis arm64, **320×640 default profile** — recreate with
  `-d pixel_7` if a realistic screen is needed for screenshots).
- **Deferred within Phase 1 scope (intentional):** context menu on long-press (§8.1) and quick-snooze
  trailing action arrive with Phase 2 snooze; TalkBack custom accessibility actions audit lands with
  the Phase 7 accessibility pass; capture-bar placeholder wraps on very narrow screens (cosmetic).
- **Blockers:** none for Phases 2–4. Pre-Phase-5 backend checklist in `tasks/plan.md`.
