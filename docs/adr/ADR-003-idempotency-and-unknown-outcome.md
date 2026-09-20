# ADR-003: Idempotency and unknown outcomes

Status: accepted for Create Order; production backend contract pending.

Each Create Order intention receives a non-empty `OperationId` and `IdempotencyKey`. The immutable
request snapshot, IDs, and payload fingerprint are reused across attempts and reconciliation.
Ambiguous completion becomes `OperationState.OutcomeUnknown`; the only safe recovery is status
verification. The mock rejects the same key with a different payload and returns the stored result
for a matching replay.
