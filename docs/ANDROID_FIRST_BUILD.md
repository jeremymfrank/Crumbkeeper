# Crumbkeeper Android v0.1.0-dev — First APK Source

**Build status: SOURCE CREATED / APK NOT BUILT OR TESTED.** The code was prepared in an environment without an Android SDK, Android Gradle Plugin artifacts, or network access to Gradle/Google repositories. No binary can be responsibly labeled as a working APK yet.

## Included in this first build

- Native Kotlin / Jetpack Compose Android project, tablet/phone adaptive layout.
- Dark theme by default; Settings menu offers light theme and Cooking Mode keep-awake toggle (default on).
- Offline SQLite recipe library (initial implementation; Room migration planned).
- New and edit recipe, ingredients, individual steps, notes, cover photo from gallery.
- Paste from clipboard or accept text shared from another Android app; deterministic offline parser suggests timers and preserves original input and warnings.
- Full, single-screen Cooking Mode, recipe ingredients with checkmarks, steps and inline timers. Active countdowns remain pinned above scrolling recipe content.
- Start, pause, resume, restart; running timer duration cannot be modified. Cancel requires confirmation.
- Cooking session timer deadlines and checkmarks saved locally; app launch can restore the active cooking session.
- First design-system palette and approved Gothic-tech SVG adaptive launcher icon.

## Explicitly incomplete

- **No compiled APK, emulator test, or on-device validation yet.** First test must run in Android Studio or a correctly configured CI runner.
- **No locked-screen alarm/notification scheduling yet.** Countdown deadlines persist, but v0.1 only displays completion while the application is open. Do not rely on this version for critical food-safety timing with the screen locked.
- No `.crumbrecipe` ZIP sharing/import, cookbook backup/restore, step-level photos, or ingredient quantity scaling yet.
- No guaranteed correct behavior across OS process death or device reboot until device testing is completed.
- No release signing or Google Play deployment.
- Android launcher icon resources are source designs; actual launcher mask rendering requires an Android test build.

## Building the first debug APK

Prerequisites: Android Studio with Android SDK Platform **35**, Android SDK Build Tools **35.0.0**, Java **17** or newer, and Gradle **8.11.1** (Android Gradle Plugin 8.9.2).

1. Open this project directory in Android Studio.
2. Allow Gradle to sync and download the project's dependencies.
3. If the IDE requests a wrapper JAR, use an installed Gradle 8.11.1 to run `gradle wrapper --gradle-version 8.11.1` once. The `gradle/wrapper/gradle-wrapper.properties` is provided; a wrapper JAR is **not** bundled.
4. Use **Build > Build Bundle(s) / APK(s) > Build APK(s)** or execute `gradle :app:assembleDebug` in the project directory with the correct Gradle version.
5. Expected output: `app/build/outputs/apk/debug/app-debug.apk` **after a successful build**.
6. Install on the tablet/phone and test in airplane mode. **Do not claim PASS until it is actually installed and exercised**.

## Test notes

The platform-independent `Recipe.kt` and `RecipeParser.kt` compiled using Kotlin 1.9.0 on a local JVM. Executed parser smoke tests passed: ChatGPT-style Markdown title/yield/ingredients/steps extraction, two unapproved timers, source retention, range warning, and raw-text fallback.

These tests **do not** validate the Android application, UI compilation, SQLite, or timer behavior.

## Why it does not include a release APK

Release packaging requires a tested Android SDK build plus an appropriate signing key. The first milestone is a debug APK tested on real hardware before generating a signed release.
