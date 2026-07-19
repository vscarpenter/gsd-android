# GSD Android — progress

## Done
- **Phase 0** foundations · **Phase 1** core local app (`v0.1.0-phase1`) · **Phase 2** task depth
  (`v0.2.0-phase2`) · **Phase 3** organization & insight (`v0.3.0-phase3`) · **Phase 4**
  notifications (`v0.4.0-phase4`)
- **Phase 5 — sync** (`v0.5.0-phase5`): snake_case wire mapper with lossy time entries and
  device-local preservation; lenient PocketBase dates + 5 s cursor rewind; JWT payload decode;
  SSE frame parser + apply rules (echo filter, owner fail-closed, delete queue-protection);
  OkHttp PocketBase client (bare-JWT header, typed 429/401); the LWW SyncEngine passing the full
  §13.6 matrix (pull/push/reconcile/backoff/failed/429-abort/history/health); PKCE auth with
  state validation, Keystore-encrypted token, 7-day-skew refresh with transient/definitive split;
  SyncCoordinator trigger fabric (launch/foreground/network/120 s cadence/1.5 s debounce/manual,
  failed-requeue on the §9.5 set, foreground-only SSE with backoff + post-drop full sync);
  device identity; Account settings, quiet sync chip, sync history screen, account switch prompt,
  delete account, erase-everywhere (remote-first).
- **Live-verified without credentials:** OAuth bounce page client-agnostic (GET static forward,
  POST 302 promotion); auth-methods envelope + all three providers with PKCE material.

- **Phase 6 — native surfaces** (`v0.6.0-phase6`): §6.4 deep-link router (all routes, cold-start
  pending route, not-found toast — smoke-tested per route via adb), §12.2 share target with URL
  title derivation, §12.1 Glance Today's Focus widget (push-updated snapshot, day-night GSD
  colors, deep-link taps), §6.5 static shortcuts. Deferrals: share-sheet title enrichment
  (fetchShareTitles fetch stack), quadrant-route scroll position, widget render verified by
  build + route smoke only (place from the launcher for a visual pass).

## Resuming From Here
- **Next:** Phase 7 — Play readiness: adaptive icon + monochrome layer (§5.9), Data Safety form
  content (§14), accessibility audit (TalkBack, 2× font, reduced motion), baseline profile,
  screenshots, internal testing track. Owner item still open: web-accessible account deletion URL.
- **Phase 5 live-verified on a Pixel 10a (2026-07-19):** Google sign-in through the Custom Tab →
  bounce → callback → exchange path works, and push/pull round-trips against the live backend.
  Two live-only bugs were found and fixed in the process: the missing INTERNET permission
  (hidden by a swallowed exception) and the wire push omitting `owner` / carrying `id:""` in
  PATCH bodies (server 400s read from the queue's lastError). Apple/GitHub sign-in and a
  two-device round-trip remain untested but share the verified path.
- **Phase 5 deferrals:** onboarding's final-page sign-in buttons (Settings is the sign-in surface
  for now); expanded-width palette dialog styling; Chrome JS-redirect user-gesture caveat on the
  GET bounce path (fix is server-side 302 if it bites).
- **Still open (owner):** web-accessible account deletion URL for the Play Data Safety form.
