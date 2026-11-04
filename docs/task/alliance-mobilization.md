# Alliance Mobilization Constraints

Alliance Mobilization is a monthly Events-strip tab. When the strip walk reports
`TAB_ABSENT`, the routine skips immediately until the next `00:00` UTC day rather
than burning navigation retries or a five-minute soft retry. That keeps the task
from holding the global emulator slot and blocking the rest of a multi-emulator
fleet while the event is simply not present.
