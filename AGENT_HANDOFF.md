# 📸 Cybermeta for Android: Agent Handoff

The state of the project for the next agent (or person). Update it in the
same commit or PR as any significant change.

## 📍 Repository state (2026-10-20)

- **Repo:** `cybercoretech/cybermeta-android-app` (public). The user
  created it empty on 2026-10-19. The local checkout is at
  `~/Devspace/Android/Cybermeta` on Blackbox.
- **`main`:** v0.1.0, the first commit pushed directly to the empty repo.
  From here on, branch and PR; the user merges.
- **Engine:** `rust/Cargo.toml` depends on `cybercoretech/cybermeta`,
  **branch `feat/privacy-scrub`** (cybermeta PR #1, v0.3.0);
  `Cargo.lock` pins the exact commit. **When PR #1 merges and `v0.3.0` is
  tagged, switch to `tag = "v0.3.0"`** and run `cargo update -p cybermeta`.
- **Release:** v0.1.0 is signed with `~/Vaults/android/cybermeta-release.jks`
  (env file `~/Vaults/android/cybermeta-release.env`, both 0600, backed up
  by MEGA).
  - Assets: `cybermeta.apk` and `cybermeta.apk.sha256`.
  - Certificate SHA-256: `88390595f5fbf171dc03d040abc8edc2a7fb9e973320b3c4bbe725d4139b283c`.
    It's also in `install.sh`, the README and the site; **change all of
    them together**.
- **Pages:** served from `main` `/docs` at
  https://cybercoretech.github.io/cybermeta-android-app/

## ✅ Strict resume checklist

1. Read this file and `README.md`.
2. Check `git status` and `git log --oneline -5`. Check PRs with
   `GH_TOKEN=$(gh auth token -u darkstardevx) gh pr list -R cybercoretech/cybermeta-android-app`.
3. Build and test:
   ```sh
   source ~/.config/android/env.sh
   ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
   ```
   The first build compiles the Rust engine with LTO; expect about 4 minutes.
4. Release build:
   ```sh
   set -a; . ~/Vaults/android/cybermeta-release.env; set +a
   ./gradlew :app:assembleRelease -PcybermetaVersionCode=N -PcybermetaVersionName=X.Y.Z
   ```
   Then upload `cybermeta.apk` and `cybermeta.apk.sha256`; the asset names
   are fixed because `install.sh` and the site link to them.
5. Identity: `git config user.email` must be
   `19244308+darkstardevx@users.noreply.github.com`.
6. Pace GitHub activity: batch work into few pushes and no force-push
   storms.
7. Docs:
   - this file and the README
   - Wiki `~/Wiki/src/android/cybermeta.md` (run `mdbook build`)
   - vault `~/Vaults/blackbox-notes/Projects/Cybermeta.md`

## 🧭 Decisions already made (with the user, 2026-10-19)

- **App name:** "Cybermeta". "Cybermeta Droid" was dropped because Droid
  is a Lucasfilm trademark.
- **Engine:** the shared Rust core through UniFFI, so the phone runs the
  exact desktop scrubber. It is not rewritten in Kotlin.
- **No INTERNET permission, ever.** CI checks the merged manifest.
- **Defaults:**
  - Share-safe keeps the colour profile and rotation; the date is off.
  - Random output names are on.
  - HEIC is converted to JPEG (the setting is on by default).
  - Quick share is off.
- **Originals** are only overwritten through `MediaStore.createWriteRequest`.
  Picker URIs and converted (re-encoded) photos are never offered for
  replacement.
- **crates.io:** cybermeta isn't published yet; hold off until 0.3.x has
  seen some use.

## 🧱 Architecture

| Area | Where | Notes |
|---|---|---|
| Rust bridge | `rust/src/lib.rs` | UniFFI proc-macros. The `Report`/`Cleaned`/`Finding`/`Block`/`KeepOptions` records. The error field is `reason`, because `message` clashes with Kotlin `Throwable`. |
| Gradle ↔ Rust | `app/build.gradle.kts` | `cargoNdk` → `src/main/jniLibs` (gitignored); `cargoHost` + `uniffiBindings` → `build/generated/uniffi/kotlin`. Unit tests get `jna.library.path=rust/target/release`. |
| I/O | `Photos.kt` | content URIs only. `setRequireOriginal` when ACCESS_MEDIA_LOCATION is granted. 80 MB cap. MediaStore save to `Pictures/Cybermeta`. FileProvider (`cache/clean/`) for sharing. ImageDecoder thumbnails and the HEIC→JPEG step. |
| State | `Model.kt` | `AppModel` (AndroidViewModel). Photo bytes live in a map outside the UI state. A semaphore allows 3 concurrent loads. Prefs: `cybermeta`. |
| UI | `App.kt`, `Screens.kt`, `Settings.kt`, `Widgets.kt`, `Theme.kt` | Compose. `ScrubMark` is the animated logo (same 108-unit geometry as the launcher icon). `GridBackdrop`. 82 palettes via `Palettes.kt` (generated). |
| Activity | `MainActivity.kt` | SEND/SEND_MULTIPLE intake, photo picker, document picker, permission request, write request, share chooser. |
| Brand | `brand/` | `icon.svg` (master), `icon-512.png`, `social.svg`. Launcher icon: `res/drawable/ic_launcher_*.xml` (adaptive and monochrome). |
| Site | `docs/` | `index.html`, `assets/site.css`, `site.js` (themes, hero canvas, cards/modals, phone demo, install tabs), `lab.js` (in-browser JPEG audit and scrub). |

## ⚠️ Gotchas

- **Don't strip in Cargo** (`strip = false`). UniFFI reads its metadata
  from the symbols. AGP strips the APK copies because `ndkVersion` is set;
  without `ndkVersion` the APK grows to 18 MB.
- **`sourceSets.java.directories.add(...)` takes a plain path.** AGP 9
  rejects a Provider there.
- **Lint has `warningsAsErrors`.** Unused resources and the KTX hints fail
  the build.
- **The emulator AVD `Blackbox_API_36` has a lock-screen PIN** that isn't
  recorded anywhere. Don't guess; ask the user or test on the phone.
  Headless swiftshader screenshots come out black.
- **The phone** is a Samsung S25 Ultra on adb. Install and drive it only
  with the user's OK.
- **`site.js` hero tag positions** are in the 900×900 canvas space; the
  photo sits at x 200–700, y 270–630.

## 🧪 Tests

- `app/src/test/.../EngineTest.kt` (7 tests) uses the real engine:
  - audit finds GPS, serial, owner, IPTC, XMP city, comment and the motion
    trailer in `leaky.jpg`
  - share-safe leaves nothing; location-only keeps the serial
  - anonymous camera and date shift work
  - HEIC is refused with a readable reason
  - output names are random
  - 82 unique palettes
- `rust/` has `cargo test` for the bridge.
- The site's lab parser was checked in Node against `docs/assets/sample-leaky.jpg`:
  11 findings, and a clean re-audit after the scrub.

## 📜 History

- 2026-10-19/20: v0.1.0 (first build, site, curl installer, signed
  release). Installed on the user's phone at their request.
