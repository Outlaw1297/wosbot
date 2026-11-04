# Deals Navigation

The Task Builder Deals node supports Bank and Journey of Light. Bank is reached
from the Deals carousel with the same timed edge-seek used for Events: rewind
left until the header strip stops changing, then walk right by a fixed pull
until the Bank tab appears or the strip stops changing again. Instant default
swipes do not move this carousel. Navigation taps the detected Bank tab only
inside the tab strip and succeeds only when a bank deposit control is visible.
Journey of Light is detected by either its selected or unselected tab template,
then its main event tab is selected. These navigation actions and destination
checks live in `NavigationHelper`; routines retain their scheduling and retry
decisions.

Saved frames cover the hidden and visible Bank carousel states. Journey of
Light still needs live Task Builder confirmation before merge readiness.
