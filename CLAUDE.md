# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

RequestRetry is a study case for the request-retry-with-backoff pattern in Jetpack Compose, built on top of Android Studio's default "Empty Activity" template. Every screen calls a real HTTP endpoint (a small mocked Node server, see `server/`) through Ktor, and demonstrates a Loading/Success/Feedback state machine with manual, budgeted retries — failure is simulated client-side (see `ApiClient` below) since the mocked server always succeeds.

- Package / namespace / application ID: `io.github.cleitonmonteiro.requestretry`
- Single module: `app`, plus a standalone `server/` Node.js project (no build relationship to the Gradle build — it's just the backend the app's Ktor client talks to)
- UI toolkit: Jetpack Compose (Material 3) + Navigation Compose, no XML layouts
- Min SDK 26, target/compile SDK 37
- Kotlin 2.2.10, AGP 9.4.0-alpha05
- Networking: Ktor client (OkHttp engine) + kotlinx.serialization content negotiation, talking to the Node mock server over plain HTTP. `AndroidManifest.xml` sets `android:usesCleartextTraffic="true"` and requests `INTERNET` for this reason.
- DI: Hilt + KSP. `gradle.properties` sets `android.disallowKotlinSourceSets=false` — required because this AGP alpha's built-in-Kotlin support otherwise rejects KSP's use of the classic `kotlin.sourceSets` DSL to register its generated-code directory; remove it if a future KSP/AGP release fixes that upstream.

## Common commands

Run all commands from the repository root using the Gradle wrapper.

```bash
# Build debug APK
./gradlew assembleDebug

# Full build (compiles, runs checks, assembles all variants)
./gradlew build

# Run JVM unit tests (app/src/test)
./gradlew test

# Run a single unit test class or method
./gradlew testDebugUnitTest --tests "io.github.cleitonmonteiro.requestretry.retry.RetryControllerTest"
./gradlew testDebugUnitTest --tests "io.github.cleitonmonteiro.requestretry.retry.RetryControllerTest.methodName"

# Run instrumented tests (app/src/androidTest) — requires a connected device/emulator
./gradlew connectedAndroidTest

# Lint
./gradlew lint

# Install debug build on a connected device/emulator
./gradlew installDebug
```

Start the mocked backend before running the app on an emulator (the app expects it at `10.0.2.2:8090`, the emulator's alias for the host's loopback interface):

```bash
cd server && npm start   # or: node index.js
```

A physical device can't reach `10.0.2.2` — point `BASE_URL` in `data/remote/ApiConfig.kt` at the host machine's LAN IP instead for that case.

## Architecture notes

The app is layered domain → data → presentation, on top of a shared, payload-agnostic retry core:

- `retry/` is the reusable core every demo screen shares, and the reason two features exist at all — it's payload-agnostic (generic in `T`), and knows nothing about domain models or Hilt:
  - `RetryPolicy` — a `fun interface` for computing the delay before a retry attempt; `ExponentialBackoffPolicy` is the default implementation (doubling delay, capped, with jitter, `Random` injected for testability).
  - `RetryUiState<T>` — the sealed `Loading` / `Success` / `Feedback` state machine. `Loading.backoffSecondsRemaining` carries the countdown; `Feedback.canRetry` goes false once the 3-attempt budget is spent.
  - `RetryController<T>` — owns the attempt counter, the in-flight `Job`, and a `StateFlow<RetryUiState<T>>`. `load()` resets the budget; `retry()` is a no-op once exhausted. `run()` collects `apiCall()`'s Flow through `.map` (success → `RetryUiState.Success`), `.onStart` (emits `Loading`), and `.catch` (failure → `Feedback`) — `Flow.catch` is transparent to cancellation, so a job superseded by a newer `load()`/`retry()` dies quietly instead of surfacing a spurious `Feedback` (see `RetryControllerTest`'s cancellation test, which pins this down).
  - `ApiCall<T>` — `operator fun invoke(): Flow<T>`, a **single-shot** Flow (one emission, then completes). Not `suspend`: this is what lets `RetryController` use Flow operators instead of `runCatching`. A ViewModel bridges its use case to this with a one-line adapter (`ApiCall { getProfile() }`), which is the entire seam between domain and `retry/`.
  - `RetryControllerFactory` — `@Inject`-constructed, holds the app's one injected `RetryPolicy` and hands out controllers (`factory.create(viewModelScope, apiCall)`). This is the only `retry/` type carrying a DI annotation — bare JSR-330 `@Inject`, no Dagger types — so the package otherwise stays framework-free. Exists so backoff tuning has one home (`di/RetryModule.kt`) instead of being a constructor default repeated at every ViewModel.
- `domain/` has no Android, Compose, or Dagger imports:
  - `model/` — `UserProfile`, `Order`.
  - `repository/` — `ProfileRepository` / `OrdersRepository` interfaces, returning `Flow<T>` (not `suspend fun`).
  - `usecase/` — `GetProfileUseCase` (pure delegation) and `GetOrdersUseCase` (`.map`s the repository's Flow to sort orders by `total` descending — the one piece of real domain logic in this app, and what its unit test asserts). `GetItemsUseCase`/`SendItemUseCase` are the picker screen's pair, both delegating to `ItemsRepository`.
- `data/` implements the domain contracts against a real (if mocked) backend:
  - `remote/ScenarioHolder` — `@Singleton`, app-wide `StateFlow<Scenario>` (`ALWAYS_SUCCEED` / `ALWAYS_FAIL` / `SUCCEED_ON_THIRD_ATTEMPT`). Because it's shared, picking a scenario on one screen affects every screen — this is intentional, not a bug to fix. It also tracks a `generation` counter, bumped on every `select()` call including a reselection of the same scenario, which is what `ApiClient` watches (not the scenario value) to know when to restart its attempt count — otherwise re-tapping the same scenario chip mid-demo wouldn't reset `SUCCEED_ON_THIRD_ATTEMPT`'s progress.
  - `remote/ApiClient` — wraps every real Ktor call in `Scenario`-driven failure injection: it always runs the call, then only lets the result through if the current `Scenario` allows it, throwing `IOException` otherwise. This is what keeps the retry/backoff demo meaningful even though the Node server itself never fails on its own. Left **unscoped** on purpose (see below).
  - `remote/ApiConfig.kt` — `BASE_URL` (`http://10.0.2.2:8090`, the emulator's alias for the host's loopback interface — see Common commands above for the physical-device caveat).
  - `remote/*Dto.kt` — wire-shaped DTOs: `@Serializable` (kotlinx.serialization) data classes with idiomatic camelCase properties, each annotated `@SerialName("snake_case_key")` to document the wire format without violating Kotlin naming conventions (`OrderDto.totalAmount`/`total_amount` is a `String`, deliberately, to give the mapper something to parse). These are genuinely serialized now: Ktor's `ContentNegotiation` plugin (`di/NetworkModule.kt`) decodes every response body straight into them.
  - `remote/*RemoteDataSource.kt` — inject the `HttpClient` and `ApiClient`, call the Node server's REST endpoints (`GET /profile`, `GET /orders`, `GET /items`, `POST /items/{id}/send`), return DTOs.
  - `mapper/*Mapper.kt` — `DTO.toDomain()` extension functions.
  - `repository/*RepositoryImpl.kt` — inject a data source, wrap the still-`suspend` fetch in `flow { emit(...) }`, map DTO → domain. Exceptions are never swallowed here; `RetryController`'s `.catch` is what catches them, at **collection** time (not when the use case is called). **Do not add `.flowOn(Dispatchers.IO)` here** — it would move the real request off the test dispatcher in `ApiClientTest`/repository tests, breaking their virtual time (production tests use a `MockEngine`-backed `HttpClient`, so no real socket ever opens anyway).
- `di/RepositoryModule.kt` — `@Binds` for the three repository interfaces. `di/RetryModule.kt` — `@Provides` the app's single `RetryPolicy` (`ExponentialBackoffPolicy`, `@Singleton`; needed because `RetryPolicy` is a `fun interface` Dagger can't construct on its own). `di/NetworkModule.kt` — `@Provides` the app's single `HttpClient` (`@Singleton`; OkHttp engine + `ContentNegotiation` installed with kotlinx.serialization `Json`). Everything else (use cases, data sources, `ApiClient`, `RetryControllerFactory`, the ViewModels) is plain constructor injection, discovered automatically.
  - **Scoping matters**: only `ScenarioHolder` and the Ktor `HttpClient` are `@Singleton` — the `HttpClient` because building a new one per request/screen would be wasteful, not because its state needs to be shared. Every other binding in the chain, including `ApiClient`, is unscoped, so each `@HiltViewModel` gets its own fresh `ApiClient` (and therefore its own attempt counter for `SUCCEED_ON_THIRD_ATTEMPT`) even though they all share one `HttpClient`. The retry budget itself (`RetryController.retriesUsed`) is per-ViewModel and unaffected either way — what a `@Singleton` `ApiClient` would actually break is its attempt counter outliving the screen, so a re-entered `SUCCEED_ON_THIRD_ATTEMPT` demo would succeed immediately instead of failing twice first.
- `ui/components/RetryStateScaffold.kt` is the UI half of the retry-core reuse: it renders Loading and Feedback itself and only delegates the Success branch to the caller, so a feature screen's own composable is just the success-case layout plus wiring.
- **Strict MVI, single state of truth per screen**: every `@HiltViewModel` exposes exactly one `state: StateFlow<XxxUiState>` — never multiple sibling `StateFlow`s (e.g. one for the request, one for the scenario). Internally a ViewModel may still own several independent `RetryController`s and small `MutableStateFlow`s (selection, etc.); those are combined with `kotlinx.coroutines.flow.combine(...).stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = ...)` into one immutable `XxxUiState` data class, which is the only thing the Composable ever collects. `SharingStarted.Eagerly` (not `WhileSubscribed`) is deliberate: the combine coroutine starts as soon as the ViewModel is constructed, so `state.value` is always live and correct even before any UI subscribes to it (tests rely on this, reading `.value` directly after `advanceUntilIdle()`). The `initialValue` is built from each source flow's own `.value` at construction time so there's no gap before the first `combine` emission.
- `feature/profile/` and `feature/orders/` are two independent screens over different payloads (`UserProfile`, `List<Order>`) — each a `@HiltViewModel` (injecting a use case, `ScenarioHolder`, and `RetryControllerFactory`) wired to `RetryStateScaffold` via `hiltViewModel()`. A ViewModel builds its controller via `retryControllers.create(viewModelScope, ApiCall { getProfile() })` — it never constructs `RetryController` directly, since only the factory carries the injected `RetryPolicy`. Each exposes `ProfileUiState(request, scenario)` / `OrdersUiState(request, scenario)` as its single `state`. They're intentionally near-identical; that similarity is what demonstrates the retry core (and now the layering) is actually shared. `feature/home/` is the launcher screen.
- `feature/picker/` demonstrates **one ViewModel owning two independent `RetryController`s, still folded into one `PickerUiState`** — the pattern to reach for whenever a screen has more than one request: give each request its own controller (so it can load/fail/retry independently), but combine every controller's `state` (plus any other UI-relevant flow) into a single `PickerUiState(items, send, selectedItem, scenario)` rather than exposing them as separate `StateFlow`s. `PickerViewModel` has `itemsController` (loads on `init`, like every other screen) and `sendController` (built up front but its `load()` isn't called until `selectItem()` fires — a controller can sit idle in its default `Loading()` state as long as the UI doesn't render it until then, which is why `PickerScreen` only composes the send `RetryStateScaffold` once `uiState.selectedItem != null`). Because `GetItemsUseCase` and `SendItemUseCase` each resolve `ItemsRepository` independently and `ApiClient` is unscoped (unlike the shared `HttpClient`), the two requests get separate `ApiClient` instances (and separate `SUCCEED_ON_THIRD_ATTEMPT` counters) automatically — the same mechanism, applied within a single ViewModel instead of across two. `setScenario()` deliberately reloads only `itemsController`; changing the scenario should not silently resurrect or restart a past selection's send.
- `navigation/AppNavHost.kt` wires `home` → `profile` / `orders` / `picker` with Navigation Compose. Each destination's ViewModel is scoped to its `NavBackStackEntry` via `hiltViewModel()`, so popping back and re-entering a demo always starts with a fresh retry budget.
- `RequestRetryApplication` (`@HiltAndroidApp`) and `MainActivity` (`@AndroidEntryPoint`) are the two Hilt entry points; there's no other Application subclass logic.
- Dependency versions are centralized in `gradle/libs.versions.toml` (Gradle version catalog) and referenced from `app/build.gradle.kts` via `libs.*` aliases — add new dependencies there rather than hardcoding coordinates in the module build file.
- `release` build type currently has optimization (minify/shrink) disabled in `app/build.gradle.kts`.

## Testing patterns already established

- JVM unit tests only (`app/src/test`), plain JUnit4 assertions — no mocking library or assertion library beyond JUnit is a dependency; write fakes (hand-rolled `ProfileRepository`/`OrdersRepository` implementations, or the real `ApiClient` + `ScenarioHolder` pair, with a `mockHttpClient { path -> ... }`-built `HttpClient` from `data/remote/MockHttpClients.kt` standing in for the real one) instead of mocks. No Hilt graph is ever started in tests — everything is constructed directly. `mockHttpClient` pins its `MockEngine`'s dispatcher to `Dispatchers.Unconfined` so responses resolve within `runTest`'s virtual time instead of hopping onto a real thread pool `advanceUntilIdle()` can't see through.
- `MainDispatcherRule` (`app/src/test/.../MainDispatcherRule.kt`) points `Dispatchers.Main` at a `StandardTestDispatcher` for any test touching a `ViewModel`'s `viewModelScope`; not needed when a `RetryController` is constructed directly with `scope = this` inside `runTest`.
- Backoff delays are real `delay()` calls under a `StandardTestDispatcher`, so tests use `advanceUntilIdle()` (or Turbine's `test { }` on `RetryController.state`, per `RetryControllerTest`) to fast-forward through them deterministically rather than waiting in real time.
- `ApiCall`/repository/use-case fakes return `flowOf(value)` for success and `flow { throw ... }` for failure (never a plain `throw` outside a flow builder — collection is what triggers it), and callers assert with `.first()` rather than calling the function directly.
- `RetryControllerTest`'s cancellation test is the regression guard for `RetryController`'s `.catch` transparency: it starts a `load()` whose Flow never resolves, supersedes it with another `load()`, and asserts via Turbine that only the second call's result is ever observed — no `Feedback` from the first. If you touch `run()`'s Flow pipeline, re-run this test specifically.
- `ProfileViewModelTest` builds its `RetryControllerFactory` with a zero-delay `RetryPolicy { Duration.ZERO }`, not the jittered production `ExponentialBackoffPolicy` — this determinism is the actual payoff of `RetryController` going through an injected factory instead of a hardcoded default.
- Every ViewModel test asserts through the single combined `viewModel.state.value.<field>` (e.g. `.request`, `.items`, `.send`, `.scenario`) — never against an intermediate controller's flow directly — since that combined `XxxUiState` is the ViewModel's only public contract.
- Repository/use-case tests fake at the narrowest seam that gives a deterministic result: `GetOrdersUseCaseTest` fakes `OrdersRepository` directly; `ProfileRepositoryImplTest` and `ProfileViewModelTest` instead wire a real `ApiClient`/`ScenarioHolder` pair against a `mockHttpClient`. `ApiClientTest` (renamed from `FakeNetworkTest`) is where `Scenario` behavior and the `generation`-based attempt-count reset are actually asserted — don't re-test that indirectly through a ViewModel.
- `PickerViewModelTest` wires its real repository chain **twice** (once per use case) — sharing one `mockHttpClient`-built `HttpClient` but constructing a separate `ApiClient` for each — which mirrors what Hilt actually builds (see `feature/picker/` above, and the scoping note under `di/`) and is what makes its independence test honest.
