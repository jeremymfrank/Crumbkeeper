# Crumbkeeper v0.1.3-dev — Saved-recipe protection

The default cookbook workflow is **Cook** and **Share**. Saved recipes should feel like completed cards, not unprotected drafts.

## Library

- Recipe cards display only **Cook** and **Share** as everyday actions.
- A small **⋮ More** menu contains **Edit a Copy** (safe option) and **Edit Original…**.
- Editing the original opens a warning dialog before any editable form is shown.
- The recipe remains unchanged during editing: the editor only saves an independent draft until an intentional save.

## Editor

- Existing recipes no longer have an easy-to-tap top **Save** button.
- The bottom action is **Review Changes…**, followed by a second **Replace Original** confirmation.
- **Save as New** creates an independent recipe with a new ID and new step/timer IDs.
- Leaving the editor keeps the draft for later without modifying the saved recipe.
- Restoring an editor draft correctly recognizes an existing saved ID and cannot silently bypass confirmation.
- The repository's `RecipeEditSafety` guard rejects an overwrite that lacks explicit final confirmation, even if a UI path calls it by mistake.

## Existing library and install safety

- **Never silently overwrite a saved recipe.**
- No recipe edits occur while cooking or sharing.
- No delete action is added to recipe cards.
- v0.1.3-dev uses app ID `com.crumbkeeper.app.dev3` for side-by-side debug installation. This avoids uninstalling or replacing the existing v0.1.2-dev test build because CI debug signing is still not stable.
- **Back up your v0.1.2-dev recipes** using Back Up Cookbook and import that `.crumbbook` into the new test version. Keep your original app installed.
- Longer-term: configure a stable signing key to support normal in-place updates.

## Acceptance checks

- [ ] Cook opens the unchanged recipe directly.
- [ ] Share starts Android sharing without editing.
- [ ] Edit is absent from main card action row; it appears only under More.
- [ ] Edit Original requires both an entry confirmation and a separate replacement confirmation.
- [ ] Save as New keeps the original untouched.
- [ ] Back from unsaved editing leaves saved original unchanged.
- [ ] Resume draft cannot bypass edit confirmation.
- [ ] Backup and restore still round-trip.
- [ ] Tablet / phone layout remains usable and timer behavior is unchanged.

Automated Kotlin tests cover duplicate IDs and overwrite authorization; device testing is required to confirm the new interaction flow.
## First verified APK (2026-11-02)

- **Automated build and JVM unit tests:** PASS, [GitHub Actions run #7](https://github.com/jeremymfrank/Crumbkeeper/actions/runs/41536861724), source commit `88227c30`.
- **Debug APK:** `Crumbkeeper_v0.1.3-dev.apk`, 9,881,790 bytes; SHA-256 `b58843489c97c9005338d7931b3f28ef735e0716d8b341ea767db1790e2528f7`.
- **On-device UI / backup restore testing:** INCOMPLETE. Please retain the existing app and an off-device cookbook backup until migration is checked.
