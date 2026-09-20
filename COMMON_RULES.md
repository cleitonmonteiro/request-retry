# Common Engineering Rules

These rules apply to every change in RequestRetry. Project-specific guidance in
[`AGENTS.md`](AGENTS.md) takes precedence when it is more specific.

## Architecture and SOLID

- Keep dependency direction explicit: presentation → domain ← data. The domain layer must not
  import Android, Compose, Ktor, Hilt, or DTO types.
- Use the domain layer for business models, repository contracts, and use cases. Data implements
  those contracts; presentation consumes use cases and renders immutable UI state.
- Give each type one clear responsibility. Prefer small collaborators with constructor injection
  over large classes that mix UI, networking, mapping, storage, and business rules.
- Depend on abstractions at layer boundaries. Add a repository interface or a small policy/strategy
  interface when it creates a meaningful test seam or lets behavior vary without coupling callers
  to an implementation.
- Keep shared infrastructure payload- and feature-agnostic. Do not make reusable retry, UI, or
  navigation components depend on a concrete feature model.
- Model finite business states with sealed types and exhaustive `when` expressions. Avoid boolean
  combinations or nullable fields that allow invalid states.

## MVI and Compose UI

- Each screen ViewModel exposes exactly one public immutable `StateFlow<XxxUiState>`; composables
  collect that state and do not own competing sources of truth.
- Represent user interactions as ViewModel functions/events. Composables render state and forward
  events; they do not call repositories, use cases, or networking code directly.
- Build screen state by combining internal flows in the ViewModel. Keep transient UI inputs in the
  screen state when they affect rendering or submission.
- Keep side effects explicit and lifecycle-aware: navigation, toasts, intents, and one-off effects
  belong at the presentation boundary, never in domain or data code.
- Reuse composables for common loading, feedback, and action behavior. Feature composables should
  supply content and wiring rather than duplicate state rendering.
- Hoist state and callbacks from reusable composables. Pass stable data and event lambdas instead
  of ViewModels or service objects into leaf UI components.

## Data, errors, and concurrency

- Keep DTOs wire-shaped and map them explicitly at the data boundary. Never expose DTOs to domain
  or UI code.
- Convert transport and HTTP failures into stable, typed application errors in the data layer.
  Preserve the original cause for diagnostics, but never render raw exception messages to users.
- Centralize retryability, backoff, and feedback classification in strategies/policies. A retry
  must repeat the exact request that failed, not mutable input edited afterward.
- Do not swallow exceptions unless a documented recovery path handles them. Preserve coroutine
  cancellation by rethrowing `CancellationException`.
- Do not add dispatcher switches inside repositories unless the caller and tests explicitly require
  one. Flow collection and test virtual time must remain predictable.
- Scope objects deliberately. Shared app state and expensive clients may be singletons; request or
  screen state must not accidentally outlive the feature that owns it.

## Dependency injection and dependencies

- Prefer constructor injection. Keep framework annotations at composition roots, modules, or types
  that genuinely require framework construction.
- Put bindings and lifecycle scopes in DI modules; do not use service locators or construct hidden
  production dependencies inside feature code.
- Add dependency versions to `gradle/libs.versions.toml` and consume them through catalog aliases.
  Do not hardcode dependency coordinates in module build files.
- Prefer existing platform/library capabilities before introducing a dependency. Any new dependency
  needs a clear ownership and testing reason.

## Testing and quality

- Write deterministic JVM tests at the narrowest useful seam. Use fakes and `MockEngine` rather
  than a Hilt graph, real sockets, or a mocking framework unless the test genuinely needs them.
- Test observable behavior through the public ViewModel state and public contracts, not private
  implementation details.
- Use coroutine test dispatchers and virtual time for delays. Cover success, typed failures,
  terminal states, retry exhaustion, cancellation, and state reset whenever applicable.
- Keep tests readable with arrange/act/assert structure and meaningful names. Update affected tests
  with every behavior change.
- Run the relevant unit tests and lint before handoff. Do not commit generated build outputs or
  local machine configuration.

## Code quality and safety

- Favor clear, idiomatic Kotlin: immutable values by default, concise functions, descriptive names,
  and early validation at boundaries.
- Keep public APIs minimal and documented when their lifecycle, ownership, or failure behavior is
  not obvious.
- Avoid unrelated refactors in feature changes. Preserve backward compatibility unless the change
  explicitly requires a migration.
- Treat user data, credentials, network payloads, and logs as sensitive. Never commit secrets or
  log private payloads in production code.
- Use Conventional Commits for every commit message: `type(scope): concise imperative summary`.
  Use an optional scope when it adds clarity, keep the summary lowercase, and choose a standard
  type such as `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci`, or `chore`.
