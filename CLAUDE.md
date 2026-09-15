# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

RequestRetry is a study case for the request-retry-with-backoff pattern in Jetpack Compose, built on top of Android Studio's default "Empty Activity" template. There is no real networking — every screen calls a mocked API and demonstrates a Loading/Success/Feedback state machine with manual, budgeted retries.

- Package / namespace / application ID: `io.github.cleitonmonteiro.requestretry`
- Single module: `app`
- UI toolkit: Jetpack Compose (Material 3) + Navigation Compose, no XML layouts
- Min SDK 26, target/compile SDK 37
- Kotlin 2.2.10, AGP 9.4.0-alpha05
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

## Architecture notes

The app is layered domain → data → presentation, on top of a shared, payload-agnostic retry core:

- `retry/` is the reusable core every demo screen shares, and the reason two features exist at all — it's payload-agnostic (generic in `T`), and knows nothing about domain models or Hilt:
  - `RetryPolicy` — a `fun interface` for computing the delay before a retry attempt; `ExponentialBackoffPolicy` is the default implementation (doubling delay, capped, with jitter, `Random` injected for testability).
  - `RetryUiState<T>` — the sealed `Loading` / `Success` / `Feedback` state machine. `Loading.backoffSecondsRemaining` carries the countdown; `Feedback.canRetry` goes false once the 3-attempt budget is spent.
  - `RetryController<T>` — owns the attempt counter, the in-flight `Job`, and a `StateFlow<RetryUiState<T>>`. `load()` resets the budget; `retry()` is a no-op once exhausted. A ViewModel *composes* a controller (via `viewModelScope`) rather than subclassing anything.
  - `ApiCall<T>` — the `suspend operator fun invoke(): T` contract. A ViewModel bridges its use case to this with a one-line adapter (`ApiCall { getProfile() }`), which is the entire seam between domain and `retry/`.
- `domain/` has no Android or Compose imports:
  - `model/` — `UserProfile`, `Order`.
  - `repository/` — `ProfileRepository` / `OrdersRepository` interfaces.
  - `usecase/` — `GetProfileUseCase` (pure delegation) and `GetOrdersUseCase` (sorts orders by `total` descending — the one piece of real domain logic in this app, and what its unit test asserts).
- `data/` implements the domain contracts against a mocked backend:
  - `remote/ScenarioHolder` — `@Singleton`, app-wide `StateFlow<Scenario>` (`ALWAYS_SUCCEED` / `ALWAYS_FAIL` / `SUCCEED_ON_THIRD_ATTEMPT`). Because it's shared, picking a scenario on one screen affects every screen — this is intentional, not a bug to fix. It also tracks a `generation` counter, bumped on every `select()` call including a reselection of the same scenario, which is what `FakeNetwork` watches (not the scenario value) to know when to restart its attempt count — otherwise re-tapping the same scenario chip mid-demo wouldn't reset `SUCCEED_ON_THIRD_ATTEMPT`'s progress.
  - `remote/FakeNetwork` — stands in for a real HTTP client: an artificial `delay`, and fails according to the current `Scenario`. Left **unscoped** on purpose (see below).
  - `remote/*Dto.kt` — wire-shaped DTOs (snake_case fields; `OrderDto.total_amount` is a `String`, deliberately, to give the mapper something to parse) plus their sample fixtures.
  - `remote/*RemoteDataSource.kt` — inject `FakeNetwork`, return DTOs.
  - `mapper/*Mapper.kt` — `DTO.toDomain()` extension functions.
  - `repository/*RepositoryImpl.kt` — inject a data source, map DTO → domain, implement the domain interface. Exceptions are never swallowed here; `RetryController` is what catches them.
- `di/RepositoryModule.kt` is the only Hilt module — `@Binds` for the two repository interfaces. Everything else (use cases, data sources, `FakeNetwork`, the ViewModels) is plain constructor injection, discovered automatically.
  - **Scoping matters**: only `ScenarioHolder` is `@Singleton`. Every other binding in the chain is unscoped, so each `@HiltViewModel` gets its own fresh `FakeNetwork` (and therefore its own attempt counter for `SUCCEED_ON_THIRD_ATTEMPT`). The retry budget itself (`RetryController.retriesUsed`) is per-ViewModel and unaffected either way — what a `@Singleton` repository would actually break is `FakeNetwork`'s attempt counter outliving the screen, so a re-entered `SUCCEED_ON_THIRD_ATTEMPT` demo would succeed immediately instead of failing twice first.
- `ui/components/RetryStateScaffold.kt` is the UI half of the retry-core reuse: it renders Loading and Feedback itself and only delegates the Success branch to the caller, so a feature screen's own composable is just the success-case layout plus wiring.
- `feature/profile/` and `feature/orders/` are two independent screens over different payloads (`UserProfile`, `List<Order>`) — each a `@HiltViewModel` (injecting a use case + `ScenarioHolder`) wired to `RetryStateScaffold` via `hiltViewModel()`. They're intentionally near-identical; that similarity is what demonstrates the retry core (and now the layering) is actually shared. `feature/home/` is the launcher screen.
- `navigation/AppNavHost.kt` wires `home` → `profile` / `orders` with Navigation Compose. Each destination's ViewModel is scoped to its `NavBackStackEntry` via `hiltViewModel()`, so popping back and re-entering a demo always starts with a fresh retry budget.
- `RequestRetryApplication` (`@HiltAndroidApp`) and `MainActivity` (`@AndroidEntryPoint`) are the two Hilt entry points; there's no other Application subclass logic.
- Dependency versions are centralized in `gradle/libs.versions.toml` (Gradle version catalog) and referenced from `app/build.gradle.kts` via `libs.*` aliases — add new dependencies there rather than hardcoding coordinates in the module build file.
- `release` build type currently has optimization (minify/shrink) disabled in `app/build.gradle.kts`.

## Testing patterns already established

- JVM unit tests only (`app/src/test`), plain JUnit4 assertions — no mocking library or assertion library beyond JUnit is a dependency; write fakes (hand-rolled `ProfileRepository`/`OrdersRepository` implementations, or the real `FakeNetwork` + `ScenarioHolder` pair) instead of mocks. No Hilt graph is ever started in tests — everything is constructed directly.
- `MainDispatcherRule` (`app/src/test/.../MainDispatcherRule.kt`) points `Dispatchers.Main` at a `StandardTestDispatcher` for any test touching a `ViewModel`'s `viewModelScope`; not needed when a `RetryController` is constructed directly with `scope = this` inside `runTest`.
- Backoff delays are real `delay()` calls under a `StandardTestDispatcher`, so tests use `advanceUntilIdle()` (or Turbine's `test { }` on `RetryController.state`, per `RetryControllerTest`) to fast-forward through them deterministically rather than waiting in real time.
- Repository/use-case tests fake at the narrowest seam that gives a deterministic result: `GetOrdersUseCaseTest` fakes `OrdersRepository` directly; `ProfileRepositoryImplTest` and `ProfileViewModelTest` instead wire a real `FakeNetwork`/`ScenarioHolder` pair, since the whole data layer here is already an in-memory fake. `FakeNetworkTest` is where `Scenario` behavior and the `generation`-based attempt-count reset are actually asserted — don't re-test that indirectly through a ViewModel.
