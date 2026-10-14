# Archive after a pull, and keep a newer remote edit (2026-10-13)

> **Status:** amends `docs/gsd-android-spec.md` §8.12, §9.5, and §11.5, which now carry the rules. This
> note records why. Parity backlog B-46 (Android half) and B-47, from the GSD Project's `parity/` docs.

## The problem

Archiving a task enqueues a delete of its server copy (§8.12). Two gaps let that delete remove a task
that another device had reopened.

1. **The sweep ran from the local copy before pulling (B-46).** `MaintenanceWorker` swept first and
   synced after. If another device reopened an old completed task since this device last synced, the
   sweep archived the stale copy, and the next push deleted the reopened task everywhere.
2. **A clean pull doesn't prove the local copy is current (B-47).** An edit can land between the pull
   and the archive, or after the archive but before the next push sends the delete. A pull also skips
   a record it can't map, which leaves the stale copy in place. The push then deleted the record with
   no version check at all. (The web has one more path: its cursor runs on `client_updated_at`, so it
   can miss a late push with an older stamp. Android's cursor runs on the server's `updated`, §9.4.)

## The decision

- **Signed in, the sweep follows a clean sync.** `SyncCoordinator.afterCleanSync` runs
  `ArchiveStore.sweepIfEnabled()` after every run whose `SyncResult.clean` is true (no error; push
  failures may remain). The maintenance job requests a sync instead of sweeping. It reads "signed in"
  from the saved session, because the coordinator may still be starting when WorkManager launches the
  process for the job. Signed out, nothing leaves the device, so the job sweeps as before. A failure in
  the after-sync step is logged and never stops later syncs.
- **The trade-off:** the job doesn't wait for its sync, so a signed-in device archives after the next
  clean sync, usually when the app opens. A device that stays offline or keeps failing to sync doesn't
  auto-archive until it syncs cleanly. iOS behaves the same way.
- **An archive's delete carries the task as archived.** The push skips that delete when the remote
  `client_updated_at` is newer than the payload's `updatedAt`. After a normal sync the two are equal,
  so ordinary archives still delete. In the worst case this device keeps the task archived while
  others keep it active. Nothing is lost.

The web shipped the same two rules in gsd-task-manager PR #603 (`cdd14f6`). iOS gates its sweep on a
clean sync the same way (gsd-iosapp PR #21).

## Not covered here

- **Manual "Archive now"** in Settings and on the Archive screen still sweeps directly; its deletes get
  the B-47 guard.
- **A delete with no payload** (moving a task to trash) still deletes without a version check. The
  queue-time edit-beats-delete guard is parity backlog B-42.
- **A remote record with no usable version** doesn't block an archive's delete. Whether it should is
  parity decision D-9.
