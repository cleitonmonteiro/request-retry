# ADR-005: Policies by operation profile

Status: accepted with preliminary values.

Reads use `CancelPrevious` and three total manual attempts separated by full-jitter cooldown.
Idempotent commands use `DropWhileRunning`, stable identity, and foreground status
verification. Unsafe commands, polling, and background synchronization are outside this study app.

All durations and attempt counts are validated by `OperationSpec`; production numbers require
latency and capacity data.
