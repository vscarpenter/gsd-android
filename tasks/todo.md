# GSD Android — progress

## Done
- [x] Phase 0, Task 1 — repo + four-module Gradle scaffold (AGP 9.2, Kotlin 2.4, Compose BOM 2026.06, wrapper 9.6.1)
- [x] Phase 0, Task 2 — NanoId (`:core:model`)
- [x] Phase 0, Task 3 — Quadrant enum + derivation
- [x] Phase 0, Task 4 — Task/Subtask/TimeEntry/Recurrence domain types
- [x] Phase 0, Task 5 — TaskValidator (Appendix B limits)
- [x] Phase 0, Task 6 — TaskJson lenient codec (ISO-8601 ms instants)
- [x] Phase 0, Task 7 — Room v1 (all nine §7.10 tables) + TaskStore enqueue-before-write; schema 1.json committed
- [x] Phase 0, Task 8 — GsdTheme tokens/type/shapes; Newsreader bundled

**Phase 0 exit gate:** `:core:model:test` (36 tests) + `:core:store:connectedAndroidTest` (6) + `:app:connectedAndroidTest` (2) — all green.

## Resuming From Here
- **Next:** Phase 1, Task 9 — capture parser + URL sanitizer (`tasks/plan.md`), pure-JVM TDD with the Appendix C vectors.
- **Test devices:** headless AVD `gsd-test` (API 36 google_apis arm64) created for instrumented runs; boot with
  `emulator -avd gsd-test -no-window -no-audio -no-boot-anim -no-snapshot`.
- **Blockers:** none for Phases 1–4. Pre-Phase-5 checklist (backend items) in `tasks/plan.md`.
- **Assumptions made:** AGP 9 built-in Kotlin (see `tasks/lessons.md`); KSP 2.3.10 decoupled versioning.
