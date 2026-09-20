# ADR-002: Typed failures and fail-closed decisions

Status: accepted.

Transport/HTTP exceptions are mapped to `RequestFailure` at the data boundary. The resilience
layer maps failure plus operation safety to a sealed `RetryDecision`; unknown, protocol, TLS, and
unlisted HTTP failures stop by default. Presentation receives only `PublicFailure` and
`RecoveryAction`, while the original cause remains internal to `RequestFailureException`.
