# Crumbkeeper — Project Specification

**Document version:** 0.2  
**Date:** 2026-11-02  
**Status:** Product scope baseline; not an implementation report  
**Primary platform:** Android (tablets first, phones supported)

## 1. Vision

Crumbkeeper is an **offline-first, distraction-free home cooking application**. It stores a personal recipe library and presents recipes as a stable, full-screen kitchen workspace. Cooking, not browsing or social engagement, drives its design.

**Guiding principle:** Once cooking begins, the recipe becomes the entire workspace. Do not make the cook switch pages to see ingredients, instructions, notes, images, or timers.

### Design priorities (highest first)

1. Reliability and prevention of accidental kitchen mistakes.
2. One-screen, readable, low-distraction cooking.
3. Complete offline functionality and local ownership of data.
4. Straightforward recipe creation and long-term preservation.
5. Simple, direct device-to-device sharing.

## 2. Firm product requirements

These requirements reflect the agreed project direction.

- **Dark theme by default.** Appearance and behavior preferences—including optional themes—are configured in a dedicated **Settings / Configuration** menu outside Cooking Mode. Do not place theme/settings navigation in the cooking workspace.
- **Tablet-first, phone-compatible.** Use available space in landscape and portrait rather than forcing a phone-sized interface onto a large tablet.
- **Single-screen Cooking Mode.** Ingredients, directions, step images, personal notes, and all recipe timers are visible or accessible by scrolling within the same uninterrupted workspace. No tabs, subpages, wizard-style steps, or routine navigation away from the recipe while cooking.
- **Persistent timer visibility.** Active countdowns must remain accessible when the user scrolls the recipe, including on smaller phones. Timers must never require opening a separate screen.
- **Multiple independent embedded timers per recipe, including multiple timers on one step if needed.** Timers have names and recipe-defined durations.
- **Timer durations lock when started.** No +/− adjustments or duration edits while running. Pause/resume is allowed; cancel/reset is deliberate and protected against accidental activation.
- **No automatic timer start from merely opening a recipe.**
- **Photos and notes.** Recipes support a cover image, optional step images, and user notes.
- **Entirely offline core.** Creating, editing, browsing, searching, cooking, timing, and importing/exporting recipes require no login, subscription, cloud, or active network connection.
- **Local ownership.** Recipes can be exported, transferred, backed up, and restored.
- **In-person sharing.** Use Android's native sharing system, with Quick Share as the preferred nearby-device experience and ordinary file transfer as fallback.
- **Explicit end of cooking.** Returning to recipe management is a deliberate action; accidental exits must not silently discard the current session.

## 3. Core experiences

### 3.1 Recipe Library (outside Cooking Mode)

- Display saved recipes with cover image, title, and useful metadata.
- Search locally by title and ingredients; support favorites/categories as the library grows.
- Open a recipe for preview/editing or deliberately enter Cooking Mode.
- Provide recipe import/export, share, and backup entry points.
- Keep management controls out of Cooking Mode.

### 3.2 Recipe Editor (outside Cooking Mode)

- Create and edit recipe title, description, servings/yield, ingredients with quantities and units, ordered steps, timer presets attached to steps, notes, and photos.
- Autosave drafts locally so a brief app interruption does not destroy edits.
- Treat ingredients and steps as structured data, not only a single text blob, to enable future scaling and reliable timers.
- Allow omission of optional fields: a straightforward recipe must be easy to enter.
- Leave room for future revisions/history without requiring it for the first usable prototype.

### 3.3 Cooking Mode (the primary experience)

- Occupies the usable screen with a dark, high-contrast presentation.
- On large tablets, use a spacious multi-column layout where suitable; on phones, reflow into a readable single-column layout.
- Keep all relevant recipe content in one workspace. Scrolling is acceptable; switching app pages is not.
- Put active timers in a persistent region or compact pinned strip when space is constrained.
- Offer very large, clearly labeled touch targets and readable typography at arm's length.
- Support optional ingredient/step checkmarks. These belong to the cooking session and **must not change the saved recipe**.
- Show timer completion clearly in place, with system-appropriate audible/notification alerting; do not open an unrelated screen.
- Keep editing tools, share menus, general settings, and library navigation out of the ordinary cooking flow.
- A deliberate **Finish Cooking** control ends the session and returns to the library. Avoid accidental activation.
- **Keep screen awake in Cooking Mode by default, where supported by Android.** Add an enabled-by-default **Keep screen awake while cooking** toggle in the Settings / Configuration menu. Apply it only while Cooking Mode is visible; release the request on exit. Respect device lock, power-management restrictions, and platform behavior; do not imply the app can override them.

### 3.4 Settings / Configuration menu (outside Cooking Mode)

- Provide a dedicated configuration destination reachable from the recipe library/editor, not from routine cooking actions.
- Default to **dark theme**; allow alternate theme selection without restarting or losing saved work.
- Provide a **Keep screen awake while cooking** preference, enabled by default, with a concise explanation of battery use and device limits.
- Reserve space for accessibility preferences (font scaling, contrast), notification/sound controls consistent with Android permissions, and import/export preferences as these are implemented.
- All selections persist locally and remain available offline. Settings changes should not alter recipe content or active timer deadlines.

## 4. Timer contract

Timer **presets** are part of recipes; timer **instances and countdown state** belong to cooking sessions.

### 4.1 States and controls

| State | Display and allowed actions |
| --- | --- |
| Ready | Show label and preset duration; Start |
| Running | Show live remaining time; Pause; protected Cancel |
| Paused | Show remaining time; Resume; protected Cancel |
| Completed | Clear visual alert; Dismiss; Restart from original preset |
| Cancelled | Return to Ready using the original preset duration |

- Never modify a running timer's preset duration through single-tap changes, swipes, or accidental edits.
- Pausing must retain the remaining duration; resuming must not restart the countdown.
- Cancel/reset must have a confirmation or comparably strong accidental-touch safeguard.
- Each timer is independent. One ending must not affect another.
- The app must persist enough session state to recover correctly after process death.
- Measure elapsed time using Android-appropriate monotonic time while the device remains booted; use a saved wall-clock deadline and explicit recovery policy for reboots, taking clock changes into account.
- The timer engine must not depend on a composable screen staying alive.
- Background alerts, screen lock, notification permissions, battery optimization, Android alarm limitations, and reboot recovery **require on-device testing**. Do not promise exact alarms on devices where Android does not permit them.
- If a deadline has passed when the app resumes, display an unmistakable expired/completed state instead of pretending the timer is still counting.
- Device shutdown cannot deliver an alarm while the device is powered off.

### 4.2 Timer safety acceptance examples

1. Start two recipe timers; both run independently while scrolling through directions.
2. Pause one; the other continues.
3. Rotate the tablet; neither timer restarts or changes duration.
4. Leave the app or lock the screen; timer deadlines survive and completion alerts work within Android's permissions.
5. Force-stop/process-kill/restart tests distinguish recoverable process death from OS restrictions; no fabricated success.
6. Attempt an accidental tap or swipe on a running duration; preset and deadline do not change.
7. Attempt cancel; the timer cannot be discarded without intentional confirmation.

## 5. Storage and data ownership

### 5.1 Internal storage

**Proposed implementation:** Native Kotlin + Jetpack Compose for UI, Room/SQLite for structured recipe/session data, and app-managed local files for images. No server is needed for the core app.

Conceptual entities:

- `Recipe` — ID, title, description, servings/yield, metadata, timestamps.
- `Ingredient` — recipe ID, sort order, amount, unit, ingredient text.
- `Step` — recipe ID, sort order, instruction content.
- `TimerPreset` — stable ID, associated step, label, duration in seconds.
- `RecipeImage` — cover/step role, locally stored file reference, caption.
- `RecipeNote` — general or step-specific notes.
- `CookingSession` and `ActiveTimer` — ephemeral checklist, current timer states, deadlines and recovery metadata.

Use schema migrations rather than destructive database resets. Images should remain available offline.

### 5.2 Portable recipe: `.crumbrecipe`

**Proposed specification:** A versioned ZIP archive containing a `recipe.json` file plus bundled image assets. A receiving device must not need internet access to open it.

- Recipe data includes unique IDs, ordered ingredients and steps, timer **presets**, images with relative paths, metadata, and schema version.
- **Do not export live running countdown state** as part of a recipe transfer.
- Personal notes should be optionally includable at export, with a clear preview before sharing.
- Preserve source/author attribution when supplied; importing should permit a personal editable copy.
- Validate version, package sizes, extracted file paths, content types, and media before import. Reject unsafe/corrupt archives without affecting the existing library.
- If the recipient already has the recipe ID, offer an explicit keep-both/update choice; never silently overwrite local changes.
- Keep the documented JSON structure forward-compatible and migrate old versions where feasible.

A complete schema, MIME type, maximum archive sizes, and migration rules are to be finalized during implementation before the first public recipe package is published.

### 5.3 Library backup: `.crumbbook`

**Planned:** A separate portable whole-library backup (recipes, images, notes, tags, and compatible metadata). Backups must be independently restorable without a Crumbkeeper account. Distinguish **individual sharing** from **whole-library backup** so one shared recipe cannot accidentally expose the complete collection.

## 6. Nearby sharing and importing

1. User selects one or more recipes in the library and taps Share.
2. App exports a valid portable recipe package to temporary local storage.
3. App invokes Android's normal share sheet with an appropriately permissioned content URI.
4. **Quick Share** is the preferred nearby method where available; do not depend on it exclusively.
5. Recipient accepts the transfer and opens the package in Crumbkeeper or imports it from a file picker.
6. Crumbkeeper displays an import preview and requires user confirmation before adding the recipe.

Use Android-supported content URI grants / FileProvider or equivalent; avoid raw `file://` paths. No account, hosted recipe service, LAN server, or custom wireless protocol for the first release.

## 7. Nonfunctional requirements

- **Offline by design:** core screens never block on network availability; imported images are copied to local storage.
- **Privacy:** no required sign-in, telemetry, advertising, or cloud upload for core operation.
- **Reliability:** no recipe/data loss on normal app upgrades, activity recreation, rotation, or brief process interruption.
- **Usability:** dark theme by default, legible contrast, large controls, screen reader labels, adjustable text sizing, visible timer states beyond color alone.
- **Performance:** responsive scrolling and image loading even with a sizable recipe library; scale/downsample large images for display while preserving original files where feasible.
- **Accessibility:** don't require gestures to finish essential actions; use explicit controls.
- **Battery:** screen-awake behavior should be intentional and clearly indicated; foreground/notification behavior must comply with supported Android versions.
- **Testing:** exercise tablet landscape, tablet portrait, phone portrait, different resolutions/font scales, multiple simultaneous timers, backgrounding, import/export, and data migration.

## 8. Planned implementation phases

These are proposed sequencing milestones, not existing releases.

| Milestone | Deliverable | Exit criterion |
| --- | --- | --- |
| v0.1 — Foundation | Android project, dark theme, local recipe storage, editor, images, adaptive tablet/phone layout | Create, edit, close, reopen recipe entirely offline |
| v0.2 — Cooking Mode | Single-screen full-recipe layout, persistent timer region, independent safe timer engine, checkmarks | Complete a recipe without navigating to another screen; multiple timers survive rotation and interruption |
| v0.3 — Library polish | Search, favorites/categories, note handling, ingredient scaling if stable, accessibility polish | Library remains usable with realistic recipe sets |
| v0.4 — Sharing and recovery | `.crumbrecipe` transfer/import, conflict handling, `.crumbbook` backup/restore | Airplane-mode transfer via device sharing where supported; round-trip recipe and library recovery |
| v1.0 — Release | Device compatibility, timer/notification/reboot test matrix, migrations, signing/distribution | Pass critical workflow and data-loss acceptance tests |

The exact minimum Android version, dependencies, install method, and version numbering are implementation decisions to confirm before coding.

## 9. Version 1 acceptance criteria

- [ ] Create, edit, and save a recipe entirely offline.
- [ ] Attach recipe photos and personal notes; display them offline.
- [ ] Open a recipe in a dark, distraction-free cooking workspace on tablet and phone.
- [ ] See all recipe content and access all timers without changing app pages.
- [ ] Run multiple independent step-linked timers; keep their durations locked after starting.
- [ ] Prevent accidental cancellation or recipe exit.
- [ ] Recover correct countdown/expired state after app process restart and test background alerts within Android's permissions.
- [ ] Keep session checkmarks separate from permanent recipe data.
- [ ] Export, transfer through Android sharing, preview, and import a recipe with images and timer presets.
- [ ] Back up and restore the library without an account.
- [ ] Preserve stored recipes across application upgrades and database migrations.
- [ ] Use the application without network access or cloud infrastructure.

## 10. Intentionally out of initial scope

- Cloud accounts, cloud syncing, public social feeds, ads, subscriptions.
- Custom wireless transfer protocol and dedicated sharing server.
- Online recipe discovery, web scraping, AI generation.
- Meal planning, shopping cart integrations, pantry inventories.
- Voice navigation / hands-free commands.
- Simultaneous multi-recipe workspaces, unless a later user test justifies the complexity.
- Guided cooking as a **separate screen**. A future in-place focus/highlight style may be considered only if it preserves the continuous workspace.

## 11. Open decisions (not requirements yet)

1. Exact Android minimum SDK and supported device/version test matrix.
2. Final versioned `.crumbrecipe` JSON schema and import limits.
3. Detailed recipe-building/editing workflow (to be designed before implementation).
4. Optional recipe revision history and ingredient scaling release timing.
5. Branding/icon, additional theme designs, typography, and release distribution.
6. Project license.

## 12. Scope-change discipline

Before adding a feature, ask:

- Does it improve actual cooking without distracting the cook?
- Does it still work offline?
- Does it preserve data ownership and portability?
- Could an accidental touch interfere with a critical cooking action?
- Does it make Cooking Mode leave its single continuous screen?

If it fails those tests, it should be deferred, redesigned, or explicitly approved as an exception.

---

This document captures product intent and proposed implementation strategy. It does **not** claim code, tests, APKs, or completed releases exist. Update it as decisions are made; keep implementation status and test results separate from product requirements.
