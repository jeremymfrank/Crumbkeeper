# Crumbkeeper — ChatGPT Recipe Text v1

Crumbkeeper accepts ordinary pasted recipes and Markdown **offline**. For precise copying from ChatGPT, use this readable convention:

```text
CRUMBKEEPER RECIPE v1
# Smashed Golden Potatoes
Serves: 4

## Ingredients
- 1.5 lb golden potatoes
- 2 tbsp olive oil
- Salt and pepper to taste

## Instructions
1. Wash and boil the potatoes.
2. Reduce heat and simmer for 20 minutes.
   Timer: Simmer | 1200s
3. Smash the potatoes, add oil and seasonings, and bake at 415°F for 20 minutes.
   Timer: Bake | 1200s

## Notes
A pinch of baking soda may improve the crispiness.
```

## Rules

- Line 1 (`CRUMBKEEPER RECIPE v1`) is optional for ordinary recipe parsing, but recommended for an explicit exchange format.
- Title on a `#` heading. `Serves:` or `Servings:` is optional.
- Ingredients as individual lines under `## Ingredients`.
- Numbered instructions under `## Instructions`; `## Directions` also works.
- To encode an exact timer: `Timer: LABEL | DURATIONs` on the line immediately following its step, where duration is integer **seconds**. `Timer: Simmer | 20 min` also works.
- Explicit timers are imported as ready **presets**. The first explicit Timer line replaces durations inferred from the same step; additional lines add independent presets. Countdowns wait for Start.
- Without explicit timer lines, recognizable durations create editable timer presets. Every added timer is available in Cooking Mode; no enable or approval checkbox is required.
- Optional notes under `## Notes`.
- Do not invent missing ingredients or times. The editor preserves the pasted original text.
- No account, API key, special plugin, or active internet is required to import this text.

## Recommended ChatGPT request

> Give me the complete finished recipe in **Crumbkeeper Recipe Text v1**, as one copyable plain-text block with its title, servings, ingredients, numbered steps, any step-specific timers and notes. Do not add commentary outside the block.

The human-readable format is intentionally not JSON. The app's **Paste Recipe** button accepts clipboard text; **Import File** accepts a saved `.txt` or `.md` file.

## Data portability

- `.crumbrecipe` is a versioned ZIP with `manifest.json`, `recipe.json`, and any referenced `images/...` files. Use **Share** on the recipe card to send it through Android's normal share sheet (Quick Share when available).
- `.crumbbook` is a versioned ZIP containing `manifest.json`, multiple `recipes/<id>.json`, and their photos. Use **Back Up Cookbook** and save to a user-selected safe location.
- Both preserve recipe IDs, ingredients, steps, timer presets, original text, notes and attached cover images; they intentionally do **not** carry active timer countdowns.
- **Import File** validates an archive and displays a preview. Importing missing recipes is the default; existing recipe IDs are never overwritten. **Keep Both** imports all entries under new IDs.
- Recipe text and archives are **different** formats: Markdown is for easy copy/paste; archives are for lossless sharing and backups.

## Upgrade safety

Debug APK signing keys from different GitHub runners can differ. A differently signed APK may fail to update an existing package, and uninstalling deletes local data. The v0.1.2-dev backup test build therefore uses package ID `com.crumbkeeper.app.dev2` and installs alongside the earlier packages. **The earlier installations retain their data and do not automatically synchronize with this new one. Do not uninstall them.**

Before future app reinstall/update/migration, export a `.crumbbook` backup from the version that holds the recipes, verify it by importing it into a separate test installation, and keep a second copy outside the tablet. To enable safe conventional in-place updates, we must also configure a **persistent private app-signing key** rather than regenerating a key for each CI runner.

## Current limits

- Only the cover-photo field is implemented in the current Android model; step-specific media will follow.
- Import limit: 128 MiB uncompressed data, 12 MiB per photo, and 3000 recipes per package.
- No end-to-end device test yet for export, Quick Share, import and full restore.
- No conversion from legacy private app databases is implemented.
