# ADR-006: Logging and privacy

Status: accepted for the client baseline; audit platform pending.

Release networking logging is disabled. Debug logging is headers-only and redacts authorization,
cookies, and idempotency keys. Metrics contracts use controlled enums and never expose payload,
account/customer identifiers, exception messages, operation IDs, or URLs as labels. Cleartext is
blocked in release and allowed only for the emulator mock in debug network security configuration.
Release optimization/shrinking is enabled and must remain covered by the release build check.
