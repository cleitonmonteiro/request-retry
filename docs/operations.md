# Operation catalogue and preliminary service levels

These values are engineering defaults for the study project. Production values require approval
from product, backend, security, and SRE.

| Operation | Profile | Survives UI/process | Identity | Preliminary limit |
|---|---|---:|---|---|
| Profile GET | Foreground read | No | generated local operation ID | 3 attempts, 5 s each, 20 s total |
| Orders GET | Foreground read | No | generated local operation ID | 3 attempts, 5 s each, 20 s total |
| Items GET | Foreground read | No | generated local operation ID | 3 attempts, 5 s each, 20 s total |
| Send item | Unsafe foreground command | No | generated local operation ID | 1 attempt, no automatic retry |
| Create order | Idempotent foreground command | Yes | operation ID + idempotency key | 3 attempts, 8 s each, 30 s total |
| Order status | Reconciliation query | Yes | original operation ID | WorkManager unique work, at most 5 worker runs |

The Ktor/OkHttp layer never retries. `RetryExecutor` owns every business-request retry. Leaving a
screen cancels disposable reads with the ViewModel; a persisted Create Order remains queryable and
is reconciled using unique WorkManager work. The mock persists idempotency records in
`server/.operations.json`; production still requires an atomic, durable backend store and an
approved retention policy.

External decisions still open: production SLOs, telemetry backend, feature-flag provider,
authentication/token contract, encryption policy for persisted request fields, logout/user-switch
rules, and rollback thresholds.
