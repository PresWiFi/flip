# Flip

Android launcher styled like a 2000s flip phone, with app blocking and daily limits that are hard to bypass. Unlock by scanning a tag, typing a long code, or using a printed emergency code (same as Upright).

Personal use, sideloaded. Not Play Store compatible (uses device admin, accessibility and usage access).

## Features

**Home screen** (Flip settings → Look)
- **Razr V3:** silver, black or pink. Left soft key = search, right = Flip settings. Menu key / 5-way center = main menu, ring = navigate, green = dial, C = clear, red = idle screen.
- **V3 + outer screen:** starts closed with time and battery. Tap or swipe up to open; closes when the screen turns off.
- **Modern:** same colors on a normal touch layout.
- **Keypad:** dial + T9 search for apps and contacts (4-6-7 → Instagram), dial only, or none (Search soft key brings one up).
- **Main menu:** 3x3 grid of chosen apps. Long-press to change, keys 1–9 to open. Other apps are search-only.

**Blocking** (Flip settings → Blocked apps, Groups)
- Always blocked (unlock every time)
- Daily limit per app, resets at midnight
- Blocked hours on chosen days (can cross midnight)
- Group limits (shared daily total)
- Hide notifications while blocked (needs notification access)
- Applies however the app is opened. Locks within ~10 s if a limit or unlock runs out mid-use.

**Unlocking**
- Quick unlock: 5 min, 3/day
- Scan a tag (NFC, barcode or QR): 15 min
- Type a random 24-character code: 15 min
- Emergency code: 15 min, logged

**Protection** (Flip settings → Protection)
- Uninstall lock (device admin)
- Settings guard (accessibility): backs out of system screens for force stop, uninstall, clear storage, home app, accessibility, device admin, usage access and notification access
- Loosening a rule needs the typed or emergency code: removing/raising a limit, unblocking, shortening hours, adding a tag or quick unlocks, turning protection off. Tightening is free.
- Log of unlocks, emergency uses, blocked screens, wrong codes, loosened rules and force-stops
- All data stays on the phone. No account, no backups.

## Setup

1. Install the APK from [Releases](https://github.com/PresWiFi/flip/releases/latest).
2. Open Flip, tap **Flip** (right soft key), and go through Setup. Each **Fix** button opens the system page:
   - Home app: Flip
   - Usage access, App blocker (accessibility), Uninstall lock
   - Battery: unrestricted (OnePlus: also app info → Battery → Unrestricted)
   - Optional: notification access, contacts (T9)
3. Create the emergency code and store it out of the way.
4. Add tags (Flip settings → Tags) and keep them somewhere inconvenient.
5. Add blocked apps and groups. Fill the main menu.
6. Turn **Protection on** last; it blocks the system pages from step 2.

## Build

JDK 17+ and Android SDK (compileSdk 37).

```
./gradlew testDebugUnitTest     # Logic.kt unit tests
./gradlew assembleRelease       # app/build/outputs/apk/release/app-release.apk
```

Render screenshots without a phone:

```
./gradlew testDebugUnitTest --tests '*ScreensTest*' -PscreensDir=/some/folder
```

## Releases

Bump `versionName` and `versionCode` in `app/build.gradle.kts`, optionally add `release-notes/vX.Y.Z.md`, commit, then:

```
git tag vX.Y.Z && git push origin main vX.Y.Z
```

GitHub Actions tests, builds and publishes `Flip-X.Y.Z.apk`.

Signing key is in repo secrets `FLIP_KEYSTORE_B64` and `FLIP_KEYSTORE_PASSWORD`. All releases must use it to install over each other. Local release builds use it if `flipKeystore` and `flipKeystorePassword` are in `~/.gradle/gradle.properties`; otherwise they use the debug key and can't update a release install.

## Code map

- `Model.kt`: data
- `Logic.kt`: all rules, pure and unit-tested (schedules, usage, blocking, unlocks, loosening, T9, codes)
- `Store.kt`: JSON storage
- `Apps.kt`: usage, installed apps, launching, dialing, contacts
- `GuardService.kt`: blocker and settings guard; `NotifService.kt`: notification hiding
- `HomeActivity.kt`, `BlockActivity.kt`, `SettingsActivity.kt`: home, lock screen, settings
- `ui/`: `V3.kt`, `Pixel.kt`, `Modern.kt`, `Home.kt`, `SettingsScreen.kt`, `Editors.kt`, `Common.kt`
