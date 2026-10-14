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

## 0.9.0 mid-window closed-test release (2026-09-06)
The September parity round shipped as release **9 (0.9.0)** on the same Alpha track — the
"changes made during testing" answer for the production questionnaire. Bump commit `64e1cd9`.
- Gates before upload: `:core:model:test` 130 green, `testDebugUnitTest` 120 green, signed
  `app-release.aab` (6.4 MB) + `app-release.apk` (4.9 MB), aapt2-verified `versionCode=9`
  `versionName=0.9.0`, jarsigner-verified, V2-signed with the upload cert.
- Release smoke on the **minified build**, `gsd-test` emulator (API 36): onboarding → capture
  `!! Smoke test 0.9.0 #release` → Do First with tag → Mark complete → Dashboard rings at
  100%; no `AndroidRuntime` fatals. Sync and reminders were not exercised (no credentials on
  the emulator). The Pixel was left alone — it runs the Play-signed build, which an upload-key
  APK cannot replace.
- Console: uploaded via Create new release → 9 (0.9.0), release notes in `<en-US>`, Save →
  Publishing overview → Send 1 change for review at 3:04 PM CDT (submission #3). Only warning: native code
  without debug symbols (same advisory as 0.8.0). Device coverage unchanged (5,439 phones,
  3,791 tablets). Status after submit: **Changes in review**, quick checks up to 14 min, then
  Google review (typically < 7 days for a closed-test update).
- The developer account is **VinnyCarpenter** (ID 6801639637564019543), reachable from
  vscarpenter@gmail.com's Console session; the other account on the chooser ("Vinny
  Carpenter") is a 2021-closed shell — pick the second one.

## 0.9.1 closed-test release, submitted and published 2026-09-21
Every fix from the polish review (#3 to #24) and the two parity fills (#26 swipes on every list, #27
celebration on every list) are on `main` but testers still had 0.9.0. Bump commit `3c62197` on
`release/0.9.1`. The owner picked 0.9.1 over 1.0.0: a fixes-only build, with 1.0.0 kept for the
build that goes to Production.
- Gates on the bump commit: `:core:model:test` 145, `testDebugUnitTest` 139, `connectedAndroidTest` 200
  on `gsd-test` (`:app` 140, `:core:store` 60, none skipped), `:app:assembleDebug`,
  `:app:assembleRelease`, `:app:bundleRelease`. All green in 2m 49s.
- Artifacts, with versioned copies beside them: `app/build/outputs/bundle/release/gsd-0.9.1-versionCode10.aab`
  (6,466,192 bytes, SHA-256 starts `61298e2dc29ad4f4`) and `.../apk/release/gsd-0.9.1-versionCode10.apk`
  (4,896,130 bytes). aapt2 on the APK: `versionCode='10' versionName='0.9.1'`, targetSdk 36. The bundle
  manifest holds `0.9.1`. jarsigner verified the bundle. apksigner verified the APK with V2 only, which
  is why jarsigner calls the APK "unsigned". Both signers carry the upload key's DN (`CN=Vinny
  Carpenter, O=GSD Task Manager`); the APK cert's SHA-256 starts `7d88b21d7c2b6af2`.
- Release smoke on the **minified build**, `gsd-test` (API 36), driven through the accessibility tree:
  onboarding Skip → capture `!! Smoke test 0.9.1 #release` → Do First with the tag and the "..." button
  → Browse → Today's Focus lists it → **leading swipe on the list completes it, confetti plays there**
  (both new in this build) → Dashboard 0 active, 1 completed, rings at 100%. No `AndroidRuntime`
  fatals in logcat. Sync and reminders were not exercised (no credentials on the emulator). The release
  build was uninstalled from `gsd-test` afterward, so the next connected run installs cleanly.
- Release notes for the Console's `<en-US>` box (472 of 500 characters):
  > Fixes from a full review before production. Swipe right to complete and left to delete on every
  > task list, not only the Matrix. Completing a task celebrates on every list, and the task menu
  > works on every list. A snoozed reminder fires again, and tapping a reminder opens its task from
  > any tab. The editor no longer undoes a change made while it was open. Links and shortcuts open in
  > the running app instead of a second copy. Sync signs you out once a session has expired.
- Console, driven through Chrome from this session at 8:35 PM CDT (submission #4): Test and release →
  Closed testing → Alpha → Create new release → the `.aab` went into the file input (6,315 KB) → the
  Console read it as **10 (0.9.1)**, API 31+, target SDK 36, 4 ABIs → release name left at the suggested
  "10 (0.9.1)" → notes pasted inside `<en-US>` tags ("Release notes provided for 1 language") → Next →
  one warning, the native-symbols advisory again → device coverage unchanged (5,493 phones, 3,871
  tablets, 0 lost, 0 gained) → Save. **Managed publishing is off on this account, so Save submitted the
  change by itself**: Publishing overview showed "1 change sent for review" with the quick checks running
  (up to 14 minutes) and "Remove changes" available during that window. There was no separate "Send for
  review" click. The 0.9.0 note above describes the same outcome in different words.
- **Published 2026-09-21 at 9:08 PM CDT**, read from the Console on 2026-09-22. The Alpha track lists
  10 (0.9.1) as "Available to selected testers", one version code. Submission activity shows #4 as
  Published and stamps it 8:34 PM, a minute before the time above, so review took 34 minutes. Publishing
  overview reads "You have no unpublished changes".
- Who can install it: the track's tester list is two Google Groups, `gsd-android-testers@googlegroups.com`
  and `gsd-android-testers-needed@googlegroups.com`. The closed-test gate section names only the first.
  Feedback goes to `gsdapp@vinny.dev`. The track targets the United States only, so a tester in another
  country cannot install the build.
- The Internal testing card shows 8 (0.8.0), dated Aug 29 at 10:05 PM. The 2026-08-29 note says the bundle
  skipped internal testing, and nothing in this repo records that release.
- The Console's "For your next release" card flags two items on 10 (0.9.1). Android 15 draws apps that
  target SDK 35 or later edge to edge, so the insets need a check. The app also reaches the deprecated
  `Window.setStatusBarColor`, `Window.setNavigationBarColor`, and `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`
  through `rf0.a` and `qf0.b`. The R8 mapping on disk, from a release build at 8:53 PM that night, names
  those classes `androidx.activity.EdgeToEdgeApi29` and `EdgeToEdgeApi28`. The app's own code calls only
  `enableEdgeToEdge()` in `MainActivity`. Finding 11 is in the same area.
- This release is the "changes made during testing" answer if Google asks during the production review.
- Still unwalked on a signed build: sync, a reminder firing, the widget, a deep link, and one real
  sign-in. 0.9.1 has published, so do them now from the Play-installed build on the Pixel.

## Production access declined twice; 0.10.0 prepared (2026-10-13)
Google declined production access on 2026-09-21 and 2026-10-09 ("More testing required"). Bump commit
`4813ff5` on `release/0.10.0` sets versionCode 11, versionName 0.10.0. Not built or uploaded yet. The
diagnosis, the 14-day plan, the release notes, and draft answers for the next application are in
`production-access-round-3.md`.

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
4. **Release smoke on the minified build** — done 2026-09-06 on the emulator for 0.9.0
   (capture → complete → dashboard; see the 0.9.0 block above). Still unwalked on a signed
   build: sync and a reminder firing — do them from the Play-installed build on the Pixel once
   0.9.0 publishes.
5. ~~**Play Console upload**~~ — done 2026-08-29: 0.8.0 published to Closed testing – Alpha.
   Remaining: the closed-test gate above (12 testers × 14 days), then "Apply for production".
6. ~~**Screenshots**~~ — done 2026-07-19 (see step 1).
7. After production access is granted: promote the Alpha release to Production.

## Deferred (post-1.0 candidates, all logged in tasks/todo.md)
Baseline-profile generation (needs a macrobenchmark module); share-sheet title enrichment;
quadrant-route scroll targeting; onboarding sign-in buttons; expanded-width palette dialog.
