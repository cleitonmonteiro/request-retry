# RequestRetry repository guidance

Follow [COMMON_RULES.md](COMMON_RULES.md). This file is the project-specific source of truth;
`AGENTS.md` points here.

RequestRetry is a Jetpack Compose study app for safe one-shot HTTP operations. It uses Kotlin,
Material 3, Navigation Compose, Ktor/OkHttp, kotlinx.serialization, and Hilt/KSP. The Android app
has one module and `server/` is an independent Node mock.

Run from the repository root:

```bash
./gradlew testDebugUnitTest
./gradlew lint
./gradlew assembleDebug
./gradlew assembleRelease
```

The mock starts with `cd server && npm start`. The emulator reaches it at
`http://10.0.2.2:8090`; a physical device needs the host LAN address in `ApiConfig.kt`.

## Study scope

The app intentionally has only two journeys:

- Profile GET is a disposable foreground read with at most three manual attempts and cancels any
  previous execution when started again.
- Create Order is a foreground idempotent command with a stable `OperationId` and
  `IdempotencyKey`, at most three manual attempts, and cancels any previous execution when started
  again.

Create Order keeps its immutable submitted snapshot for retries. It does not survive process
death: Room, WorkManager, and background reconciliation are out of scope. The mock persists
idempotency records in `server/.operations.json`, returns the original result for an identical
replay, and returns 409 for mismatched input.

## Resilience architecture

```text
Composable → ViewModel → OperationController → RetryExecutor → use case/repository → Ktor
```

`RetryExecutor` is the only request-retry owner. It applies typed failure classification,
fail-closed decisions, full-jitter cooldown, cancellation propagation, and
sanitized retry telemetry. It never makes a second HTTP call automatically: a retryable failure
returns control to the user immediately, and the cooldown is spent at the start of the next
manual attempt the user explicitly triggers. Ktor request retry is not
installed and OkHttp connection retry is disabled.

`OperationController` owns one `StateFlow<OperationState<T>>`, serializes `start` and `retry`
through a mailbox, captures immutable session input, and rejects stale results by session
identity. It models running, success, and failure explicitly.

`OperationSpec` has only operation identity/name, attempts, and backoff. The controller always
cancels a previous execution when a new start arrives. Do not reintroduce queue, join, reject,
retry-budget, circuit-breaker, bulkhead, automatic-auth-refresh, background-sync, or
ambiguous-outcome/status-verification abstractions into this study app.

## Boundaries and tests

- Domain contains models, repository contracts, and use cases only; no Android, Compose, Ktor,
  Hilt, or DTO imports.
- DTOs are wire-shaped and use explicit mappers. Repositories expose cold `Flow` values and do not
  retry, swallow failures, or switch dispatchers.
- `ScenarioHolder` and `HttpClient` are singletons; `ApiClient` remains unscoped so attempt counts
  stay independent.
- Every ViewModel exposes one immutable public `StateFlow`, one sealed intent API, and
  lifecycle-aware effects. Scenario changes reload Profile only and never replay Create Order.
- JVM tests use JUnit4, virtual coroutine time, fakes, and Ktor `MockEngine`; no Hilt graph or
  mocking library. Cover the typed failure matrix, backoff, cancellation, stale
  results, cancellation of previous executions, and stable request identity.
