# ADR-001: Single retry owner

Status: accepted for the study implementation; production calibration pending.

`RetryExecutor` is the only request-retry owner. Ktor `HttpRequestRetry` is not installed and the
OkHttp engine has `retryOnConnectionFailure(false)`.

This keeps attempts, UI state, and telemetry consistent.
