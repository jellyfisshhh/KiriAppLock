# KiriAppLock — Agent Notes

Single-module Android app (`:app`, package `com.example.applock`). Kotlin + Jetpack Compose + Material 3. No tests, no CI, no `opencode.json` in repo. Public repo: https://github.com/jellyfisshhh/KiriAppLock (no LICENSE).

## Commands

```bash
export JAVA_HOME=/home/jellyfish/toolchains/jdk-17.0.20.1+1
export ANDROID_HOME=/home/jellyfish/toolchains/android-sdk
G=/home/jellyfish/toolchains/gradle-8.13/bin/gradle
$G :app:assembleDebug      # APK: app/build/outputs/apk/debug/app-debug.apk (debug-signed)
$G :app:assembleRelease    # app-release-unsigned.apk — NO signingConfig, not installable
$G :app:lintDebug          # 0 errors; 7 warnings, 1 info (pre-existing)
A=$ANDROID_HOME/platform-tools/adb
$A install -r app/build/outputs/apk/debug/app-debug.apk
$A shell dumpsys window | grep -m1 mCurrentFocus   # focused activity
$A shell uiautomator events                        # live accessibility events
$A shell run-as com.example.applock cat shared_prefs/app_lock_settings.xml
```

Local helpers (not committed; may not exist in a fresh environment): `/tmp/opencode/ui.sh` dumps UI text+bounds and can tap by label; `/tmp/opencode/digits.sh <pin>` taps PIN digits using the live scrambled layout.

## Device

`ZA223G4P99` (`moto_g35_5G`), Android 15 / API 35, 1080×2400 @ 400dpi. Current on-device config: PIN `0401`, scramble ON, relock `IMMEDIATELY`, protected apps include Settings, Reddit, WhatsApp, Signal, Waterfox, Fossify Notes/Gallery/Filemanager, Discord; decoy = Aurora Store. Restore relock to `IMMEDIATELY` and theme to `SYSTEM` after testing.

## Architecture

- `MainActivity` (singleTask) → `AppRoot`/`SettingsScreen`; its own in-app `AppSession` is separate from `LockSessionManager`.
- `LockActivity` (singleTask, `taskAffinity=.lock`, `excludeFromRecents`) → `LockScreen`.
- `AppLockAccessibilityService` gates protected apps; usage access is only a fallback. Never `canRetrieveWindowContent`; never log PINs, keypad order, or package names from events.
- `LockSessionManager` is the single source of truth: per-package session map (`package → authenticatedAt`), lock-overlay dedupe (`pendingLockPackage`/`lockScreenPackage`).
- `data/AppSettings.kt` + `data/SettingsRepository.kt` persist all settings (`app_lock_settings` prefs).

## Release

- `gh` is at `~/.local/bin/gh` (not on PATH). Git identity is set locally to `jellyfisshhh` / `345443604+jellyfisshhh@users.noreply.github.com`; do not change it globally.
- `assembleRelease` has no `signingConfig` → `app-release-unsigned.apk`. The published `KiriAppLock-v1.0.apk` is the debug-signed build.
- To cut a release: build the debug APK, copy it to a versioned name, then `gh release create vX.Y <apk> --title "vX.Y" --notes "..."`. Do not commit the APK (`.gitignore` excludes it).

## Gotchas

- Enforcement needs **both** accessibility service enabled *and* usage access. Force-stopping App Lock removes it from `enabled_accessibility_services` — re-enable without the UI:
  ```bash
  $A shell settings put secure enabled_accessibility_services \
    'com.example.applock/com.example.applock.service.AppLockAccessibilityService:de.mm20.launcher2.release/de.mm20.launcher2.globalactions.LauncherAccessibilityService'
  $A shell settings put secure accessibility_enabled 1
  ```
- Kvaesitso (`de.mm20.launcher2.release`) has its own accessibility service in the same list — always preserve it.
- `uiautomator dump` can be stale right after app switches; wait ~2.5s and re-dump. `adb shell input tap` drops taps at <0.4s spacing.
- `com.reddit.frontpage` has alias `launcher.default` — launch with `am start -n com.reddit.frontpage/launcher.default` (MAIN launcher intent does not resolve). `com.google.android.googlequicksearchbox` has no launcher entry; use Calculator (`com.google.android.calculator`) for an unprotected-app test.
- `ui.sh` skips nodes without `bounds` (Reddit WebView), so its dump may be sparse there — fall back to `dumpsys window` for focus.
- Scramble keypad order is seeded per lock-screen session. `LockActivity` keeps an observable `keypadSession` (saved across rotation) and increments it on every `onNewIntent`; `LockScreen` keys its `rememberSaveable` state on it. Do not pass a constant `0` again or the keypad will not reshuffle on re-lock.

## Relock semantics

- `IMMEDIATELY` = **lock when the protected app leaves the foreground** for any other real app (Home/other app counts). Screen-off also expires sessions for this policy. Delegate/non-app windows, Custom Tabs, internal activity changes, and system overlays never count as leaving.
- `SCREEN_OFF` = survive app switches; relock only on screen-off.
- Timed (1/5 min) = survive switches and screen-off; expire by session age.
- Delegate frames (e.g. Settings → Google settings in `com.google.android.gms`): filtered by `isApplicationWindow` + `SYSTEM_SURFACE_PACKAGES`.
- Custom Tabs: `*CustomTabActivity*` windows are ignored — they belong to the calling app's flow. Regular browser tabs *do* count as leaving.

## Layout rules

- Portrait: keypad pinned to bottom (header top). Short windows (<520dp) scroll instead of clamping.
- Landscape: two columns; `keypadLeftInLandscape` (default off) swaps to keypad-left, prompt-right. Use `BoxWithConstraints` + `safeDrawingPadding()`. No forced orientation.
- Verified bounds on test device: portrait last keypad row y≈2062; landscape standard keypad x≈1522–2102, left-handed x≈406–986.
