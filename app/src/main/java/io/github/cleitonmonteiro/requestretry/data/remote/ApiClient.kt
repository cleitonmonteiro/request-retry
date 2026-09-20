package io.github.cleitonmonteiro.requestretry.data.remote

import io.github.cleitonmonteiro.requestretry.domain.error.RequestException
import io.ktor.client.plugins.ResponseException
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
            Scenario.CONNECTION_ERROR, Scenario.ALWAYS_FAIL -> true
            Scenario.HTTP_400, Scenario.HTTP_404, Scenario.HTTP_422, Scenario.HTTP_500 -> true
            Scenario.SUCCEED_ON_THIRD_ATTEMPT -> attempt < 3
        }
        if (simulatedError) {
            throw when (scenario) {
                Scenario.CONNECTION_ERROR, Scenario.ALWAYS_FAIL, Scenario.SUCCEED_ON_THIRD_ATTEMPT -> RequestException.Connection()
                Scenario.HTTP_400 -> RequestException.Http(400)
                Scenario.HTTP_404 -> RequestException.Http(404)
                Scenario.HTTP_422 -> RequestException.Http(422)
                Scenario.HTTP_500 -> RequestException.Http(500)
                Scenario.ALWAYS_SUCCEED -> error("unreachable")
            }
        }
        result
    } catch (error: RequestException) {
        throw error
    } catch (error: ResponseException) {
        throw RequestException.Http(error.response.status.value, error)
    } catch (error: IOException) {
        throw RequestException.Connection(error)
    }

    suspend fun <T> executeHttp(call: suspend () -> T): T = execute(call)
}
