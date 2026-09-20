# Spec: account switch keeps tasks and resets the cursor (review finding S3)

Approved in chat on 2026-09-20, with the restart guard included. Source: `docs/play/2026-09-20-polish-review.md`, finding S3.
The S1 spec that lived in this file is in history at `97cb8d3` and `cbe1067`.

## Goal

Signing in to a different account must never delete the tasks the user chose to keep, and it must pull the new account's full history.
Today `confirmOwner` seeds nothing, so tasks already synced to the old account have no queue rows and reconciliation deletes them.
The pull cursor is never reset, so the new account's records older than the old cursor never arrive.

## Inputs and outputs

- `OwnerChange.adopt(local, previousOwner, newOwner, keepLocalTasks, nowMs)` is new and pure. It acts only when a previous owner exists and differs from the new one.
  - It clears the pull cursor.
  - With `keepLocalTasks`, it enqueues one UPDATE row for every active task that has no pending or failed row. The payload is the task as stored, with no restamp.
- `SyncLocalStore` gains `clearCursor()` and `enqueueUpdate(task, nowMs)`.
- `SyncCoordinator.confirmOwner` reads the previous owner, erases local data when asked, runs `OwnerChange.adopt`, and only then records the new owner.
- `SyncCoordinator.start` compares the stored token's user id with the last confirmed owner. When they differ and active tasks exist, it raises `switchPrompt` again and stays signed out until the user answers.

## Constraints

- A same-owner sign-in changes nothing. A task deleted on another device while this one was signed out still deletes here.
- Seeded rows are never restamped, so last-write-wins still decides against anything already in the new account.
- The seed runs once, at the moment of the decision. It does not depend on the cursor advancing.
- `:core:sync` stays free of Android imports. The seed and cursor rules are JVM tests over fakes.

## Edge cases

- First sign-in on an install: no previous owner, so nothing changes. Local tasks already have queue rows.
- New owner, no local tasks: no prompt. The cursor still clears, so the full pull runs.
- "Start fresh": local data is erased first, the cursor clears, and nothing is seeded.
- "Delete account and keep tasks" followed by a new sign-in: the queue was cleared, so every kept task is seeded.
- A task with only failed rows keeps those rows. The manual sync that follows a sign-in requeues them.
- The app dies while the prompt is open: the token is saved and the owner is not. The next launch raises the prompt again instead of syncing. A crash midway through the seed ends the same way, and a second answer seeds the rest.
- The rebuilt prompt knows the user id from the token and no email. A blank email is stored as no email.

## Out of scope

- Seeding on every nil-cursor sync, as iOS does.
- Clearing the cursor on sign-out.
- Persisting the pending sign-in provider across a restart.
- The other review findings.

## Acceptance criteria

1. New owner, keep: exactly the tasks without queue rows get one UPDATE row each, unstamped, and the cursor is null.
2. New owner, start fresh: the cursor is null and nothing is enqueued.
3. Same owner: the cursor and the queue are unchanged.
4. First sign-in: the cursor and the queue are unchanged.
5. After a keep, one engine run against an empty remote leaves the task on the device and creates it remotely.
6. On a device, "Keep my tasks" keeps the task locally and uploads it to the new account.
7. On a device, "Start fresh" pulls a task older than the old account's cursor.
8. On a device, an unanswered prompt comes back after a restart, nothing syncs, and every task survives.
9. `./gradlew :core:model:test`, `./gradlew testDebugUnitTest`, and `./gradlew connectedAndroidTest` are green.

## Test stubs

- `OwnerChangeTest` (JVM): criteria 1 to 5.
- `SyncCoordinatorOwnerChangeTest` (instrumented): criteria 6 to 8.
