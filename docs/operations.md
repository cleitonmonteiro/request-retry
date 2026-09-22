# Operation catalogue

| Operation | Profile | Survives process | Identity | Limit |
|---|---|---:|---|---|
| Profile GET | Foreground read | No | generated operation ID | 3 manual attempts with full-jitter cooldown |
| Create order | Idempotent foreground command | No | operation ID + idempotency key | 3 manual attempts with full-jitter cooldown |
| Order status | Foreground verification query | No | original operation ID | one explicit user action |

The Ktor/OkHttp layer never retries; `RetryExecutor` owns retry cooldown and never repeats a
business request without a user action. A Create Order response with ambiguous delivery becomes
`OutcomeUnknown` and can be verified while its ViewModel session is alive. The app does not persist
operations or schedule background work.

The mock persists idempotency records in `server/.operations.json`, but production would need an
atomic durable backend store, retention, and tenant/security policies.
