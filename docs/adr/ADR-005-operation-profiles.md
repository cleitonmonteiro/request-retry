# ADR-005: Policies by operation profile

Status: accepted with preliminary values.

Reads use `CancelPrevious` and bounded automatic retry. Idempotent commands use
`DropWhileRunning`, stable identity, persistence, and status verification. Unsafe commands receive
one attempt and no generic automatic retry. Polling and background synchronization remain distinct
components rather than pretending to be one-shot calls.

All durations and attempt counts are validated by `OperationSpec`; production numbers require
latency and capacity data.
