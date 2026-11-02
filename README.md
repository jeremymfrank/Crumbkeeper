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

**Planning / specification stage.** No Android implementation, APK, or tested release is represented by this repository yet.

## Project documentation

- **[Project Specification](docs/PROJECT_SPEC.md)** — agreed behavior, proposed architecture, data format, timer safety contract, roadmap, acceptance criteria, deferred ideas, and open decisions.
- **[Hybrid Recipe Builder](docs/RECIPE_BUILDER.md)** — create from scratch, import recipes copied from ChatGPT, offline parsing and preview, step timers, and safe manual editing.
- **[Branding & Android Icon](branding/README.md)** — approved identity, vector launcher icon, Android adaptive resources, and dark-theme color tokens.

## Implementation direction

Proposed: native Kotlin + Jetpack Compose, Room/SQLite for structured local data, and bundled images in portable recipe files. The specification is the source of truth; implementation choices not yet confirmed remain labeled as proposals.

## Guiding principle

> Once cooking begins, the recipe becomes the entire workspace.
