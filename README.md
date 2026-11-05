# Time Tracker for Android

A small, local-only time tracker. Make colored activity buttons, then tap to record what you are doing. The next tap closes the previous interval and starts a new one, including when you tap the same activity again.

Built in Kotlin with Jetpack Compose, Room/SQLite, and a Glance home-screen widget. Requires Android 8.0 (API 26) or newer.

## Use the app

1. Open **Create button**, enter a title, and choose a preset or custom RGB color.
2. Tap a button on Home. After saving, the app confirms **"[title] tracker is set"**.
3. Tap another button to switch activities. There is no background timer to keep alive.
4. Open **Calendar** to browse days and tap entries to edit their endpoints. Date/time pickers are available alongside exact text entry.
5. Use **Move shared boundary** when a correction should also adjust the immediately adjacent interval. Overlaps and future endpoints are rejected.
6. To stop without starting another activity, edit the active calendar entry, clear **Keep tracking**, and save an end time.
7. In **Your buttons**, rename, recolor, reorder, archive, or restore buttons. Archive keeps their history. An active activity must be ended or switched before archiving.

Each endpoint stores its UTC instant, recorded time zone, and offset. The calendar defaults to the phone's current time zone; its zone selector can pin another display zone. Repeated daylight-saving times require an explicit offset selection. The device's configured time zone must be correct; the app does not use GPS.

## Add the widget

Long-press the Android home screen, open **Widgets**, find **Time Tracker**, and drag its widget onto the home screen. Resize it as needed and scroll to reach all buttons. It contains tracker buttons only. Taps save directly without opening the app; the active button gets a check mark.

Widget feedback uses Android's standard text toast and an updated active indication. Toast visibility, widget refresh timing, stopped-app behavior, and scrolling depend on Android and the launcher. Reopen the app if a launcher has stopped it or is showing a stale widget.

## Your data stays on the device

- Activity names and intervals live in the app-private `time-tracker.db` SQLite database.
- No accounts, server, analytics, ads, synchronization, or Internet permission.
- Android cloud backup and device transfer are explicitly excluded. **Uninstalling the app or clearing its storage deletes its tracking history.** There is no export/restore feature in this version.
- `.gitignore` excludes database files and SQLite sidecars, exports, device captures, local tools, signing keys, and build output. `scripts/check-public-content.py` also checks tracked paths before publication and in CI.
- Room's committed `app/schemas/` JSON describes table structure only. It contains no database rows or personal activity data.
- Tests use synthetic records in isolated databases, including a separate in-memory application database for UI/widget tests. They never open the normal tracking database.

SQLite is protected by Android's application sandbox. This app does not add database encryption. Glance/WorkManager may declare platform scheduling permissions for their short widget sessions; no app-owned recurring tracking worker or foreground tracking service is scheduled.

## Build

Install Android Studio, or JDK 21 plus the Android SDK. Install Android SDK Platform 36 and Build Tools 35.0.0, set `ANDROID_HOME`, and accept the SDK licenses. Android Studio can create the ignored `local.properties` SDK path automatically.

Windows PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

macOS / Linux:

```sh
chmod +x gradlew
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

The installable development APK is `app/build/outputs/apk/debug/app-debug.apk`. It uses the development signing key. Release builds are deliberately unsigned; configure your own private signing configuration for distribution. Do not commit signing credentials.

Use a disposable emulator or a dedicated test device for instrumentation. Gradle's test installer can uninstall the app. With that test device connected:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

CI is intentionally disabled. The optional definition in `ci/android.yml` runs unit tests, Android lint, debug/release builds, and SQLite/UI/widget instrumentation tests on an Android emulator. A successful build uploads the debug APK as a workflow artifact. Instrumentation screenshots contain only synthetic test activities. To enable CI, copy this file to `.github/workflows/android.yml` using a GitHub identity authorized to upload workflows.

## Project layout

| Location | Purpose |
| --- | --- |
| `app/src/main/.../data` | Room schema, DAOs, integrity triggers, and serialized transactional repository. |
| `app/src/main/.../model` | Time zones, DST validation, interval arithmetic, readable text colors. |
| `app/src/main/.../ui` | Dashboard, activity editor, day calendar, and endpoint editor. |
| `app/src/main/.../widget` | Scrollable Glance buttons and direct tracking callback. |
| `app/src/test` | Deterministic time/interval tests. |
| `app/src/androidTest` | Real SQLite transaction/constraint tests and Compose UI flows. |

See [ARCHITECTURE.md](ARCHITECTURE.md) and the [architecture image](architecture.png) for design details. PC/LAN support is deferred. Stable IDs and the shared repository allow a future authenticated adapter without adding network activity now.

## Validation limits

Validated on 2026-11-05: debug and unsigned release builds, Android lint (zero errors), 10 unit tests, and 17 instrumentation tests passed. Device tests ran on an Android 15 / API 35 Pixel 2 emulator. They include actual SQLite constraints and rollback, process/database recreation, calendar edits, and a real Android widget host delivering repeated PendingIntent taps. Screenshots were reviewed. The long dashboard was also tested with three-button navigation and 130% text size, in addition to the full suite's gesture-navigation configuration.

Automated checks do not establish a measured battery saving. Physical-device idle comparisons, Android 8–14 runtime compatibility, and additional OEM launchers still require release validation. The native widget-host test requires API 29 or later; the app itself targets API 26 or later. There is no production upgrade migration yet because this is schema version 1; future versions must provide explicit migrations without deleting existing data.
