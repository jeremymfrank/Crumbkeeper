# Recover recipes from earlier Crumbkeeper debug APKs (advanced / USB)

Earlier test builds have private SQLite databases that **new package IDs cannot access directly**. In-place updates are not always possible because GitHub's debug signing key was not stable across workflow runs.

**Do not uninstall the old app.** Export first and verify the backup.

## Prerequisites

- Windows, Linux or macOS with Python 3.8 or newer and Android SDK Platform Tools (`adb`) installed.
- Android device with Developer options > USB debugging enabled; accept the computer's USB debugging prompt.
- Your original debuggable Crumbkeeper APK still installed, with its recipes intact.
- This tool reads your own installed app using Android's `run-as`. It does not require rooting the device.

## Export

From the repository root, for the original **v0.1.0-dev** package:

```powershell
python tools/export_legacy_android.py --package com.crumbkeeper.app --output Original-Crumbkeeper.crumbbook
```

For the **v0.1.1-dev** side-by-side package:

```powershell
python tools/export_legacy_android.py --package com.crumbkeeper.app.dev --output Test-Crumbkeeper.crumbbook
```

If `adb` is not on PATH, pass `--adb "C:\\Users\\you\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe"` with the actual location. With multiple connected devices, disconnect the others first.

The tool copies `crumbkeeper.db` (plus any WAL files) and the photo files using read-only commands, writes a standard `.crumbbook` ZIP on **your computer**, and reports the exported recipe/photo count. It does not change any old recipes or app installations.

**After export:** Move the `.crumbbook` to the device and use **Import File** in Crumbkeeper v0.1.2-dev. Verify titles, text, timers and photos before removing any older app. Keep another copy of the backup off-device.

## Failure modes

- `run-as: package not debuggable`: this method works only with previous debug builds.
- `package not found`: wrong package ID or old app is not installed.
- No saved recipes / corrupt DB / missing photo: the tool fails instead of silently creating a partial backup.
- If the new app import rejects a ZIP, leave the old app installed. Do not delete source files.

This is an **optional advanced recovery tool**, locally tested with a synthetic v0.1-era SQLite database and cover photo. It has not yet been tested against the user's actual Android tablet.
