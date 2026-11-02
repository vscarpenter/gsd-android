# GSD Android

Native Android GSD Task Manager (Kotlin + Jetpack Compose).

- **Behavior authority:** `docs/gsd-android-spec.md` — every rule, limit, token, and protocol detail. When code and spec disagree, the spec wins.
- **Process authority:** `docs/coding-standards.md` — TDD red/green/refactor, conventional commits, task tiers.
- **Plan:** `tasks/plan.md` (phases 0–7, complete) · `tasks/parity-plan.md` (Aug 2026 parity catch-up, complete 2026-08-29) · `tasks/parity-plan-2026-09-06.md` (Sept 2026 parity round 2, complete 2026-09-06 — amendments folded into the main spec) · `tasks/saved-parity-plan.md` (Oct 2026: Saved, pull contract 3, sync fences; spec `docs/2026-10-29-cross-platform-parity-spec.md`, folded into the main spec) · progress in `tasks/todo.md` · Play submission in `docs/play/` (0.9.0 submitted to Closed testing – Alpha 2026-09-06, 0.8.0 live until it publishes; tester gate open). `docs/play/submission-checklist.md` is local-only: it's gitignored and missing from fresh clones and worktrees.
- Modules: `:core:model` (pure JVM — keep it Android-free), `:core:store` (Room), `:core:sync` (PocketBase), `:app`.
- Fast gate: `./gradlew :core:model:test` — keep it sub-second.
