# ADR-004: Durable critical commands

Status: accepted for the pilot; storage-risk review pending.

Room is the local source of truth for Create Order. The operation is inserted before opening the
socket, state changes use guarded SQL updates, and terminal states cannot return to active states.
`SENDING` failures become `PENDING_CONFIRMATION`; reconciliation uses WorkManager unique work with
`KEEP`. A worker never generates new operation identity or resends the mutation.

The study database stores operation identity, a one-way payload fingerprint, state, counters, and
the confirmed result; it does not persist form/customer fields. A production rollout must still
decide key protection, retention, logout, and tenant isolation with security.
