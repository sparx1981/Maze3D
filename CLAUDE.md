# Marblix (Maze3D)

Android 3D marble maze game. Kotlin + OpenGL ES 3.0, no third-party dependencies.
Package (Google Play): `com.marblix.game`. Namespace in code: `com.maze3d`.

## Builds
- Debug APK: `gradle :app:assembleDebug` → copy to `dist/Marblix-debug.apk`.
- Release bundle for Google Play: `gradle :app:bundleRelease` → `dist/Marblix-<version>-release.aab`.
  Bump `versionCode` in `app/build.gradle.kts` for every Play upload.
- Sounds are generated from maths: `python3 tools/audio/generate.py <out dir>` (copy only the files you changed into `app/src/main/res/raw`).
- Store listing text and graphics: `dist/playstore/`.

## Signing key and backups
- The Play **upload key** lives in `release/` (`marblix-upload.jks` + `keystore.properties`). It is git-ignored and must
  never be committed.
- The owner keeps the upload key and a full project backup in their Google Drive, in the folder
  **"My Android App Creations"**. If `release/` is missing, ask the owner to restore it from there.
  Without it, a signed release can't be built; the upload key can be reset in Play Console → App integrity.
