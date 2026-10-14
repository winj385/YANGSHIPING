@echo off
setlocal
where gradle >nul 2>nul || (
  echo ERROR: gradle not found on PATH. Install Gradle, or just open the project in Android Studio.
  exit /b 1
)

if not exist keystore.properties (
  echo No keystore.properties found -- creating a local debug keystore...
  keytool -genkeypair -v -keystore yangshipin-release.keystore -alias yangshipin -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=YangshipinTV" -storepass android -keypass android
  echo storeFile=yangshipin-release.keystore> keystore.properties
  echo storePassword=android>> keystore.properties
  echo keyAlias=yangshipin>> keystore.properties
  echo keyPassword=android>> keystore.properties
)

call gradle assembleRelease
echo.
echo Done. Signed APK: app\build\outputs\apk\release\app-release.apk
endlocal
