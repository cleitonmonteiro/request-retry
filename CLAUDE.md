# RequestRetry repository guidance

Follow the repository-wide [common engineering rules](COMMON_RULES.md). This file is the
project-specific source of truth; `AGENTS.md` is a symlink to it.

## Project and commands

RequestRetry is a Jetpack Compose study app for resilient one-shot HTTP operations. It uses Kotlin,
Material 3, Navigation Compose, Ktor/OkHttp, kotlinx.serialization, Hilt/KSP, Room, and WorkManager.
The Android app is one `app` module; `server/` is an independent Node mock.

- Namespace/application ID: `io.github.cleitonmonteiro.requestretry`
- Min SDK 26; target/compile SDK 37
- Domain → data → presentation, with a payload-agnostic resilience core
- No XML layouts

Run from the repository root:

```bash
./gradlew testDebugUnitTest
./gradlew lint
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew connectedAndroidTest  # requires a device/emulator
```

Start the backend with `cd server && npm start`. The emulator reaches it at
`http://10.0.2.2:8090`; a physical device needs the host LAN address in `ApiConfig.kt`.

## Resilience architecture

The current pipeline is:

```text
Composable → ViewModel → OperationController → RetryExecutor → use case/repository → Ktor
                              │
                              └─ Room store / status reconciler / unique WorkManager work
```

The removed legacy contracts (`RetryController`, `RetryUiState`, `RetryPolicy`,
`ErrorTypeStrategy`, `RequestException`, and `ApiCall`) must not be reintroduced. Architecture
decisions are in `docs/adr/`; the operation matrix is in `docs/operations.md`.

### RetryExecutor

- Executes a suspending `OneShotCall<Input, Output>` with an immutable input snapshot.
- Applies validated `OperationSpec` limits: per-attempt timeout, overall monotonic deadline,
  maximum attempts, full-jitter backoff, and a capped server `Retry-After`.
- Uses `FailureClassifier` plus `RetryDecider`; unknown behavior is fail-closed.
- Consults a per-operation-name retry budget, circuit breaker, and shared bulkhead.
- Reports only controlled, sanitized telemetry fields through `RetryObserver`.
- Propagates `CancellationException` and `Error`; neither becomes public feedback.
- Is the only request-retry owner. Ktor request retry is not installed and OkHttp automatic
  connection retry is disabled.

### OperationController

- Owns one `StateFlow<OperationState<T>>` and serializes commands/events through a mailbox.
- Public commands are `start(input)`, `retry()`, `verifyStatus()`, `cancelObservation()`, and
  `reset()` where semantically allowed.
- Applies `CancelPrevious`, `DropWhileRunning`, `JoinExisting`, bounded `Queue`, or `Reject`.
- Stores the submitted input in the session. Never read mutable form/selection state from a retry.
- Uses a session token so obsolete results cannot alter the current state.
- Models `Running`, `BackingOff`, `Succeeded`, `Failed`, and `OutcomeUnknown` explicitly.
- Never treats local cancellation as remote rollback.

### Failures and UI

- `data/remote/TransportFailureMapper.kt` converts transport/Ktor/HTTP failures to stable
  `RequestFailure`; the wrapper may retain a cause for diagnostics only.
- `ConservativeRetryDecider` combines a typed failure with operation safety. 4xx statuses are
  terminal by default; only 408, 425, and 429 are transient by explicit rule. Unknown/protocol/TLS
  failures do not retry.
- Presentation receives only `PublicFailure` and sealed `RecoveryAction`. All retry/error copy is
  in Android string resources and no exception message is rendered.
- `OperationStateScaffold` renders common operation states. Feature screens provide success
  content and map recovery actions to their single intent entry point.

### Profiles

- Profile, Orders GET, and Picker items are foreground reads: at most three total attempts,
  bounded full jitter, and `CancelPrevious`.
- Picker send is currently an unsafe command: one attempt, `DropWhileRunning`, no generic retry.
- Create Order is an idempotent command: mandatory stable `OperationId` and `IdempotencyKey`,
  `DropWhileRunning`, persistence, automatic retries only when safe, and status verification for
  ambiguous completion.
- Polling and background sync need separate components; do not force them through one-shot calls.

## Durable Create Order

`NewOrderRequest` requires non-empty typed operation/idempotency identity. `CreateOrderUseCase`
inserts operation identity plus a one-way payload fingerprint in Room before network I/O and marks
each send. Form/customer fields are not persisted. The Room store uses guarded
updates so terminal states do not become active again. A failure after sending becomes pending
confirmation rather than an instruction to create a new order.

`GET /operations/{operationId}` returns `PROCESSING`, `SUCCEEDED`, `REJECTED`, or `UNKNOWN`.
`VerifyOrderOperationUseCase` updates Room from that authority. `OutcomeUnknown` schedules one
network-constrained WorkManager job named from the non-sensitive operation UUID with
`ExistingWorkPolicy.KEEP`; the worker only queries status and never generates a key or repeats the
mutation. WorkManager backoff is the sole backoff around worker reruns.

The mock persists idempotency records in `server/.operations.json`, reserves identity before the
effect, validates a payload fingerprint, returns the original result for an identical replay, and
returns 409 for the same key with different input. Production needs an atomic durable store,
approved retention, encryption, tenant isolation, and logout rules.

## Data and dependency injection

- Domain contains models, repository/store contracts, and use cases only; no Android, Compose,
  Ktor, Hilt, Room, or DTO imports.
- DTOs remain wire-shaped and use explicit mappers. Repositories expose cold `Flow` values and do
  not add `flowOn`, swallow failures, or retry.
- `ScenarioHolder` and `HttpClient` are singletons. `ApiClient` stays unscoped so independent
  ViewModels/requests do not share simulated attempt counters.
- Resilience strategies and guards are bound in `RetryModule`; Room in `DatabaseModule`; repository,
  operation-store, and scheduler interfaces in `RepositoryModule`.
- Add versions in `gradle/libs.versions.toml`, never inline dependency coordinates.

## Network and security

- Ktor has explicit connect/socket/request timeouts. The executor still owns the overall deadline.
- Correlation IDs are generated per HTTP request. Response request ID, backend code, status, and
  capped `Retry-After` are captured when present.
- Release logging is `NONE`. Debug logging is headers-only and redacts authorization, cookies, and
  idempotency keys; bodies are never logged.
- Release blocks cleartext through both manifest and Network Security Config. Debug permits
  cleartext only for emulator host `10.0.2.2`.
- `AuthRefreshCoordinator` provides generation-aware single-flight refresh. When authentication is
  wired to a real backend, allow at most one refresh per logical request and keep it outside the
  business retry budget.

## MVI and feature rules

- Every ViewModel exposes exactly one immutable `StateFlow<XxxUiState>`, built with eager `combine`
  where multiple internal flows exist. Tests read only this public state.
- Every ViewModel-backed feature exposes one sealed intent API and lifecycle-aware effects.
- Route composables alone know the ViewModel. Stateless screens receive immutable state and one
  intent lambda.
- Scenario changes reload disposable reads only. They must never silently replay a mutation.
- Create Order form edits after submit cannot change the submitted session.
- Picker intentionally owns separate item-read and send controllers, combined into one UI state.

## Tests

- JVM tests use JUnit4, coroutine virtual time, Turbine where helpful, hand-written fakes, and Ktor
  `MockEngine`; no mocking library or Hilt graph.
- Use `MainDispatcherRule` for ViewModels. Direct controllers in `runTest` should use
  `backgroundScope` so the mailbox lifecycle is cancelled automatically.
- Cover the typed failure matrix, full jitter bounds, deadline/timeout, cancellation, stale results,
  concurrent starts, retry budget, circuit breaker, stable request identity, unknown outcome, and
  reconciliation.
- `mockHttpClient` keeps its engine on `Dispatchers.Unconfined`; do not add repository dispatcher
  switches that break virtual time.
- Run unit tests, lint, and the relevant build variants before handoff. Instrumented Room/process
  death tests require an emulator/device.
