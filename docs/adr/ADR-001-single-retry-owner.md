# ADR-001: Single retry owner

Status: accepted for the study implementation; production calibration pending.

`RetryExecutor` is the only request-retry owner. Ktor `HttpRequestRetry` is not installed and the
OkHttp engine has `retryOnConnectionFailure(false)`. WorkManager only repeats reconciliation
queries and never creates a new business intention or idempotency key.

This keeps attempts, deadlines, retry budgets, UI state, and telemetry consistent.
