# Chief Order Constraints

The Chief Order shelf is a 2x3 book grid on 720x1280. Current shelf orders are
Crunch Time, Rush Job, Comprehensive Care, Double Time, Advanced Training, and
Dawn Alliance Commendation. Each enabled order uses its shelf-icon template,
then the shared blue `Enact` control on the order detail page.

Crunch Time's detail page shows a 12-hour cooldown; the other new shelf orders
use a 12-hour reschedule until measured otherwise. Rush Job remains 24 hours.
Legacy Urgent Mobilisation and Productivity Day stay available for older shelves.

Cooldown overlays hide or darken a book, so a missing shelf-icon match is treated
as unavailable and retried later rather than as a false enact success.
