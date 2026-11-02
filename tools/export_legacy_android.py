#!/usr/bin/env python3
"""Export Crumbkeeper v0.1.0 / v0.1.1 debug recipes over USB to .crumbbook.

Run only with your own debuggable test app, with Android USB debugging enabled.
Requires Android platform-tools (adb) and Python 3.8+; no third-party modules.
Never modifies the source installation. Do not uninstall it before verifying the backup.
"""
import argparse
import json
import pathlib
import re
import sqlite3
import subprocess
import sys
import tempfile
import zipfile

DEBUG_PACKAGES = ("com.crumbkeeper.app", "com.crumbkeeper.app.dev")
SAFE_NAME = re.compile(r"^[a-zA-Z0-9_-]{1,128}$")
SAFE_BASENAME = re.compile(r"^[a-zA-Z0-9._-]{1,128}$")


def adb_read(adb: str, package: str, path: str, optional: bool = False):
    args = [adb, "exec-out", "run-as", package, "cat", path]
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    if result.returncode:
        if optional:
            return None
        msg = result.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"Could not read {path!r} from {package}: {msg or 'adb error'}")
    return result.stdout


def export(adb: str, package: str, output: pathlib.Path):
    with tempfile.TemporaryDirectory(prefix="crumbkeeper-export-") as temp:
        base = pathlib.Path(temp)
        for suffix in ("", "-wal", "-shm"):
            content = adb_read(adb, package, "databases/crumbkeeper.db" + suffix,
                               optional=bool(suffix))
            if content is not None:
                (base / ("crumbkeeper.db" + suffix)).write_bytes(content)
        connection = sqlite3.connect(f"file:{base / 'crumbkeeper.db'}?mode=ro", uri=True)
        try:
            rows = connection.execute("SELECT data FROM recipes ORDER BY updated_at DESC").fetchall()
        finally:
            connection.close()
        if not rows:
            raise RuntimeError("No saved recipes in this app. No empty backup created.")
        recipes = []
        photos = {}
        used_ids = set()
        for (raw,) in rows:
            recipe = json.loads(raw)
            rid = recipe.get("id", "")
            if not SAFE_NAME.fullmatch(rid) or rid in used_ids:
                raise RuntimeError("Bad or duplicate recipe ID in source database.")
            used_ids.add(rid)
            if not recipe.get("title"):
                raise RuntimeError(f"Recipe {rid} has no title; review the source database.")
            photo = recipe.get("coverImage")
            if photo and photo != "null":
                mark = "/files/recipe-images/"
                if mark not in photo:
                    raise RuntimeError(f"Unexpected image location in {rid}: {photo}")
                name = photo.split(mark, 1)[1]
                if not SAFE_BASENAME.fullmatch(name):
                    raise RuntimeError("Unsafe image filename.")
                payload = adb_read(adb, package, "files/recipe-images/" + name)
                if len(payload) > 12 * 1024 * 1024:
                    raise RuntimeError("Photo exceeds Crumbkeeper's 12 MiB import limit.")
                image_path = f"images/{rid}.cover"
                photos[image_path] = payload
                recipe["coverImage"] = image_path
            else:
                recipe["coverImage"] = None
            recipes.append(recipe)
        if len(recipes) > 3000:
            raise RuntimeError("Recipe count exceeds import limit.")
        output.parent.mkdir(parents=True, exist_ok=True)
        paths = [f"recipes/{r['id']}.json" for r in recipes]
        with zipfile.ZipFile(output, mode="w", compression=zipfile.ZIP_DEFLATED) as z:
            z.writestr("manifest.json", json.dumps({
                "format": "crumbkeeper.book", "schemaVersion": 1, "recipes": paths
            }, indent=2))
            for path, recipe in zip(paths, recipes):
                z.writestr(path, json.dumps(recipe, indent=2, ensure_ascii=False))
            for name, content in photos.items():
                z.writestr(name, content)
        if output.stat().st_size <= 0:
            raise RuntimeError("Backup file is empty.")
        print(f"Backup created: {output.resolve()}")
        print(f"Recipes: {len(recipes)}; photos: {len(photos)}")
        print("Next: copy the .crumbbook file to the tablet; open Crumbkeeper v0.1.2 > Import File.")
        print("Do not uninstall the original app until you have verified the import.")


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--package", required=True, choices=DEBUG_PACKAGES,
                   help="Original installed Crumbkeeper package")
    p.add_argument("--output", required=True, type=pathlib.Path,
                   help="Destination .crumbbook backup file on your computer")
    p.add_argument("--adb", default="adb", help="Path to adb from Android platform-tools")
    a = p.parse_args()
    if a.output.suffix != ".crumbbook":
        p.error("The output file must end with .crumbbook")
    try:
        export(a.adb, a.package, a.output)
    except (OSError, RuntimeError, sqlite3.Error, json.JSONDecodeError, subprocess.SubprocessError) as e:
        print(f"Export failed: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())