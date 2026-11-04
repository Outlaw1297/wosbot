# Character Switch Constraints

Chief Profile OCR runs only after the Settings control is visible. Name and ID
regions stay stacked with the name strip above the ID row; loading frames that
OCR the `ID …` label into the name strip are discarded, and digit runs shorter
than six characters are rejected as furnace or power bleed rather than a
character ID.

Unreadable OCR is not a character mismatch. Verification leaves the profile
closed and Initialize does not open Settings or the roster on garbage readings.
When verification does mismatch with usable evidence, the Chief Profile panel
stays open so switch can tap the already-located Settings control instead of
searching again on a transient frame.
