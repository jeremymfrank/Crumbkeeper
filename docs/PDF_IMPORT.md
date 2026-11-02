# v0.1.4-dev — PDF import and binary-file guard

The v0.1.3 importer treated all non-ZIP bytes as UTF-8 text, resulting in 3,335 unplaced lines of PDF object data in one user's Android screenshot (Skia/Chrome "Best Fish Taco Recipe").

## Changes

- Detect PDF magic header (%PDF-) and ZIP signatures; reject malformed UTF-8 and binary files rather than opening an unusable draft.
- Use Android PdfRenderer and a **bundled on-device ML Kit Latin OCR model** to read pages offline. Also handles image-only/scanned PDFs when legible.
- Attempt to isolate Ingredients and Instructions/Directions, remove obvious website extras, and join numbered continuation lines.
- Preserve the full recognized text in the unsaved draft under **Show Original Text**. The original PDF file itself is not attached to the recipe.
- Timer mentions detected by the existing parser remain unapproved suggestions, not automatic timers.
- Show a progress dialog while scanning instead of blocking with binary data or showing giant unplaced lists.
- Conservative limits: 25 MB input, 40 pages, 180,000 recognized characters. One bitmap held at a time.
- Import remains review-first; existing saved recipes cannot be overwritten inadvertently.
- v0.1.4-dev debug app package: com.crumbkeeper.app.dev4, installed separately from prior debug builds to protect their data.

## Caveats

OCR can confuse 1/2 vs 1/4, numbers, units and oven temperatures. Users must compare with the PDF before saving. Printed web pages with complex columns, many advertisements or unusual layouts may still be difficult to parse. The importer only recognizes Latin-script text in this version.

The actual PDF was not uploaded; only a screenshot of its failed binary-text import was provided. Android OCR accuracy on that exact document remains **untested**.

## Verification

- Automated tests cover PDF signature recognition, malformed/binary rejection, cleaning of a synthetic cluttered printed recipe, and ordinary Markdown regression.
- Build and existing unit tests must pass GitHub Actions before distributing APK.
- Device tests pending: real browser PDF, scanned PDF in airplane mode, failures do not change saved recipes, preexisting timers/sharing/backup unchanged.
