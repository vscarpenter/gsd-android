# Spec: the task menu works on every list (review finding 48)

Approved in chat on 2026-09-20, option A, "wire all eight". Source: `docs/play/2026-09-20-polish-review.md`, finding 48.
The S6 spec that lived in this file is in history at `9f51a0e`.

## Goal

The task menu has eight items. On Matrix all eight work. On Browse search results and on a smart view list only Edit and Complete work, and the other six close the menu and change nothing.
Spec 8.13 says a smart view opens "the flat filtered list (§8.1 rows)", and 8.1 defines the full menu. The lists fall short of the spec.
A long press always reached these menus. Since finding 1 the "..." button reaches them in one tap.

## The rule

One place owns the row operations, and every screen that draws a task row uses it. A control with nothing behind it is not drawn.

## Inputs and outputs

- `TaskActionsViewModel(store, trashStore)` is new. It takes these members out of `MatrixViewModel`, moved and not copied: `toggleCompleted`, `delete`, `undoDelete`, `clearUndo`, `lastDeleted`, `moveToQuadrant`, `duplicate`, `snooze`, and `toggleTimer`. `DeletedSnapshot` moves with them.
- `MatrixViewModel` keeps `sections`, select mode, and the bulk actions. It no longer needs a `TrashStore`.
- `BrowseViewModel` loses its copy of `toggleCompleted`.
- `taskRowActions(task, context, actions, onEdit, onToggleComplete)` is new. It returns the full `TaskRowActions`, and it holds the Share intent that sat inline in `MatrixScreen`. The caller passes the completion handler, so Matrix keeps its haptic and confetti.
- `UndoDeleteEffect(actions, snackbarHost)` is new, taken from `MatrixScreen`. Matrix, Browse, and the smart view list each show the Undo snackbar through it.
- `TaskRowWithMenu` gains `menuEnabled`, true by default. When false the card gets no `onOpenMenu`, so it draws no button and a long press opens nothing. Matrix passes `!selectionMode`.
- `TaskCard.onToggleComplete` becomes nullable. A null callback draws no completion disc. Trash and Archive pass null.

## Constraints

- No behavior change on Matrix outside select mode. Its menu, swipes, celebration, and Undo work as before.
- Every write still goes through `TaskStore`, so the enqueue-before-write invariant is untouched.
- `:core:model` stays free of Android imports. Nothing in it changes.
- No new dependency. Share has no automated test, because the project has no intent-testing library.

## Edge cases

- Delete on a list removes the row from that list, and Undo brings it back with its original timestamps, as on Matrix.
- Duplicate on a smart view list adds a second row when the copy matches the view's criteria.
- Move to on Today's Focus can take the task out of the view. The row leaves the list, which is correct.
- A task deleted between the tap and the write: the store methods already return null and write nothing.
- An Undo offer ends when its screen leaves. A tab switch keeps the view model, so the record must not survive the cancelled effect. A second delete replaces the first offer and keeps its own record. Added after the review of #18.
- Select mode with the menu off: a tap still toggles the selection, and a long press does nothing.
- A Trash or Archive card with no disc: the content column takes the freed width.

## Acceptance criteria

1. On a smart view list, Delete removes the task from the store, the Undo snackbar appears, and Undo restores the task.
2. On a smart view list, Duplicate, Start timer, Snooze, and Move to each change the stored task as they do on Matrix.
3. On Browse search results, Delete removes the task from the store.
4. In Matrix select mode a card shows no "Task options" button.
5. Trash and Archive cards expose no "Mark complete" node.
6. Matrix outside select mode still passes every existing test.
7. After a delete, leaving the screen and coming back shows no second Undo offer, and a second delete still shows its own.
8. Every gate is green: `:core:model:test`, `testDebugUnitTest`, `connectedAndroidTest`, `assembleDebug`, and `assembleRelease`.

## Out of scope

- Swipe gestures on Browse search results and smart view lists. They have none today. Recorded as a new finding.
- The haptic and confetti when a task is completed on a list. Spec 8.4 says "always", and today only Matrix celebrates. Recorded as a new finding.
- The tablet grid's missing menu, which is finding 14.
- Review findings 5, 44, 45, 46, and 47.
