#!/usr/bin/env python3
"""Verify a signed release APK and create the Android distribution files."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--sdk-root", type=Path, default=os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT"))
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--source-commit", required=True)
    args = parser.parse_args()
    if not args.sdk_root:
        parser.error("--sdk-root or ANDROID_HOME is required")
    tools = args.sdk_root / "build-tools" / "35.0.0"

    def run(*command):
        return subprocess.check_output([str(x) for x in command], text=True, stderr=subprocess.STDOUT)

    signature = run(tools / "apksigner", "verify", "--verbose", "--print-certs", args.apk)
    if "Verified using v2 scheme (APK Signature Scheme v2): true" not in signature:
        raise SystemExit("Distribution requires a valid APK Signature Scheme v2 signature")
    if "CN=Android Debug" in signature:
        raise SystemExit("A debug signing key cannot be used for distribution")
    digest = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)", signature)
    if not digest:
        raise SystemExit("Could not verify the release certificate")
    run(tools / "zipalign", "-c", "4", args.apk)
    badging = run(tools / "aapt", "dump", "badging", args.apk)
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not package or package[1] != "com.crumbkeeper.app" or "application-debuggable" in badging:
        raise SystemExit("Not a non-debuggable Crumbkeeper release APK")
    if "application-label:'Crumbkeeper'" not in badging:
        raise SystemExit("Application must be named Crumbkeeper")
    version = package[3]
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise SystemExit("Distribution requires a release version without a preview suffix")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    apk = args.output_dir / f"Crumbkeeper-v{version}.apk"
    shutil.copyfile(args.apk, apk)
    sha256 = hashlib.sha256(apk.read_bytes()).hexdigest()
    info = {
        "app": "Crumbkeeper", "version": version, "versionCode": int(package[2]),
        "applicationId": package[1], "minimumAndroid": "8.0 (API 26)",
        "sourceCommit": args.source_commit, "apk": apk.name, "apkSha256": sha256,
        "signingCertificateSha256": digest[1].lower(), "debuggable": False,
    }
    readme = f"""CRUMBKEEPER {version}

Install {apk.name} on an Android phone or tablet running Android 8.0 or newer.
When Android asks, allow installation from the app used to open the APK.

MOVING FROM A PREVIEW
In the existing preview, choose Back Up Cookbook and save the .crumbbook
file outside the app. Install Crumbkeeper, then use Import File to bring
that backup into the release. The latest preview and release can stay
installed side by side. Verify recipes before removing the preview.
If an early prototype conflicts with this package, preserve its backup
before removing that old installation.

RECIPES AND TIMERS
Tap a .crumbrecipe or .crumbbook file and choose Crumbkeeper to preview an
import. Android may show Open With; choose Always if desired.
Attachments can also be shared to Crumbkeeper or opened with Import File.
Timers stay in their recipe steps through every state. Audible alerts run
while Cooking Mode is open; locked-screen/background system alarms are
not included in this release.

UPDATES
Future releases use this same application ID and signing key, allowing
Android to update the release while preserving the local cookbook.
Keep cookbook backups outside the app.

Source: https://github.com/jeremymfrank/Crumbkeeper
Source commit: {args.source_commit}
"""
    notes = f"""Crumbkeeper {version}

- Gilded Grimoire theme, supplied emblem and adaptive photo recipe cards.
- Upright camera photos, adjustable cover framing and full-photo viewing.
- Original photos and framing survive backup and recipe sharing.
- Added and restored timers are available without an enable/approval step.
- Timers remain in their recipe steps through start, pause, finish and reset.
- Timer controls reserve their space to preserve the reading position.
- Open or share .crumbrecipe and .crumbbook attachments directly into the app.
- Existing validation and import preview protect the saved cookbook.
- External imports wait until cooking or unsaved editing is finished.
- Release app name, permanent signing and versioned APK/ZIP filenames.
- Offline recipes, photos, PDF OCR, backups, sharing and recoverable Trash.
"""
    archive = args.output_dir / f"Crumbkeeper-v{version}-Android.zip"
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED) as out:
        out.write(apk, apk.name)
        out.writestr("INSTALL.txt", readme)
        out.writestr("RELEASE_NOTES.txt", notes)
        out.writestr("SHA256SUMS.txt", f"{sha256}  {apk.name}\n")
        out.writestr("release.json", json.dumps(info, indent=2) + "\n")
    with zipfile.ZipFile(archive) as check:
        assert check.testzip() is None
        assert hashlib.sha256(check.read(apk.name)).hexdigest() == sha256
    print(json.dumps({"apk": str(apk), "distributionZip": str(archive), "verified": info}, indent=2))


if __name__ == "__main__":
    main()
