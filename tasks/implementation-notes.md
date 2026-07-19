# Implementation notes (tactical deviations ledger)

## Phase 0

- **Task 1 / AGP 9 built-in Kotlin:** AGP 9.x errors if `org.jetbrains.kotlin.android` is applied — Kotlin support is built into AGP now. Removed the plugin from all Android modules (and the root classpath); `kotlin { jvmToolchain(17) }` still works, and the Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) is still applied explicitly. Plan's build files predated this.
- **Task 1 / KSP versioning:** KSP moved off the `<kotlin>-<ksp>` version scheme; current releases are standalone (2.3.x, Kotlin-version-independent). Pinned `ksp = "2.3.10"` instead of the plan's guessed `2.4.0-2.0.4`.
