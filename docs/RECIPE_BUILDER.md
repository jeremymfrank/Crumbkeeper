# Crumbkeeper — Hybrid Recipe Builder and Import Workflow

**Design version:** 0.1  
**Date:** 2026-11-02  
**Status:** Approved product direction, proposed interaction and parser design; not implemented

## Core concept

The recipe editor is **hybrid**: a user can type a recipe manually, paste an entire recipe from ChatGPT or another source, or import a portable `.crumbrecipe` file. All paths lead to the **same editable, structured recipe model**. No online service, account, or ChatGPT API integration is necessary.

**Golden rule:** Never make saving a recipe depend on an AI service, a special prompt, or perfect parser output. Original text is always recoverable; ambiguous content is never silently invented or discarded.

The recipe builder is separate from Cooking Mode. Cooking Mode remains a single uninterrupted recipe workspace.

## Primary user journeys

### A. Copy a recipe from ChatGPT (high priority)

1. Ask ChatGPT for a recipe, discuss substitutions, cooking times and measurements, and settle on a final recipe.
2. Copy the final response (ordinary text or Markdown; no special formatting required).
3. Open Crumbkeeper > **New Recipe** > **Paste Recipe**, and paste from Android's clipboard.
4. Crumbkeeper performs a **local, offline** parse into recipe title, yield, ingredients/groups, steps, timers, temperatures, images references/notes, and metadata where explicitly present.
5. A **Review Import** view shows the parsed recipe with editable fields, source text available for side-by-side comparison, and clear **Needs Review** flags.
6. User fixes anything ambiguous, reviews editable timer presets, optionally attaches images, then chooses **Save Recipe** or **Save as New** when editing a known recipe.
7. The recipe is ready to launch in the full-screen Cooking Mode.

**Optional enhancement:** When the source app exposes a native Android share-text action, Crumbkeeper may register as a receiver for shared text, opening the same Review Import flow. This must be verified with real ChatGPT Android client behavior; clipboard paste is the always-available baseline.

### B. Create from scratch

- **New Recipe** opens a blank editor immediately; a title can be supplied later.
- Enter a title, optional description, servings/yield, ingredients, steps, timer presets, photos, notes and categories in any comfortable order.
- Save the working draft automatically; do not block entry behind required fields.
- For an ingredient, accept either a single flexible phrase or structured amount/unit/name/preparation fields. The user must always be able to override parser results.
- Every instruction is a reorderable step block with optional timers, images and notes.
- **Preview Cooking Mode** must be available without silently publishing or overwriting changes.

### C. Import from a file

- Open a native `.crumbrecipe` through Android's file picker or share/intents.
- Validate the archive and its versioned JSON data plus any images in offline local storage.
- Show the same Review Import screen before committing.
- Handle duplicate recipe IDs explicitly: **Update existing**, **Keep both**, or **Cancel**; never silently replace local edits.
- This is distinct from importing clipboard text; an archive can preserve exact timer presets and images without inference.

## Editor layout

**Tablet:** one editing screen with a flexible two-column layout: ingredients and recipe details on one side, instruction blocks and attached timers on the other. A bottom/side region can show notes, images and import-review warnings. Never require navigation to another page for ordinary field edits.

**Phone:** the same data and controls reflow vertically. Scrolling is permitted. Make fields and drag handles touch-friendly.

### Main recipe fields

- Title; optional description; servings/yield (store as structured value when possible and preserve literal text).
- Ingredient groups (e.g., Dough, Filling, Glaze) and ordered ingredients.
- Each ingredient: original source line, quantity (including fraction/range when supported), unit, item/name, preparation notes, optional flag; a fallback free-text ingredient is always valid.
- Ordered instruction/step blocks. Each block includes verbatim instruction text, optional step-specific photo, notes, and **zero or more timer presets**.
- Recipe-wide notes and private personal notes, cover/step photos, tags/categories, source/attribution information.
- Created/updated timestamps and stable identifiers.

### Editing interaction

- **Add Ingredient**, **Add Group**, **Add Step**, **Add Timer**, **Add Image**, **Add Note** are in-place controls, not separate app pages.
- Reorder ingredients, groups and steps with clear handles plus accessible move-up/down actions.
- Edit or remove a timer preset only in the Recipe Editor. Cooking Mode locks duration as soon as that timer instance starts.
- Changes autosave into a local draft. **Save Changes** means updating an existing recipe; **Save as New Recipe** explicitly creates a separate ID.
- **Undo** is desirable for destructive changes, with confirmation for deleting an entire step, group or image where practical.

## Paste parsing contract

### Supported input styles

1. Normal prose recipes, including ChatGPT responses.
2. Plain text with headings such as Ingredients, Directions, Instructions, Notes, Prep, Cook, Yield.
3. Markdown: `#` headings, emphasis, ordered/unordered lists, horizontal rules, and common checkboxes.
4. Multi-line ingredient groups and numbered instructions.
5. A native, versioned structured representation (optional future direct ChatGPT export), or an existing `.crumbrecipe` package via file import.

### Offline pipeline (deterministic and inspectable)

1. **Capture:** save immutable pasted source text in an import-draft record before parsing.
2. **Normalize:** line breaks, whitespace, Unicode fractions, bullets, numbering, Markdown markup; preserve the source exactly for reference.
3. **Classify:** identify likely title, servings, metadata, ingredient sections/groups, instruction steps, notes and recipe source.
4. **Extract ingredients:** recognize explicit numeric/fraction quantities and measurement units; preserve ambiguous or unsupported amounts verbatim.
5. **Extract steps:** preserve complete instruction text and sequence; do not combine distinct steps merely to reduce block count.
6. **Suggest timers:** identify explicit time durations in step instructions, associate the suggestion with the relevant step, preserve surrounding context, and never start timers automatically.
7. **Flag ambiguities:** for example, an unclear ingredient line, `20–25 minutes` range, an unassigned time, or words that could be either a preparation note or an ingredient.
8. **Review:** display structured fields and unresolved source spans side by side or via an expandable **Original Text** region.
9. **Commit only on confirmation:** after the user confirms import, write the structured recipe and optional images through an atomic local database operation.

### Important parsing rules

- **No hallucination:** never add missing flour, salt, temperatures, servings, or cooking stages simply because a recipe usually contains them.
- **No hidden loss:** unclassified lines remain in an **Unplaced text** section, visible and copyable, until resolved.
- **No mandatory AI:** the first parser should run locally via rule-based/structured extraction. Optional on-device enhanced parsing can be assessed later, but ordinary paste must always function offline.
- **No timer inference from metadata alone:** `Total time: 60 minutes` is recipe metadata, **not** an automatically inserted step timer.
- **Durations are suggestions, not commands:** `Let rise 45 minutes` may suggest `Rise — 45 min`, available when the recipe is saved, with no enable checkbox.
- **Ranges are ambiguous:** `Bake 20–25 minutes` should remain readable as written; allow choosing a preset duration rather than silently taking one end.
- **Conditional time cues:** `Bake until golden` has no numerical timer; do not manufacture one.
- **Temperatures are not timer durations:** `Bake at 415°F` belongs to the step, not a countdown.
- **Overlapping activities:** `Simmer 20 min while preheating oven` may contain one timer plus a non-timed parallel instruction; preserve both.
- **Multiple durations in one step:** present separate editable timer suggestions when explicit actions are associated with each.
- **Numeric precision:** accept familiar fractions (`1/2`, `½`, `1 1/2`), common unit abbreviations and `to taste`, while preserving the author's exact original text for difficult cases.
- **Ingredient ranges:** preserve `2–3 cloves` without treating it as precisely 2 cloves; defer automatic scaling if range semantics are unresolved.
- **Attribution:** a recipe pasted from ChatGPT must not automatically be falsely credited to ChatGPT as its original creator. Allow a source label and actual attribution where known.
- **Images:** clipboard text generally does not include its referenced image bytes. Keep any image descriptions/links visible as references; attach photographs separately unless real image data was shared.
- **Repeated import:** preview duplicates using stable IDs (for native files) and optional content fingerprints (for pasted text); never overwrite based solely on title similarity.

### User-visible review states

- **Ready**: extracted with no material ambiguity.
- **Review suggested**: reasonable parse but a nonfatal question such as quantity or timer range.
- **Unplaced**: content that did not fit a structured field; show the exact text.
- **Invalid / cannot import**: file validation failure, or unrecoverable schema issue. Ordinary prose without obvious structure is **not** invalid; keep it as draft text.

User can save a partially structured recipe as a draft without falsely implying that import successfully parsed every ingredient or step. The final cooking-ready save should call out unresolved items.

## Example: ChatGPT to Crumbkeeper

Pasted:

```text
Smashed Golden Potatoes
Serves 4

Ingredients
- 1.5 lb golden potatoes
- 2 tbsp olive oil
- Salt and pepper

Directions
1. Wash and boil the potatoes.
2. Reduce to a simmer for 20 minutes.
3. Smash potatoes, season, and bake at 415°F for 20 minutes.
Notes: A small pinch of baking soda may help crispiness.
```

Proposed review:

- Title: **Smashed Golden Potatoes**; yield: **4 servings**
- Ingredient entries: potatoes `1.5 lb`, olive oil `2 tbsp`, salt and pepper (free text)
- Step 1: wash and boil potatoes; no timer preset
- Step 2: simmer; **suggested timer: 20 min** (not running)
- Step 3: bake at **415°F**; **suggested timer: 20 min** (not running)
- Note: small pinch of baking soda may help crispiness
- The user can rename, adjust or remove either preset before saving. Added timers are immediately available in Cooking Mode and start only on request.

## Data model and implementation notes

- Parse input into a **staging model** distinct from saved recipes; no partially parsed data overwrites a stored recipe.
- Preserve a reference to the original pasted text in the import draft and optionally recipe provenance.
- Assign stable IDs to ingredients, step blocks, images and timers on successful commit. A native imported ID is preserved/handled according to duplicate policy.
- Preserve recipe editor versions separately from **CookingSession** and **ActiveTimer** instances; editing should not change an in-progress countdown.
- Timer presets store `durationSeconds`, `label`, and `stepId`; current deadline, pause status and alarm state live only in cooking session storage.
- Parse reasons, warnings and source span references are useful for diagnosing mistakes without exposing any data to a server.
- Schema and JSON interchange version are documented separately from the parsing implementation. Native file round-tripping should be lossless for supported fields.

## Acceptance tests

1. Paste an ordinary ChatGPT recipe containing Markdown heading, ingredient bullets, numbered directions, notes and two cooking durations: draft parses offline; both timer presets are available after saving without approval.
2. Paste a recipe with no title and vague amounts: raw text is retained, edit remains possible, no details invented.
3. Paste a range like `Bake 20–25 min at 415°F`: range is flagged; temperature remains part of the instruction.
4. Paste Unicode fractions and mixed quantities (`½ cup`, `1 1/2 tsp`), compare against preserved source.
5. Include an unrecognized sentence between ingredients and steps: show it under **Unplaced text**; no silent deletion.
6. Copy/share text from another Android app when supported: opens Review Import, and clipboard workflow remains functional without share integration.
7. Close the app mid-edit and reopen: unsaved draft and original text recover.
8. Edit an existing recipe and press **Save Changes**: same recipe ID; **Save as New** creates a new ID.
9. Attach a step image and multiple timer presets, export `.crumbrecipe`, import on another device, and verify identical content.
10. Repeated import and duplicate-ID cases never overwrite private notes without approval.
11. Imported timers cannot start automatically, and running Cooking Mode timers cannot be edited via the recipe builder.
12. All manual and paste workflows work in airplane mode on a large tablet and a phone.

## Explicitly deferred

- Automatic web scraping or ChatGPT account/API connection.
- Always-on cloud AI interpretation.
- Automatic image downloading from pasted URLs.
- OCR of handwritten cookbook pages (a promising future import method).
- One-tap remote synchronization between ChatGPT and Crumbkeeper without any user copy/share action.

---

**Decision:** Hybrid recipe editing, with **ChatGPT copy/paste as a first-class offline import path**, is the chosen direction. This document describes intended functionality, not completed implementation.
