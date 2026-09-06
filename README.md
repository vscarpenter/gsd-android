# GSD for Android

GSD ("Get Stuff Done") is a privacy-first, offline-first Eisenhower-matrix task manager. This repo is the native Android app: Kotlin, Jetpack Compose, and no wrapper frameworks. It answers one question well: what do I work on next?

Tasks sort along two axes, urgent and important, into four quadrants: do first, schedule, delegate, eliminate. Everything works offline with no account. Sync is optional, and only starts when you sign in.

## The GSD ecosystem

This app joins three shipped clients that share one self-hosted PocketBase backend and one wire schema. A task captured here round-trips losslessly through all of them.

| Client | Platform |
|---|---|
| [GSD web app](https://gsdtaskmanager.com) | Next.js + Dexie |
| GSD iOS app | iPhone, iPad, Mac Catalyst (SwiftUI) |
| GSD MCP server | Claude Desktop |
| **GSD Android** (this repo) | Phones, tablets, foldables |

## What it does

- **Frictionless capture.** Type `!! ship the deck #work` and the task lands in Do First, tagged. Share a link from any app straight onto the board.
- **Depth under a calm surface.** Recurrence, subtasks, dependency graphs with cycle detection, time tracking, snooze, smart views, and an honest analytics dashboard. No streaks, no badges.
- **Exact reminders.** Alarm-scheduled notifications with Complete and Snooze actions, quiet hours, and reboot recovery.
- **Optional sync.** Last-write-wins sync with the shared backend over REST and server-sent events. Sign in with Google, Apple, or GitHub.
- **At home on Android.** A Today's Focus widget, launcher shortcuts, deep links, predictive back, and a true 2x2 tablet grid with drag-and-drop.

## Architecture

Four Gradle modules with an enforced dependency direction. The design rule that pays the biggest dividend: all behavior lives in pure Kotlin, so the test loop runs in about a second with no emulator.

| Module | Responsibility |
|---|---|
| `:core:model` | Pure JVM domain: parser, recurrence, dependency graph, filters, analytics. Zero Android imports. |
| `:core:store` | Room persistence and the single TaskStore mutation path. |
| `:core:sync` | PocketBase client, wire mapper, and the last-write-wins sync engine. |
| `:app` | Compose UI, widget, share target, notifications, composition root. |

Two invariants hold everything together. Every local mutation enqueues its sync operation in the same transaction, before the row becomes visible. And the persisted quadrant never drifts from the urgent/important flags that derive it.

## Building and testing

Requires JDK 17 or newer. Gradle provisions its own toolchain.

```bash
./gradlew :core:model:test        # the fast gate: pure-logic tests, sub-second
./gradlew testDebugUnitTest       # all JVM suites (model, sync, app)
./gradlew connectedAndroidTest    # Room + Compose tests, needs an emulator
./gradlew :app:installDebug       # run it
```

## Documentation

- [`docs/gsd-android-spec.md`](docs/gsd-android-spec.md): the product spec. Every behavior rule, design token, and protocol detail. When code and spec disagree, the spec wins.
- [`docs/coding-standards.md`](docs/coding-standards.md): how the code gets written. Spec-driven development, TDD, conventional commits.
- [`docs/play/`](docs/play/): Play Store submission material, including the Data Safety answers and the accessibility audit.

## Status

Feature-complete through the spec's eight-phase roadmap, tagged `v0.1.0-phase1` through `v0.7.0-phase7`. Play Store submission is in progress; the checklist lives in [`docs/play/submission-checklist.md`](docs/play/submission-checklist.md). The August 2026 cross-platform parity catch-up — backup envelope 2.1.0, a 30-day trash, the shared 40-case capture-parser corpus, and anonymous opt-in feedback — landed 2026-08-29, with fixtures kept byte-identical to the sibling web and iOS repos. A second parity round landed 2026-09-06 on `feat/parity-2026-09`: per-quadrant completion rings and day-based deadline badges on the Dashboard, a Browse-wide task search with a shared list ordering, first-frame skeletons on the Matrix, Dashboard, and smart-view lists, off-list reminder offsets rendered instead of snapped, bounded smart-view import/export, and the sibling security hardening — fail-closed remote listings, a malformed-version push guard, three-verdict remote-absence reconciliation, and route-first account deletion with surfaced outcomes.

Built by [Vinny Carpenter](https://vinny.dev), with Claude doing the typing.
