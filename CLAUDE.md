# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

RequestRetry is a study case for the request-retry-with-backoff pattern in Jetpack Compose, built on top of Android Studio's default "Empty Activity" template. There is no real networking — every screen calls a mocked API and demonstrates a Loading/Success/Feedback state machine with manual, budgeted retries.

- Package / namespace / application ID: `io.github.cleitonmonteiro.requestretry`
- Single module: `app`
- UI toolkit: Jetpack Compose (Material 3) + Navigation Compose, no XML layouts
- Min SDK 26, target/compile SDK 37
- Kotlin 2.2.10, AGP 9.4.0-alpha05

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

- `retry/` is the reusable core every demo screen shares, and the reason two features exist at all — it's payload-agnostic (generic in `T`):
  - `RetryPolicy` — a `fun interface` for computing the delay before a retry attempt; `ExponentialBackoffPolicy` is the default implementation (doubling delay, capped, with jitter, `Random` injected for testability).
  - `RetryUiState<T>` — the sealed `Loading` / `Success` / `Feedback` state machine. `Loading.backoffSecondsRemaining` carries the countdown; `Feedback.canRetry` goes false once the 3-attempt budget is spent.
  - `RetryController<T>` — owns the attempt counter, the in-flight `Job`, and a `StateFlow<RetryUiState<T>>`. `load()` resets the budget; `retry()` is a no-op once exhausted. A ViewModel *composes* a controller (via `viewModelScope`) rather than subclassing anything.
  - `ApiCall<T>` — the `suspend operator fun invoke(): T` contract mocked calls implement.
- `data/FakeApi.kt` is the mocked backend: no networking, an artificial `delay`, and a settable `Scenario` (`ALWAYS_SUCCEED` / `ALWAYS_FAIL` / `SUCCEED_ON_THIRD_ATTEMPT`) so any screen can be driven into either path from the UI.
- `ui/components/RetryStateScaffold.kt` is the UI half of the reuse: it renders Loading and Feedback itself and only delegates the Success branch to the caller, so a feature screen's own composable is just the success-case layout plus wiring.
- `feature/profile/` and `feature/orders/` are two independent screens over different payloads (`UserProfile`, `List<Order>`) that each wire a thin ViewModel (owns one `RetryController`) to `RetryStateScaffold` — they're intentionally near-identical, since that similarity is what demonstrates the core is actually shared. `feature/home/` is the launcher screen.
- `navigation/AppNavHost.kt` wires `home` → `profile` / `orders` with Navigation Compose. Each destination's ViewModel is scoped to its `NavBackStackEntry` via the default `viewModel()` factory, so popping back and re-entering a demo always starts with a fresh retry budget.
- Dependency versions are centralized in `gradle/libs.versions.toml` (Gradle version catalog) and referenced from `app/build.gradle.kts` via `libs.*` aliases — add new dependencies there rather than hardcoding coordinates in the module build file.
- `release` build type currently has optimization (minify/shrink) disabled in `app/build.gradle.kts`.

## Testing patterns already established

- JVM unit tests only (`app/src/test`), plain JUnit4 assertions — no mocking library or assertion library beyond JUnit is a dependency yet; write fakes (like `FakeApi`) instead of mocks.
- `MainDispatcherRule` (`app/src/test/.../MainDispatcherRule.kt`) points `Dispatchers.Main` at a `StandardTestDispatcher` for any test touching a `ViewModel`'s `viewModelScope`; not needed when a `RetryController` is constructed directly with `scope = this` inside `runTest`.
- Backoff delays are real `delay()` calls under a `StandardTestDispatcher`, so tests use `advanceUntilIdle()` (or Turbine's `test { }` on `RetryController.state`, per `RetryControllerTest`) to fast-forward through them deterministically rather than waiting in real time.
