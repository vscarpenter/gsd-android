# Play submission checklist

## Done in-repo (v0.7.0)
- [x] Adaptive icon with monochrome layer; splash on paper
- [x] Minified, resource-shrunk release build (R8 clean); profileinstaller wired
- [x] Target SDK 36, min 31; permissions minimal and declared (`docs/play/data-safety.md`)
- [x] In-app account deletion; accessibility baseline audited (`accessibility-audit.md`)
- [x] Listing copy + shot-list (`listing.md`)

## Owner steps
1. **Web account-deletion URL** — publish on gsdtaskmanager.com and link it in the Data Safety
   form (hard Play requirement; the only §16 item still open).
2. **Signing** — create an upload key (`keytool -genkeypair`), add a `signingConfigs.release`
   block or use Play App Signing; never commit the keystore.
3. **Build the bundle** — `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/`.
4. **Release smoke on the minified build** — install the release build on the Pixel and walk
   capture → complete → sync → reminder once (R8 build is compile-verified but not runtime-smoked).
5. **Play Console** — create the app (`dev.vinny.gsd`), fill Data Safety from
   `data-safety.md`, the exact-alarm declaration from the same file, upload the AAB to the
   **internal testing** track, add yourself as tester.
6. **Screenshots** — per the shot-list; a Pixel-profile emulator and a tablet AVD cover both sets.
7. Promote internal → closed → production at your pace.

## Deferred (post-1.0 candidates, all logged in tasks/todo.md)
Baseline-profile generation (needs a macrobenchmark module); share-sheet title enrichment;
quadrant-route scroll targeting; onboarding sign-in buttons; expanded-width palette dialog.
