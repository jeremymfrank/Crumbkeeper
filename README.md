# Crumbkeeper

**A distraction-free, offline-first Android cookbook built for actual cooking.**

Crumbkeeper turns a tablet or phone into a dedicated kitchen workspace: ingredients, instructions, photos, notes, and multiple independent timers all remain in a **single full-screen recipe view**.

## Core principles

- **Dark by default.** Comfortable, high-contrast presentation for kitchen use.
- **One cooking screen.** No routine page switching once cooking starts; the full recipe and timers remain together.
- **Timers you can trust.** Multiple simultaneous timers; durations lock once started and destructive actions are protected.
- **Tablet-first, phone-ready.** Adaptive portrait and landscape layouts.
- **Offline and yours.** Local recipe storage, editing, images, and backups without accounts or cloud dependence.
- **Share directly.** Portable `.crumbrecipe` packages sent through Android's native share sheet, preferably Quick Share when available.

## Project status

**Android prototype available.** The debug test builds run on Android; no production release or stable-signing/update channel yet.

## Project documentation

- **[Project Specification](docs/PROJECT_SPEC.md)** — agreed behavior, proposed architecture, data format, timer safety contract, roadmap, acceptance criteria, deferred ideas, and open decisions.
- **[ChatGPT Import Text Format](docs/CHATGPT_IMPORT.md)** — paste-ready Markdown with optional exact timer directives.
- **[Backup & Sharing Workflow](docs/RECIPE_PORTABILITY.md)** — local backups, individual recipe sharing and recovery safeguards.
- **[Hybrid Recipe Builder](docs/RECIPE_BUILDER.md)** — create from scratch, import recipes copied from ChatGPT, offline parsing and preview, step timers, and safe manual editing.
- **[Branding & Android Icon](branding/README.md)** — approved identity, vector launcher icon, Android adaptive resources, and dark-theme color tokens.

## Implementation direction

Proposed: native Kotlin + Jetpack Compose, Room/SQLite for structured local data, and bundled images in portable recipe files. The specification is the source of truth; implementation choices not yet confirmed remain labeled as proposals.

## Guiding principle

> Once cooking begins, the recipe becomes the entire workspace.

## Android prototype source

The first native Android prototype source is now in `app/` (**v0.1.0-dev**, GitHub debug APK **build PASS**, on-device testing **not yet run**). It includes a dark-theme local cookbook, hybrid paste-and-edit flow, in-place Cooking Mode, and protected multiple timers. Read [First Android Build](docs/ANDROID_FIRST_BUILD.md) for prerequisites, build instructions, test status, and known limitations.

**Do not depend on this prototype for locked-screen alarms:** Android notification/alarm integration is not implemented yet.

First successful CI build: [Run #2](https://github.com/jeremymfrank/Crumbkeeper/actions/runs/41523550547) — download the `Crumbkeeper-v0.1.0-debug` artifact from the workflow. This is a debug testing build, **not a production release**.

### v0.1.3-dev — Cook & Share first

Saved recipe cards prioritize **Cook** and **Share**. Editing has moved to a less prominent **More** menu; the original recipe needs separate confirmations to open for editing and to replace on save. [Read the saved-recipe protection design](docs/RECIPE_PROTECTION.md). Existing debug installations remain separate until signing is stabilized.
