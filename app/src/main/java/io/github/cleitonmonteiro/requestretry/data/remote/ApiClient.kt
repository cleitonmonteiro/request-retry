package io.github.cleitonmonteiro.requestretry.data.remote

import java.io.IOException
import javax.inject.Inject

/**
 * Wraps every real HTTP call in [Scenario]-driven failure injection: [call] always runs against
 * the real (mocked) Node server, but the result is only let through if the current [Scenario]
 * allows it — otherwise it's discarded and an [IOException] is thrown instead. This is what
 * keeps the retry/backoff demo meaningful even though the server itself never fails on its own.
 * Left **unscoped** on purpose (see `di/RepositoryModule.kt`'s scoping note).
 */
class ApiClient @Inject constructor(
    private val scenarios: ScenarioHolder,
) {
    private var lastGeneration = scenarios.generation.value
    private var attempt = 0

    suspend fun <T> execute(call: suspend () -> T): T {
        val generation = scenarios.generation.value
        if (generation != lastGeneration) {
            lastGeneration = generation
            attempt = 0
        }
        attempt++
        val result = call()
        val scenario = scenarios.scenario.value
        val shouldFail = when (scenario) {
            Scenario.ALWAYS_SUCCEED -> false
            Scenario.ALWAYS_FAIL -> true
            Scenario.SUCCEED_ON_THIRD_ATTEMPT -> attempt < 3
        }
        if (shouldFail) throw IOException("Request failed")
        return result
    }
}
