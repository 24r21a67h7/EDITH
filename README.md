# EDITH

Privacy-first, offline-first, voice-first personal assistant for Android.

## Status

Push-to-talk foundation only: tap **Activate** → on-device speech recognition → EDITH Core → on-device text-to-speech → standby.
There is no wake word yet, no LLM, and no network access (the app declares no `INTERNET` permission).

## Building

The Android project lives in `EDITH/`.

- JDK 17 (set Android Studio's *Gradle JDK*, or `JAVA_HOME`). Do not hard-code a JDK path in `gradle.properties`.
- Android SDK with platform 35.
- `cd EDITH && ./gradlew testDebugUnitTest` runs the JVM unit tests (no device needed).
- `cd EDITH && ./gradlew assembleDebug` builds the debug APK.

## Device requirements (Pixel 9a, Android 17)

- English on-device speech recognition pack installed (one-time download, done in system settings; EDITH never downloads anything itself).
- An installed offline English text-to-speech voice. EDITH refuses to use network-backed voices and says so instead of silently falling back.
