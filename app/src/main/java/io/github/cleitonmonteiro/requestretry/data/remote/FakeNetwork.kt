package io.github.cleitonmonteiro.requestretry.data.remote

import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * A mocked network call standing in for a real request: no networking happens, but it
 * suspends briefly (so Loading is visible) and can be told to fail via [ScenarioHolder].
 */
class FakeNetwork @Inject constructor(
    private val scenarios: ScenarioHolder,
) {
    private var lastGeneration = scenarios.generation.value
    private var attempt = 0

    suspend fun <T> execute(payload: () -> T): T {
        val generation = scenarios.generation.value
        if (generation != lastGeneration) {
            lastGeneration = generation
            attempt = 0
        }
        attempt++
        val scenario = scenarios.scenario.value
        delay(600.milliseconds)
        val shouldFail = when (scenario) {
            Scenario.ALWAYS_SUCCEED -> false
            Scenario.ALWAYS_FAIL -> true
            Scenario.SUCCEED_ON_THIRD_ATTEMPT -> attempt < 3
        }
        if (shouldFail) throw IOException("Request failed")
        return payload()
    }
}
