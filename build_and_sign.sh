#!/usr/bin/env bash
# Local build + sign helper (macOS / Linux). Requires `gradle` and `keytool` on PATH.
set -e

if ! command -v gradle >/dev/null 2>&1; then
  echo "ERROR: 'gradle' not found. Install Gradle, or just open the project in Android Studio."
  exit 1
fi

if [ ! -f keystore.properties ]; then
  echo "No keystore.properties found -> creating a local debug keystore..."
  keytool -genkeypair -v -keystore yangshipin-release.keystore -alias yangshipin \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=YangshipinTV" \
    -storepass android -keypass android
  printf 'storeFile=yangshipin-release.keystore\nstorePassword=android\nkeyAlias=yangshipin\nkeyPassword=android\n' > keystore.properties
fi

gradle assembleRelease
echo
echo "Done. Signed APK: app/build/outputs/apk/release/app-release.apk"
