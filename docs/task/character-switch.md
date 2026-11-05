# Character Switch Constraints

Chief Profile OCR runs only after the Settings control is visible. Name and ID
regions stay stacked with the name strip above the ID row; a higher fallback
name box is tried when the primary strip still OCR's `ID …`. Digit runs shorter
than six characters are rejected as furnace or power bleed rather than a
character ID.

Startup overlays left on home can swallow the avatar tap. Verification retries
opening Chief Profile with Back recovery, and Initialize dismisses known startup
dialogs before each attempt. Unreadable OCR or a profile that never becomes
ready is not a character mismatch and must not continue into the rest of the
task list: Initialize yields the emulator slot on a short profile cooldown.
When the roster already shows the target active with a matching name OCR,
unreadable Chief Profile confirmation accepts that roster evidence instead of
failing the switch.
