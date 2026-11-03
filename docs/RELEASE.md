# Crumbkeeper 1.1.0

The release app is named **Crumbkeeper**, with application ID `com.crumbkeeper.app` and version code `10100`.

Distribution files:

- `Crumbkeeper-v1.1.0.apk` — signed, non-debuggable Android app.
- `Crumbkeeper-v1.1.0-Android.zip` — the same APK, install/migration instructions, release notes and checksums.

## Changes

Gilded Grimoire provides a charcoal/ivory/gold theme, serif headings, plain recipe text, adaptive phone/tablet photo cards, an Add Recipe menu and cookbook tools. The supplied emblem is shown in the header and in full in About. Light appearance remains available.

Photos are decoded off the UI thread at bounded resolution with all eight EXIF rotation/reflection states handled. Card framing uses normalized position and zoom; originals remain unchanged for full viewing, backup and sharing. Existing images default to centered framing. Missing images get an illustrated cover. New imports are limited to the archive's 12 MB photo limit so the cookbook remains portable. See [photo behavior](PHOTOS_AND_FRAMING.md).

Every added timer with a positive duration is available in Cooking Mode. The separate approval checkbox is removed. Old `approved: false` fields are ignored when loading recipes and archives, making previously hidden timers available without re-editing recipes. Timers remain stopped until Start is pressed. The first explicit Timer line replaces inferred timers for its step; subsequent explicit lines preserve independent timers without duplicates.

Timers stay inside their own recipe steps in every state. Secondary actions keep their space before a timer starts, so Cancel or Dismiss cannot resize the card. Starting a timer never creates a section above the recipe.

Local `.crumbrecipe` and `.crumbbook` files open the existing validated import preview. Shared streams, Crumbkeeper MIME types, ZIP/octet-stream document providers and named file attachments are supported. Cold launches and new intents use the same importer as Import File. Incoming files wait while the user is cooking or editing.

## Build and sign

Use JDK 17, Gradle 8.11.1, Android platform 35 and build-tools 35.0.0. The permanent release keystore is retained separately from source and distribution files.

Set `CRUMBKEEPER_KEYSTORE_PATH`, `CRUMBKEEPER_STORE_PASSWORD`, `CRUMBKEEPER_KEY_ALIAS` and `CRUMBKEEPER_KEY_PASSWORD` for a signed build. Without signing configuration, Gradle emits an explicitly unsigned APK. The distribution packaging script rejects unsigned/debug APKs.

Run `gradle :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease`, then `python3 tools/package_release.py --apk app/build/outputs/apk/release/app-release.apk --sdk-root <sdk-path> --output-dir dist --source-commit <commit>`.

The packaging script verifies app identity, version, label, non-debuggable flag, alignment, APK v2 signature and certificate before naming the APK/ZIP. The ZIP is checked against the APK checksum. CI runs unit tests, lint, compilation and emulator checks; it never publishes an unsigned APK as a distribution release.

Keep the private signing key and credentials out of source and public install ZIPs. Use that same key for every future release.

## Existing installations

Install 1.1.0 over the signed 1.0.0 release. The application ID and signing key are unchanged, and the local cookbook is retained. Photo framing is an additive JSON field; old recipes need no destructive database migration. Keep regular cookbook backups.

The latest preview uses `com.crumbkeeper.app.dev5`. Export its cookbook, install the release beside it, then import the backup and verify recipes before removing the preview. The original 0.1.0 prototype used the production package ID with a temporary debug key; preserve its cookbook first if that old installation causes a signature conflict.

## Timer alerts

Sound runs while Cooking Mode is open. Android background/locked-screen system alarms are outside this release.
