package io.github.cleitonmonteiro.requestretry.data.remote

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailureException
import kotlinx.coroutines.CancellationException
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

    suspend fun <T> execute(call: suspend () -> T): T = try {
        val generation = scenarios.generation.value
        if (generation != lastGeneration) {
            lastGeneration = generation
            attempt = 0
        }
        attempt++
        val result = call()
        val scenario = scenarios.scenario.value
        val simulatedError = when (scenario) {
            Scenario.ALWAYS_SUCCEED -> false
            Scenario.CONNECTION_ERROR, Scenario.HTTP_400, Scenario.HTTP_429, Scenario.HTTP_503 -> true
            Scenario.SUCCEED_ON_THIRD_ATTEMPT -> attempt < 3
        }
        if (simulatedError) {
            throw when (scenario) {
                Scenario.CONNECTION_ERROR, Scenario.SUCCEED_ON_THIRD_ATTEMPT ->
                    RequestFailureException(RequestFailure.Connection)
                Scenario.HTTP_400 -> RequestFailureException(RequestFailure.Http(400))
                Scenario.HTTP_429 -> RequestFailureException(RequestFailure.Http(429, retryAfter = kotlin.time.Duration.ZERO))
                Scenario.HTTP_503 -> RequestFailureException(RequestFailure.Http(503))
                Scenario.ALWAYS_SUCCEED -> error("unreachable")
            }
        }
        result
    } catch (error: CancellationException) {
        throw error
    } catch (error: Error) {
        throw error
    } catch (error: Throwable) {
        throw error.toRequestFailureException()
    }

    suspend fun <T> executeHttp(call: suspend () -> T): T = execute(call)
}
