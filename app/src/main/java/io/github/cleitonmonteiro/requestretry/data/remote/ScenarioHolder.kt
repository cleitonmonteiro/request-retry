package io.github.cleitonmonteiro.requestretry.data.remote

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which way [FakeNetwork] should behave, so a screen's demo can be driven from the UI. */
enum class Scenario {
    ALWAYS_SUCCEED,
    ALWAYS_FAIL,
    SUCCEED_ON_THIRD_ATTEMPT,
}

/** Shared, app-wide demo knob: every screen's [FakeNetwork] reads the same scenario. */
@Singleton
class ScenarioHolder @Inject constructor() {

    private val _scenario = MutableStateFlow(Scenario.ALWAYS_SUCCEED)
    val scenario: StateFlow<Scenario> = _scenario.asStateFlow()

    /**
     * Bumped on every [select] call, even a reselection of the current [Scenario]. [FakeNetwork]
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
