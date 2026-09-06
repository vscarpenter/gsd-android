# Play submission checklist

## Done in-repo (v0.7.0)
- [x] Adaptive icon with monochrome layer; splash on paper
- [x] Minified, resource-shrunk release build (R8 clean); profileinstaller wired
- [x] Target SDK 36, min 31; permissions minimal and declared (`docs/play/data-safety.md`)
- [x] In-app account deletion; accessibility baseline audited (`accessibility-audit.md`)
- [x] Listing copy + shot-list (`listing.md`)

## Done in Play Console (2026-07-19, interactive session)
App created: **GSD — Get Stuff Done**, package `dev.vinny.gsd`, free, app id 4975470969419409708.
All app-content declarations saved (nothing sent for Google review yet; changes sit in
Publishing overview): privacy policy URL, Ads = no, Sign-in = nothing restricted, IARC content
rating (Everyone), target audience 18+, Advertising ID = no, Government apps = no, Financial
features = none, Health = none, and the full Data Safety form (email + user-generated content,
optional, encrypted, deletable; deletion URLs point at the privacy page). Store settings:
category App/Productivity, contact email gsdapp@vinny.dev, website gsdtaskmanager.com. Main
store listing text saved as draft; the Graphics section is what blocks marking it complete.

## Done in Play Console (2026-08-29) — closed test live
Submission #2 (Closed testing – Alpha, Store Listing, App Content, Store settings) was
**Published** at 5:19 PM; #1 (5:06 PM) was canceled and resubmitted. Release **8 (0.8.0)** has
been available to selected testers since 5:33 PM, one country/region, 9,295 supported devices.
Production track: Inactive. Internal testing was skipped; the bundle went straight to Alpha.

## Closed-test gate (status 2026-09-06)
Production requires a closed test with **12 opted-in testers for 14 continuous days**, then
"Apply for production" (a questionnaire about recruitment, usage, feedback, and changes).
- Dashboard reads **0 testers currently opted in**; the 14-day clock has not started.
- Tester list is the Google Group `gsd-android-testers@googlegroups.com` (4 members; owner
  vinnycarpenterdev@gmail.com; "Anyone on the web can join"). Membership alone does not
  count — each tester must opt in, then install:
  - Join on the web: <https://play.google.com/apps/testing/dev.vinny.gsd>
  - Install (after opting in): <https://play.google.com/store/apps/details?id=dev.vinny.gsd>
- Recruiting: tester call emailed to the group 2026-09-06; group welcome message carries both
  links; post on r/AndroidClosedTesting (reciprocal — test their apps back). Aim for ~15
  opt-ins so churn does not stall the clock.
- Before Redditors arrive: group "Who can view conversations" → Group members; "Who can view
  members" → Group managers.
- Day 14 → Apply for production → Google review (budget several days) → promote the Alpha
  release to Production.

## Owner steps
Play Console account: **vinnycarpenterdev@gmail.com**.

1. ~~Listing graphics~~ — done 2026-07-19: icon (rendered from the adaptive icon), feature
   graphic (Newsreader lockup), 5 phone shots (1080×1920, emulator with seeded demo data),
   2 tablet shots (2560×1600) in both the 7-inch and 10-inch slots. Store listing saved in
   full; the widget shot from the shot-list was skipped (widget pinning is manual).
   Optional: publish a dedicated deletion page and swap the Data Safety URLs.
2. **Signing** — the gradle side is wired; it reads an untracked `keystore.properties`. Create the
   upload key once:
   ```bash
   keytool -genkeypair -v -keystore ~/keys/gsd-upload.jks -alias gsd-upload \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
   Then create `keystore.properties` at the repo root (gitignored):
   ```properties
   storeFile=/Users/vinnycarpenter/keys/gsd-upload.jks
   storePassword=...
   keyAlias=gsd-upload
   keyPassword=...
   ```
   Enroll in Play App Signing at app creation so this stays an upload key, not the app key.
3. ~~**Build the bundle**~~ — done 2026-08-29: signed 0.8.0 `app-release.aab` (versionCode 8).
4. **Release smoke on the minified build** — install the release build on the Pixel and walk
   capture → complete → sync → reminder once. Not recorded as done; the closed-test window is
   the time to do it.
5. ~~**Play Console upload**~~ — done 2026-08-29: 0.8.0 published to Closed testing – Alpha.
   Remaining: the closed-test gate above (12 testers × 14 days), then "Apply for production".
6. ~~**Screenshots**~~ — done 2026-07-19 (see step 1).
7. After production access is granted: promote the Alpha release to Production.

## Deferred (post-1.0 candidates, all logged in tasks/todo.md)
Baseline-profile generation (needs a macrobenchmark module); share-sheet title enrichment;
quadrant-route scroll targeting; onboarding sign-in buttons; expanded-width palette dialog.
