package io.github.cleitonmonteiro.requestretry.data.remote

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which way [ApiClient] should behave, so a screen's demo can be driven from the UI. */
enum class Scenario {
    ALWAYS_SUCCEED,
    CONNECTION_ERROR,
    /** Backwards-compatible alias for the original failure demo. */
    ALWAYS_FAIL,
    HTTP_400,
    HTTP_404,
    HTTP_422,
    HTTP_500,
    SUCCEED_ON_THIRD_ATTEMPT,
    /** Simulates a request that never left the client — exercises the [io.github.cleitonmonteiro.requestretry.domain.error.OutcomeCertainty.NOT_SENT] path. */
    TIMEOUT,
    /** Simulates a `429` with `Retry-After` — exercises [io.github.cleitonmonteiro.requestretry.retry.RetryReason.RETRY_AFTER_HEADER]. */
    RATE_LIMITED,
}

/** Shared, app-wide demo knob: every screen's [ApiClient] reads the same scenario. */
@Singleton
class ScenarioHolder @Inject constructor() {

    private val _scenario = MutableStateFlow(Scenario.ALWAYS_SUCCEED)
    val scenario: StateFlow<Scenario> = _scenario.asStateFlow()

    /**
     * Bumped on every [select] call, even a reselection of the current [Scenario]. [ApiClient]
     * watches this — not [scenario]'s value — to know when to restart its attempt count, so
     * re-tapping the same scenario always starts the demo over.
     */
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation.asStateFlow()

    fun select(scenario: Scenario) {
        _scenario.value = scenario
        _generation.value++
    }
}
