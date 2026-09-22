# ADR-003: Idempotency and unknown outcomes

Status: accepted for Create Order; production backend contract pending.

Each Create Order intention receives a non-empty `OperationId` and `IdempotencyKey`. The immutable
request snapshot and IDs are reused across attempts. Ambiguous completion becomes
`OperationState.OutcomeUnknown`; the only safe recovery is foreground status verification while the
ViewModel session exists. The mock rejects the same key with a different payload and returns the
stored result for a matching replay.
