# Recipe photos and cover framing

Each recipe has one optional cover image. The library shows a portrait thumbnail or an illustrated book cover. Phones use one card column; larger screens fit additional columns. Titles and recipe text remain readable with Android font scaling.

Add or replace a photo in the Recipe Builder. Imported files are copied into app-private storage without changing their bytes. The photo must be supported by the device and under 12 MB, matching the sharing/backup limit. Import errors leave the recipe's previous photo intact.

The framing preview uses the same portrait aspect ratio as the library card. Horizontal position, vertical position and zoom affect only the displayed thumbnail. Choose Use Framing to update the recipe draft; Cancel leaves its previous framing unchanged. Save the recipe normally to commit the change. Reset Framing centers the original without zoom.

Tap a card photograph or choose View Full Photo in the editor to see the complete upright image. Cooking Mode fits the complete photo within its fixed-height image region. The emblem in About also uses fit-to-frame rendering, preserving its lettering.

JPEG camera orientation and reflected orientations are corrected during decoding, including for photos already saved by older versions. Large images are sampled off the main thread and decoded bitmaps use a bounded cache. The decoder never rewrites the original.

Normalized framing coordinates travel with `.crumbrecipe` and `.crumbbook` exports. Both formats retain the complete original photo. Files from older versions default to centered framing, and older apps can ignore the new framing field.

Timers are separate from photo rendering. Image loading reserves its layout space, and timer controls stay in their recipe steps through start, pause, completion and reset.
