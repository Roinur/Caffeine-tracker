# Release verification

1. Build from a clean checkout with the Gradle wrapper. Run release JVM tests and lint.
2. Build the signed candidate using the permanent certificate. Verify it with Android Build Tools `apksigner verify --print-certs` and check the APK's version name, version code and SHA-256.
3. Register the Android package `com.roinur.caffeinetracker` and public release certificate SHA-1 as an Android OAuth client in the existing Google Cloud project. Confirm the Drive API and consent publishing status. Keep the existing OAuth project identity when availability of earlier backups matters.
4. On a spare Android device or profile with Google Play services, sign in with a normal account and try upload, verified read-back, disconnect, fresh-install recovery and restore. Test a new device with an existing backup and two devices writing close together. Test local JSON recovery, widget quick add and Undo, and an update from a prior build signed with the release certificate.
5. Review the source file list and APK contents. Publish the signed APK, SHA-256 and release notes to the chosen repository after the device checks pass.
