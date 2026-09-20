# Spec: dead-session sync guard (review finding S1)

Approved in chat on 2026-09-20, identity-check variant. Source: `docs/play/2026-09-20-polish-review.md`, finding S1.

## Goal

A sync run must never delete or trash local tasks unless the server has confirmed, in that same run, that the session belongs to the signed-in owner.
Today only `push()` checks for an owner. `pull()` and `reconcileDeletions()` run as a guest after a dead session, and an empty guest listing deletes every synced task.

## Inputs and outputs

- `SyncEngine.runSync` resolves the owner once. With no owner it does nothing and returns `status = "error"`, `error = "Not signed in"`, and `notSignedIn = true`.
- `SyncRemote.confirmIdentity(ownerId)` is new. It returns normally when the server recognizes the session as `ownerId`. It throws otherwise.
- `PocketBaseApi.confirmIdentity` calls `POST /api/collections/users/auth-refresh` and compares the returned record id. The endpoint needs a valid token, so it cannot pass for a guest whatever the collection rules say. The returned token is ignored.
- `reconcileDeletions` calls `confirmIdentity` before the first `ABANDON` or `APPLY` verdict. A failure aborts the run with an error and changes nothing.
- `SyncCoordinator` still refreshes the token before each run. When the run reports `notSignedIn`, it sets `signedIn = false` and `sessionExpired = true`. Signing in again clears the flag.
- Settings shows `health_expired` above the sign-in options while `sessionExpired` is true.

## Constraints

- `:core:sync` stays free of Android imports. Every rule is a JVM test over fakes or MockWebServer.
- The steady-state run makes no extra request. The identity call happens only when reconciliation is about to trash or delete.
- `PROTECT` verdicts need no identity check, because they change nothing.
- No change to the wire model, the cursor rules, or the three-verdict table.

## Edge cases

- Expired token, refresh answers 401: the token clears, the run aborts before any request, and the account shows as signed out with the expired note.
- Dead token with more than 7 days left: no refresh happens, the server treats the run as a guest, and `confirmIdentity` throws `Unauthorized`. The run reports an error and deletes nothing.
- The server confirms a different record id: `confirmIdentity` throws and the run deletes nothing.
- A rate-limited identity call throws `RateLimited`. The run ends with an error and the next run retries.
- A verified owner with a genuinely empty remote still applies the deletions, so "erase everywhere" from another device keeps working.
- Transient refresh failures keep the stored token, as spec 10.3 requires. The listing then fails on the network and the run ends before reconciliation.

## Out of scope

- Resetting the pull cursor on sign-out, erase, or owner change (finding S3).
- Signing the user out when `confirmIdentity` gets a definitive 401 on an unexpired token.
- The other review findings.

## Acceptance criteria

1. Null owner, empty remote, one synced local task: the task survives, nothing is pulled, and the result reports `notSignedIn`.
2. Owner present, identity rejected, one synced local task: the task survives, nothing is trashed, and the result is an error.
3. Owner present, identity rejected, one task with only failed queue rows: the task is not trashed and its queue rows stay.
4. Owner present, identity rejected, nothing to reconcile: the run succeeds.
5. The existing `APPLY`, `ABANDON`, and `PROTECT` tests stay green with a confirming remote.
6. `confirmIdentity` passes on a matching record id, throws `Unauthorized` on a 401, and throws on a different record id.
7. A coordinator whose token disappears mid-process ends the session, raises `sessionExpired`, and keeps every task. Signing in again clears the flag.
8. The signed-out Account rows show the expired note only when the session expired.
9. `./gradlew :core:model:test`, `./gradlew testDebugUnitTest`, and `./gradlew connectedAndroidTest` are green.

## Test stubs

- `SyncEngineTest`: criteria 1 to 5.
- `PocketBaseApiTest`: criterion 6.
- `SyncCoordinatorSessionTest` (instrumented): criterion 7.
- `SignedOutAccountTest` (instrumented): criterion 8.
