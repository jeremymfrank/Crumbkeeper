# Crumbkeeper

**An offline Android cookbook built for actual cooking.**

Crumbkeeper keeps ingredients, instructions, photos, notes and independent timers in one full-screen cooking view on tablets and phones. No account or cloud service is required.

## Android release — 1.0.0

The app is named **Crumbkeeper**. Distribution files are `Crumbkeeper-v1.0.0.apk` and `Crumbkeeper-v1.0.0-Android.zip`. See the [release and installation guide](docs/RELEASE.md).

- Timers stay in their recipe steps, preserving your reading position.
- Open or share `.crumbrecipe` and `.crumbbook` files directly into Crumbkeeper.
- Import pasted text, local documents and PDFs, with bundled offline OCR.
- Share individual recipes with photos and timer presets; back up the cookbook.
- Protected editing, local notes and photos, plus recoverable recipe Trash.
- Dark theme, checkboxes, screen-awake option and foreground timer sounds.

Android 8.0 or newer is required. Timer alerts run while Cooking Mode is open; locked-screen/background system alarms are outside version 1.0.0.

## Moving from a preview

Use **Back Up Cookbook** in the existing preview. Install Crumbkeeper, then use **Import File** to import the saved `.crumbbook`. Verify recipes before removing the preview. [Migration details](docs/RELEASE.md#existing-installations).

## Documentation

- [Release, installation and signing](docs/RELEASE.md)
- [ChatGPT recipe text format](docs/CHATGPT_IMPORT.md)
- [Backup and recipe sharing](docs/RECIPE_PORTABILITY.md)
- [PDF import and OCR](docs/PDF_IMPORT.md)
- [Protected recipe editing](docs/RECIPE_PROTECTION.md)
- [Trash and recovery](docs/TRASH_AND_RECOVERY.md)
- [Project specification](docs/PROJECT_SPEC.md)

The Android workflow runs unit tests, lint, release compilation and emulator checks. Private signing material is kept outside source and public distribution packages.
