# Caffeine Tracker

Caffeine Tracker is an Android app for logging caffeine, seeing estimated levels across the day and adding favourite drinks directly from a home screen widget. It supports Swedish and English, imports Caffeine Clock history, and offers JSON backups and optional Google Drive backup.

## Install

Download the signed APK and matching SHA-256 file from this repository's Releases page when a release is available. Open the APK on Android 8.0 or later and allow installation from the app you opened it with if Android asks. ADB installation of a compatible update uses `adb install -r Caffeine-Tracker-1.0-release.apk`.

If you previously installed a private build with a different signing certificate, export a full JSON backup in **Settings → Backup and import**, copy it somewhere accessible outside the app and verify that the file can be opened. Pause automatic Drive backup on any device still running the earlier private build before switching devices. Only then remove the private build, install the signed release and restore the JSON. Uninstalling deletes on-device app data. Keep the exported JSON until you have checked your history, favourites and settings in the release.

## Build from source

Install JDK 17 or 21 and Android SDK Platform 34 with Build Tools 34.0.0. Set `JAVA_HOME` and `ANDROID_HOME`, or configure the SDK in Android Studio. From a clean checkout run:

```sh
./gradlew assembleDebug testReleaseUnitTest lintRelease
```

On Windows use `.gradlew.bat`. Gradle downloads dependencies from Google's Maven repository, Maven Central and the Gradle Plugin Portal. The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

For a signed public build, the maintainer supplies `CAFFEINE_KEYSTORE`, `CAFFEINE_STORE_PASSWORD`, `CAFFEINE_KEY_ALIAS` and `CAFFEINE_KEY_PASSWORD` in their build environment. Run `.uild.ps1 -Release -Checks` on Windows to produce the APK and checksum file under `outputs/`. On other systems, use `./gradlew assembleRelease` with those variables set. Maintain one signing key across releases. Keep the store and password outside the repository and back them up separately. The PowerShell script can also load the maintainer's local Windows-protected credentials from the Android configuration directory. Those credentials are tied to that Windows login, so keep a separate recovery copy of the signing password in a password manager.

The visible version is maintained in `app/build.gradle.kts` and the PowerShell build script advances `build-number.properties` for packaged builds. Use `-NoBump` for local verification with the current number.

## Backups and privacy

Intake history, favourites and preferences live in a local SQLite database. Logging and the widget work offline without an account. A full backup includes drinking times, quantities, notes, recipes and app preferences. JSON backups are readable files, so store them somewhere you trust. Full restores show a preview and save a local recovery copy before replacing app data.

Android system cloud backup is disabled for app data. On Android 12 and newer the production database can be transferred directly to another phone through Android's device-transfer feature. For other transfers, use a JSON export or opt in to Drive backup.

The Drive option requests access to Drive files associated with Caffeine Tracker. It uploads a full backup to the chosen Google account only when automatic backup is enabled or you start a backup. The backup is protected in transit and is readable by the account holder. Disconnecting stops future uploads and leaves existing files in Drive. You can also download a Caffeine Tracker backup from Drive and restore the file from Settings, which works independently of Google sign-in. You can remove those files from Drive yourself. If a new phone finds an existing backup, review and restore it before making that phone the backup source. When uploads conflict, the app pauses and keeps the competing copies. A successful upload keeps the previous verified copy. Older versions pruned from the normal backup list are moved to Drive Trash. See [release verification](docs/RELEASE_VERIFICATION.md) for checking authorization with the distribution certificate.

## Credits and source terms

Caffeine Tracker reuses components from Roinur's Sauce Tracker Rewrite and Book Tracker. Caffeine estimates are a model of absorption and elimination and depend on the recorded quantities and timings. See [source provenance](BASE_PROVENANCE.txt), [source terms](LICENSE) and [third-party notices](THIRD_PARTY_NOTICES.md).
