# Ctrus for Android

Android port of Ctrus (the iOS focus/app-blocking app at github.com/Ctrus-dev/Ctrus---Fresh-Start-Every-Tap).
Separate repo from the iOS project on purpose — no Xcode/Swift tooling here.

- Package: `mo.dev.ctrus`
- Stack: Kotlin + Jetpack Compose, minSdk 27, compileSdk/targetSdk 35
- UI is meant to visually match the iOS app (same theme colors, same screen layouts),
  not to reuse iOS code — this is a from-scratch Kotlin implementation.

## Blocking architecture

Android has no equivalent of iOS's FamilyControls/ManagedSettings/DeviceActivity. After
reviewing Switchly (github: gitlab.com/Saltyy/switchly-public), an open-source, Play
Store-published Android app blocker with the same NFC-tag-unblock concept as Ctrus, this
project follows the same approach:

- **`blocking/BlockingAccessibilityService`** is the primary detection mechanism —
  listens for `TYPE_WINDOW_STATE_CHANGED` accessibility events to know which app just
  came to the foreground. This was a deliberate choice over polling
  `UsageStatsManager` in a foreground service: Switchly's own source comments note
  AccessibilityService is kept alive far more reliably across OEMs than a polling
  foreground service, and they moved off polling for that reason. `UsageStatsManager`
  is worth keeping only as an auxiliary foreground-resolution signal for edge cases
  (e.g. split-screen), not as the primary mechanism.
- Using `AccessibilityService` for this is Play Store policy-sensitive (Google
  tightened enforcement around AccessibilityService misuse in 2026). It requires a
  proper accessibility declaration in the Play Console and clear in-app disclosure of
  why the permission is requested before submitting. Switchly is a live proof this is
  approvable for a genuinely-disclosed focus/blocking use case — don't skip the
  disclosure step when this gets close to a real release.
- **`blocking/BlockSafetyPolicy`** — never-block list (Settings, dialer, system UI, the
  default launcher, Ctrus itself) so a misconfigured profile can't lock the user out of
  their phone. Extend this before wiring up real blocking logic.
- **`blocking/BlockerActivity`** — the full-screen surface shown instead of a blocked
  app (launched by the accessibility service via `Intent` + `FLAG_ACTIVITY_NEW_TASK`,
  with `performGlobalAction(GLOBAL_ACTION_HOME)` first to reliably dismiss the blocked
  app underneath, matching Switchly's `BlockLaunchController`).
- **`blocking/BlockDecisionEngine`** currently always returns `Allow` — there is no
  ported profile/session data model yet, so the accessibility service is inert out of
  the box. Wiring this up to a real "which packages does the active profile block" is
  the next real milestone.

## What's scaffolded vs. real

This is a first-pass scaffold, not a working port:

- `ui/settings/SettingsScreen.kt` and `ui/session/ActiveSessionScreen.kt` mirror the
  iOS `SettingsView.swift` / `ActiveProfileSessionView.swift` layout and copy fairly
  closely (built by reading the actual iOS source), but are wired to placeholder state
  in `MainActivity.kt`, not a real data model.
- `ui/home/HomeScreen.kt` is a minimal placeholder — the real iOS `HomeView.swift`
  (profile list, active-session detection, etc.) was not ported yet.
- No NFC reading, no SwiftData-equivalent persistence (Room, presumably), no profile
  creation flow, no widget, no app-picker for building a profile's blocked-app list.
- The launcher icon (`res/drawable/ic_launcher_*`) is a placeholder shape, not the real
  Ctrus artwork — port the actual icon assets before shipping anything.
- The master unlock code (`MainActivity.kt`, `MASTER_UNLOCK_CODE`) is hardcoded to
  match the iOS value for parity; keep both in sync if it ever changes.

## Build

Builds clean: `./gradlew :app:assembleDebug` succeeds (verified using Android Studio's
bundled JBR as JAVA_HOME, since there's no separate system JDK). Only verified to
compile and package, not run on a device/emulator yet — visually check the screens
against the iOS app once you can launch it, since none of the layout has been eyeballed
outside of reading the source.

Toolchain is current-stable as of when this was last built: Gradle 9.7.0, AGP 9.3.1,
Kotlin 2.4.10, Compose BOM 2026.06.01. AGP 9 has Kotlin support built in, so there's no
separate `org.jetbrains.kotlin.android` plugin or `kotlinOptions` block — don't add them
back if a migration guide/older tutorial suggests it.
