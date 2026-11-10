# App Lock

A private, on-device Android app lock built with Kotlin and Jetpack Compose.

## Download

Get the latest APK from [Releases](https://github.com/jellyfisshhh/KiriAppLock/releases) (`KiriAppLock-v1.1.apk`), install it, and grant Usage access + the App Lock accessibility service on first launch.

> Initial releases are debug-signed for sideloading; production signing will be added separately.

## Features

- **First-launch setup** that walks through the permissions the lock needs:
  usage access, unrestricted background use, and the App Lock accessibility
  service (with a plain-language explanation of what it does and does not read).
- **Material You dynamic color** on Android 12 and newer (`dynamicLightColorScheme` /
  `dynamicDarkColorScheme`), with a standard Material 3 scheme on older devices.
- **Theme selection** — Follow System (default), Light, or Dark.
- **PIN keypad** with optional **Scramble Keypad** mode. Digits 0–9 are shuffled with
  `SecureRandom` every time the lock screen opens; Delete and Continue stay in their
  usual bottom-row positions.
- **Biometric unlock** using `BiometricPrompt` when the device supports it, including an
  option to **launch the biometric prompt automatically** when the lock screen opens
  (on by default once biometrics are enabled).
- **Protected application enforcement** through a user-enabled accessibility service.
  When a protected app comes to the foreground, a PIN gate is shown before it can be
  used. Usage access is used as a foreground-detection fallback.
- **Fake PIN** that opens a decoy interface, optionally launching a selected decoy app.
- **Protected applications** list with per-app switches and search.
- **Relock behavior** — immediately, when the screen turns off, after 1 minute, or after
  5 minutes.
- **PIN length** from 4 to 8 digits.
- **Permissions section** in Settings for reviewing and changing every permission later.
- On-device settings persistence; PINs are stored only as salted PBKDF2 hashes and are
  never logged.
- No network access, analytics, ads, or trackers.

> Note: `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` and accessibility-based app locking are
> restricted for some Play Store distribution paths. This project is intended for local
> builds and sideloading unless the distribution policy is reviewed.

## Build

Toolchain used for local builds:

- JDK 17: `/home/jellyfish/toolchains/jdk-17.0.20.1+1`
- Android SDK: `/home/jellyfish/toolchains/android-sdk`
- Gradle 8.13

```bash
JAVA_HOME=/home/jellyfish/toolchains/jdk-17.0.20.1+1 \
ANDROID_HOME=/home/jellyfish/toolchains/android-sdk \
/home/jellyfish/toolchains/gradle-8.13/bin/gradle :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Package

`com.example.applock`
