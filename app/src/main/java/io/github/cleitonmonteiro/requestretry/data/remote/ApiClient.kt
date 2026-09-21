package io.github.cleitonmonteiro.requestretry.data.remote

import io.github.cleitonmonteiro.requestretry.domain.error.OutcomeCertainty
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailureException
import io.github.cleitonmonteiro.requestretry.domain.error.TimeoutStage
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Wraps every real HTTP call in [Scenario]-driven failure injection: [execute] always runs
 * against the real (mocked) Node server, but the result is only let through if the current
 * [Scenario] allows it — otherwise it's discarded and a [RequestFailureException] is thrown
 * instead. This is what keeps the retry/backoff demo meaningful even though the server itself
 * never fails on its own. Left **unscoped** on purpose (see `di/RepositoryModule.kt`'s scoping
 * note).
 *
 * Every simulated failure here reports [OutcomeCertainty.MAY_HAVE_REACHED_SERVER] — deliberately
 * conservative, and actually true: the real request above already completed by the time the
 * scenario check runs, so it *did* reach the server. [Scenario.TIMEOUT] is the one exception,
 * simulating a request that never got that far, to exercise the [OutcomeCertainty.NOT_SENT] path.
 */
class ApiClient @Inject constructor(
    private val scenarios: ScenarioHolder,
) {
    private var lastGeneration = scenarios.generation.value
    private var attempt = 0

    private val errorBodyJson = Json { ignoreUnknownKeys = true }

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
            Scenario.TIMEOUT, Scenario.RATE_LIMITED -> true
        }
        if (simulatedError) {
            throw when (scenario) {
                Scenario.CONNECTION_ERROR, Scenario.ALWAYS_FAIL, Scenario.SUCCEED_ON_THIRD_ATTEMPT ->
                    RequestFailureException(RequestFailure.Connection(OutcomeCertainty.MAY_HAVE_REACHED_SERVER))
                Scenario.HTTP_400 -> RequestFailureException(RequestFailure.Http(400))
                Scenario.HTTP_404 -> RequestFailureException(RequestFailure.Http(404))
                Scenario.HTTP_422 -> RequestFailureException(
                    RequestFailure.Http(422, message = "The submitted data didn't pass server-side validation."),
                )
                Scenario.HTTP_500 -> RequestFailureException(RequestFailure.Http(500))
                Scenario.TIMEOUT ->
                    RequestFailureException(RequestFailure.Timeout(TimeoutStage.CONNECT, OutcomeCertainty.NOT_SENT))
                Scenario.RATE_LIMITED ->
                    RequestFailureException(RequestFailure.Http(429, retryAfter = 2.seconds))
                Scenario.ALWAYS_SUCCEED -> error("unreachable")
            }
        }
        result
    } catch (error: RequestFailureException) {
        throw error
    } catch (error: ResponseException) {
        // The mock server's error bodies are always {"error": "..."} (see server/index.js) — a
        // real 422's validation reason genuinely comes from the server, so it's read here rather
        // than replaced with client-side copy. A body that doesn't parse just leaves message null.
        val message = runCatching {
            errorBodyJson.decodeFromString<ErrorBodyDto>(error.response.bodyAsText()).error
        }.getOrNull()
        throw RequestFailureException(RequestFailure.Http(error.response.status.value, message = message), error)
    } catch (error: SerializationException) {
        throw RequestFailureException(RequestFailure.Protocol(error.message ?: "serialization"), error)
    } catch (error: IOException) {
        throw RequestFailureException(RequestFailure.Connection(OutcomeCertainty.MAY_HAVE_REACHED_SERVER), error)
    }

    suspend fun <T> executeHttp(call: suspend () -> T): T = execute(call)
}
