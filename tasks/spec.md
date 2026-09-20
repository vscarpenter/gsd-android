# Spec: state changes never reject a stored task (review finding S6)

Approved in chat on 2026-09-20, option "Validate typed input only". Source: `docs/play/2026-09-20-polish-review.md`, finding S6.
The S3 spec that lived in this file is in history at `f9a0ab1`.

## Goal

A task that arrived through sync must stay usable on Android even when it breaks an Android limit.
iOS counts characters. The web and Android count UTF-16 units. A title of 79 letters plus one emoji is legal on iOS and is 81 units here.
Pull stores it as is. Today any touch validates the whole task, throws, and crashes, because the view models and the notification receiver catch nothing.

## The rule

Validate what the user types, where they type it. A state change never rejects a stored task.
The web and iOS already work this way. The web validates in `create.ts` and `update.ts` only. iOS validates in capture, create, and `save` only.

## Inputs and outputs

- `TaskValidator.normalize(task, now)` is new, pure, and never throws. It applies the three coercions spec 7.11 allows: tags trimmed, lowercased, deduplicated, and empties dropped; estimate 0 becomes unset; a snooze is capped at 365 days.
- `TaskValidator.validate(task, now)` keeps its contract. It normalizes, checks every limit on the normalized task, and throws a typed error.
- `TaskStore.add`, `update`, and `replaceAll` still validate. Their callers are the editor, capture, share, import, and bulk Add tag.
- `TaskStore.setCompleted`, `snooze`, `startTimer`, and `stopTimer` only normalize. The recurrence spawn only normalizes.
- `TaskStore.change(id, transform)` is new. It reads the stored task, applies the transform, normalizes, stamps `updatedAt`, enqueues an UPDATE, and writes. It returns null when the task is gone. Its callers are the quadrant move (Matrix and bulk), bulk Remove tag, and bulk Set due.
- `TaskStore.duplicate(id)` is new. It copies through `TaskDuplicator`, normalizes, and enqueues a CREATE. It returns null when the task is gone.

## Constraints

- Every new path keeps the enqueue-before-write invariant: one transaction, queue row first.
- Units stay UTF-16, as on the web. No limit changes.
- `:core:model` stays free of Android imports. `normalize` gets JVM tests.
- Typed input still fails with a typed error and human copy.

## Edge cases

- An over-limit task opened in the editor: Save fails with the title message until the user shortens the title. The web does the same today.
- Bulk Add tag on an over-limit task: counted as failed, as today. The tag is typed input, so the whole task validates.
- A snooze more than 365 days out: still capped.
- Completing a recurring task: the completion and the spawned instance land in one transaction, and neither validates.
- A task deleted between the tap and the write: `change` and `duplicate` return null and write nothing.

## Acceptance criteria

1. A pulled task titled with 79 letters plus one emoji can be completed, snoozed, timed, moved, and duplicated.
2. Completing a recurring one spawns the next instance with the same title.
3. `update` with that title still throws `TitleLength`.
4. `normalize` never throws on an over-limit task, and it applies the three coercions.
5. Bulk Move, Remove tag, and Set due succeed on that task. Bulk Add tag reports it as failed.
6. Every gate is green: `:core:model:test`, `testDebugUnitTest`, `connectedAndroidTest`, `assembleDebug`, and `assembleRelease`.

## Out of scope

- Counting characters instead of UTF-16 units.
- Review finding S5 (completing an already-completed recurring task).
- A catch-all for other exceptions in the view models or the receiver.
