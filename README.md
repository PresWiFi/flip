# Flip

An Android home screen styled after a 2000s flip phone, with app blocking and daily time limits that are hard to get around. Unlocking works like Upright: scan a tag, type a long code, or use the printed emergency code.

Personal use, sideloaded. Not for the Play Store (it uses device admin, an accessibility service and usage access on purpose).

## What it does

**Home screen** (Flip settings → Look)
- **Razr V3**: the open phone in silver, black or pink, with an earpiece slot, a barrel hinge, a blue pixel-font screen, and a flat etched keypad with blue backlit symbols. The left soft key opens search and the right one opens Flip settings. The menu key and the center of the 5-way ring open the main menu, the ring moves the highlight through the menu and search results, the green key dials, C clears, and the red key goes back to the idle screen.
- **V3 + outer screen**: starts "closed", showing a small outer display with the time and battery. Tap or swipe up to open it. It closes again when the screen turns off.
- **Modern**: the same blue colors on a normal touch home screen.
- **Keypad**: dial + T9 search for apps and contacts (4-6-7 finds Instagram), dial only, or no keypad. With no keypad, the Search soft key brings one up.
- **Main menu**: 9 apps of your choice in a 3x3 grid. Long-press a slot to change it. In the menu, keys 1–9 open the matching slot. Every other app is reachable only through search.

**Blocking** (Flip settings → Blocked apps, Groups)
- **Always blocked**: needs an unlock every time.
- **Daily limit per app**: locks for the rest of the day once used up, and resets at midnight.
- **Blocked hours**: e.g. 10 PM – 7 AM, on chosen days. Windows can run past midnight.
- **Group limits**: several apps share one daily total.
- **Hide notifications** while an app is blocked (per app, needs notification access).
- Blocking works however the app is opened: from Flip, recents, a notification or a link. If a limit or an unlock runs out while the app is open, it locks within about 10 seconds.

**Unlocking** (the lock screen over a blocked app)
- **Quick unlock**: 5 minutes, 3 per day, no scan needed.
- **Scan a tag** (NFC card/sticker or barcode/QR): 15 minutes.
- **Type a code**: type a random 24-character code shown on screen, for 15 minutes.
- **Emergency code**: the printed code, 15 minutes. It's logged.

**Protection** (Flip settings → Protection)
- Uninstall lock (device admin).
- Settings guard (accessibility): backs out of system screens that show Flip next to force stop, uninstall, clear storage, home app, accessibility, device admin, usage access or notification access.
- With protection on, **loosening** anything needs the typed code (or the emergency code). Loosening means removing or raising a limit, unblocking an app, shortening blocked hours, adding a tag, adding quick unlocks, or turning protection off. Tightening is always free.
- Log: every unlock, quick unlock, emergency use, blocked settings screen, wrong code, loosened rule and force-stop, with the time.
- Everything stays on the phone, with no account and no backups.

## Setup on the phone

1. Download the APK from [Releases](https://github.com/PresWiFi/flip/releases/latest) on the phone and install it (allow installs from your browser when asked).
2. Open Flip, tap the right soft key (**Flip**), and work through Setup. Each **Fix** button opens the right system page:
   - **Home app**: pick Flip.
   - **Usage access**, **App blocker** (accessibility), **Uninstall lock**.
   - **Battery: unrestricted**. On OnePlus also go to app info → Battery → **Unrestricted**.
   - Optional: **Notification access** (to hide notifications), **Contacts** (for T9 search).
3. Make the emergency code, write it down, and keep it somewhere out of the way.
4. Add tags (Flip settings → Tags), and keep them somewhere you'd have to get up for.
5. Add blocked apps and groups. Fill the main menu (open the menu and long-press a slot).
6. Turn **Protection on** last. With it on, the system pages from step 2 are blocked.

## Build

Needs JDK 17+ and the Android SDK (compileSdk 37).

```
./gradlew testDebugUnitTest     # unit tests for the blocking rules (Logic.kt)
./gradlew assembleRelease       # app/build/outputs/apk/release/app-release.apk
```

Screenshots of the main screens, rendered without a phone:

```
./gradlew testDebugUnitTest --tests '*ScreensTest*' -PscreensDir=/some/folder
```

## Releases

Bump `versionName` and `versionCode` in `app/build.gradle.kts`, optionally add notes in `release-notes/vX.Y.Z.md`, commit, then:

```
git tag vX.Y.Z && git push origin main vX.Y.Z
```

GitHub Actions tests, builds and publishes `Flip-X.Y.Z.apk` on the Releases page.

Releases are signed with Flip's own key, kept in the repo secrets `FLIP_KEYSTORE_B64` and `FLIP_KEYSTORE_PASSWORD`. Every release must use the same key, or it won't install over the previous one. Local release builds use the same key if `flipKeystore` and `flipKeystorePassword` are set in `~/.gradle/gradle.properties`. Otherwise they fall back to the debug key, and those builds can't update a release install.

## Code map

- `Model.kt`: data (rules, groups, tags, unlocks, log, settings).
- `Logic.kt`: all the rules, pure and unit-tested: schedules, usage from Android's events, whether an app is blocked and why, unlocks and quick unlocks, which changes count as loosening, T9, codes.
- `Store.kt`: one JSON file.
- `Apps.kt`: today's usage, installed apps and icons, opening apps, dialing, contacts.
- `GuardService.kt`: the blocker and the settings guard. `NotifService.kt`: hides notifications.
- `HomeActivity.kt`: the home screen. `BlockActivity.kt`: the lock screen. `SettingsActivity.kt`: Flip settings.
- `ui/`: `V3.kt` and `Pixel.kt` (V3 looks and the pixel font), `Modern.kt`, `Home.kt` (launcher state, T9 results), `SettingsScreen.kt`, `Editors.kt`, `Common.kt`.
