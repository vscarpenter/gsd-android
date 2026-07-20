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
3. **Build the bundle** — `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
4. **Release smoke on the minified build** — install the release build on the Pixel and walk
   capture → complete → sync → reminder once (R8 build is compile-verified but not runtime-smoked).
5. **Play Console** — app + declarations are done (see above); upload the AAB to the
   **internal testing** track and add yourself as tester. Production needs a closed test
   first: 12+ testers opted in for 14+ days, then "Apply for production".
6. **Screenshots** — per the shot-list; a Pixel-profile emulator and a tablet AVD cover both sets.
7. Promote internal → closed → production at your pace.

## Deferred (post-1.0 candidates, all logged in tasks/todo.md)
Baseline-profile generation (needs a macrobenchmark module); share-sheet title enrichment;
quadrant-route scroll targeting; onboarding sign-in buttons; expanded-width palette dialog.
