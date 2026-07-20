# GSD Android

Native Android GSD Task Manager (Kotlin + Jetpack Compose).

- **Behavior authority:** `docs/gsd-android-spec.md` — every rule, limit, token, and protocol detail. When code and spec disagree, the spec wins.
- **Process authority:** `docs/coding-standards.md` — TDD red/green/refactor, conventional commits, task tiers.
- **Plan:** `tasks/plan.md` · progress in `tasks/todo.md` · Play submission in `docs/play/`.
- Modules: `:core:model` (pure JVM — keep it Android-free), `:core:store` (Room), `:core:sync` (PocketBase), `:app`.
- Fast gate: `./gradlew :core:model:test` — keep it sub-second.
