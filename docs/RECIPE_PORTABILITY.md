# Crumbkeeper — Backup, Sharing and Text Import (v0.1.2-dev)

**Target:** Android v0.1.2-dev, first build for backup/sharing workflows.

## Three user operations

1. **Back Up Cookbook:** from Recipe Library, write a complete `.crumbbook` ZIP to a user-selected location through Android's document picker. All saved recipes, notes, timer presets, and available cover images are included.
2. **Share:** from a recipe card, package that single recipe as `.crumbrecipe` and invoke Android's share sheet. Quick Share is preferred for nearby Android-to-Android transfers. The sender keeps their recipe.
3. **Paste Recipe / Import File:** paste ordinary ChatGPT Markdown, import recommended Crumbkeeper Recipe Text v1, open a text document (`.txt`/`.md`), or select a `.crumbrecipe`/`.crumbbook` archive. The same editor supports review and corrections.

## Restore safety

A ZIP archive is fully validated in a staging step prior to any database writes: version, filename paths, ID uniqueness, required entries, file sizes and whole-archive byte limits. The user sees a confirmation dialog with recipe count and duplicate count. The normal import **skips existing IDs** and saves missing recipes in a single SQLite transaction. **Keep Both** copies all recipes using new stable IDs; existing recipes are not overwritten. Cover images are copied into the receiving app's private image directory.

Backup files should be kept off-device as well as on-device. A recipe list that uses app-private paths is not a valid backup without its images.

## Existing installations and signatures

Previous prototypes used `com.crumbkeeper.app` (v0.1.0-dev) and `com.crumbkeeper.app.dev` (v0.1.1-dev). Their signing keys were CI debug keys and are not available for reliable future in-place updates. v0.1.2-dev uses `com.crumbkeeper.app.dev2` to **avoid overwriting or uninstalling** previous installations. Recipes saved in older builds remain inside those older packages; this new version cannot directly read them. Keep the old apps installed until a specific migration path is implemented or data has been safely reconstructed.

A persistent signing key in secure CI secrets is a release prerequisite. **Never commit a production private signing key to the repository.**

## Acceptance tests

- [ ] Export two recipes (one with a photo, one with two timers) to a `.crumbbook`; restore on another app install.
- [ ] Confirm recipe title, ingredients, instruction text, notes, timer presets and cover photo all survive.
- [ ] Share one recipe via Quick Share; recipient imports with no network service.
- [ ] Import a standard ChatGPT Recipe Text v1 block; exact `Timer:` lines become approved timer presets.
- [ ] Import a normal ChatGPT Markdown recipe without timer directives; time mentions become unapproved suggestions.
- [ ] Import the same archive twice; default import skips existing entries.
- [ ] Explicitly import **Keep Both**; originals remain intact.
- [ ] Cancel restore at preview; no database or image files change.
- [ ] Import invalid/unsafe archive; failure does not delete previous recipes.
- [ ] Verify backup is saved to an external location and survives uninstall of this *test* app (do not uninstall the current original apps).
- [ ] Confirm Cooking Mode and its simultaneous timers still work after restore.

Code and CI results must be verified separately from these on-device acceptance tests. No production release is implied.
