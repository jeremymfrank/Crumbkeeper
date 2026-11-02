# Crumbkeeper — Branding and Android Icon Assets

**Status:** Branding design chosen; native Android launcher vector resources are prepared but not yet device-tested.

## Approved master artwork

The owner-approved master is a **1254 × 1254 PNG** featuring a Gothic open grimoire, cooking pot, checklist and stopwatch, copper/antique gold lettering and purple circuitry. No fairy is present.

- Original approved image SHA-256: `3b184948f09311549b170acf751b92f195f9cbf09014ee746c216ef95e6f78dc`.
- **Do not replace, recolor, or simplify the master.** Use the original for prominent branding.
- **Asset status:** The unaltered PNG was preserved in the downloadable branding ZIP generated in the project chat. The full-resolution binary **has not been committed here**; it must be uploaded separately when binary GitHub upload is available. Do not mistake the vector launcher icon for the illustrated master.

## Editable Android launcher icon

- [Foreground SVG](icon/crumbkeeper-foreground.svg)
- [Background SVG](icon/crumbkeeper-background.svg)
- [Monochrome SVG](icon/crumbkeeper-monochrome.svg)
- `android/res/drawable/ic_launcher_foreground.xml`
- `android/res/drawable/ic_launcher_background.xml`
- `android/res/drawable/ic_launcher_monochrome.xml`
- `android/res/mipmap-anydpi-v26/ic_launcher.xml`
- `android/res/values/colors.xml`

The launcher motif is a deliberately simple, **vector** companion to the approved art: the same open Gothic grimoire, steam/cooking pot, checklist, timer, amber-metal frame and violet circuits. **No fairy; no wordmark inside the launcher icon.**

These resources are **copy-ready**, not wired to an Android app yet. When the Android module is created, copy the `android/res/` contents to `app/src/main/res/`, reference `@mipmap/ic_launcher` in the manifest, and test adaptive circular/squircle masks plus Android themed icon behavior on actual devices.

## Default dark theme palette

| Role | Hex |
| --- | --- |
| Base background | `#100C15` |
| Surface | `#1D1624` |
| Raised surface | `#292031` |
| Primary text | `#F1E6D4` |
| Secondary text | `#BDAFC0` |
| Violet magic/tech accent | `#B34BE4` |
| Copper accent | `#B88A53` |
| Antique gold | `#EBD1A7` |
| Success | `#83C5A5` |
| Warning | `#E5B875` |
| Danger | `#EE8F95` |

The app keeps **dark theme by default**, but lets users choose appearance and keep-awake preferences in its Settings menu outside Cooking Mode. Avoid decorative glare in recipe text and timer controls; accessibility/contrast takes precedence.

## Release checklist

- [x] Master approved and preserved outside the repository in the asset bundle.
- [x] Matching editable vector icon sources.
- [x] Adaptive icon XML with monochrome layer.
- [x] Color token XML and design documentation.
- [ ] Commit full-resolution master PNG into repository when binary upload is supported.
- [ ] Integrate into a real Android app module and verify manifest resource references.
- [ ] Test several icon masks, launcher sizes, theme variants and devices.
