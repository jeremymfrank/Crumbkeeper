# Crumbkeeper v0.1.5-testers — Deletion and recovery

## Recipe library

- Default recipe card actions are still **Cook** and **Share**.
- **⋮ More → Move to Trash…** is the only way to remove a recipe from the library.
- Confirmation asks users to choose **Move to Trash** or **Keep Recipe**.
- Recipes in Trash are not available to cook or share, and they are **not exported in regular cookbook backups**. Users who need them in a backup must restore them first.
- **Settings → Trash** shows the removed recipes. **Restore** puts a recipe back under its existing ID, preserving its instructions, notes, timer presets and cover photo.
- **Delete Forever** in Trash requires another explicit confirmation, and can only delete a trashed recipe.
- There is no automatic trash expiration. Trash survives app restarts; users decide when to permanently delete.
- Existing recipes are migrated additively using SQLite schema v1→v2 with `deleted_at INTEGER NOT NULL DEFAULT 0`; old recipes remain active.
- Imports check all IDs, including those in Trash, so a ZIP import cannot overwrite hidden recipes.

## Tester distribution

This version also includes the bundled offline PDF OCR importer from v0.1.4.
The Android debug build is a **public prerelease for volunteer testers**, not a production-ready app. It uses `com.crumbkeeper.app.dev5`, separate from previous debug test packages. Old installs and local recipes are not changed.
No background/locked-screen timer alarm scheduling or stable production signing key is provided in this build.

## Required manual testing

1. Make two recipes, back up the cookbook, and move one to Trash.
2. Confirm only the remaining recipe is shown in the library and exported in a fresh cookbook backup.
3. Restore from Settings → Trash and verify ingredients, timers and cover photo still exist.
4. Verify pressing **Cancel/Keep Recipe** preserves the recipe.
5. Confirm **Delete Forever** appears only in Trash and does nothing without final confirmation.
6. Try re-importing a trashed recipe from a backup; the original must not be overwritten.
7. Test PDF printing import and OCR, recipe share, and independent foreground timers.
8. Test a fresh install offline; note the PDF OCR model is bundled.

CI unit tests check the explicit confirmation guard; true schema migration and UI behavior require device tests.
