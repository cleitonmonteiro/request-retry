package io.github.cleitonmonteiro.requestretry.data.remote

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailureException
import java.io.IOException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiClientTest {

    @Test
    fun `execute succeeds immediately under ALWAYS_SUCCEED`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.ALWAYS_SUCCEED) }
        val client = ApiClient(scenarios)

        // Act
        val result = client.execute { "payload" }

        // Assert
        assertEquals("payload", result)
    }

    @Test
    fun `execute always fails under ALWAYS_FAIL`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.ALWAYS_FAIL) }
        val client = ApiClient(scenarios)

        // Act
        val thrown = runCatching { client.execute { "payload" } }.exceptionOrNull()

        // Assert
        assertTrue(thrown is IOException)
    }

    @Test
    fun `execute succeeds only from the third call under SUCCEED_ON_THIRD_ATTEMPT`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.SUCCEED_ON_THIRD_ATTEMPT) }
        val client = ApiClient(scenarios)

        // Act
        val firstAttempt = runCatching { client.execute { "payload" } }.exceptionOrNull()
        val secondAttempt = runCatching { client.execute { "payload" } }.exceptionOrNull()
        val thirdAttempt = client.execute { "payload" }

        // Assert
        assertTrue(firstAttempt is IOException)
        assertTrue(secondAttempt is IOException)
        assertEquals("payload", thirdAttempt)
    }

    @Test
    fun `re-selecting the same scenario restarts the attempt count`() = runTest {
        // Arrange: burn two attempts of SUCCEED_ON_THIRD_ATTEMPT, one short of success
        val scenarios = ScenarioHolder().apply { select(Scenario.SUCCEED_ON_THIRD_ATTEMPT) }
        val client = ApiClient(scenarios)
        runCatching { client.execute { "payload" } }
        runCatching { client.execute { "payload" } }

        // Act: re-tapping the same scenario chip should start the demo over
        scenarios.select(Scenario.SUCCEED_ON_THIRD_ATTEMPT)
        val afterReselect = runCatching { client.execute { "payload" } }.exceptionOrNull()

        // Assert
        assertTrue(afterReselect is IOException)
    }

    @Test
    fun `simulated HTTP status crosses the data boundary as typed failure`() = runTest {
        val scenarios = ScenarioHolder().apply { select(Scenario.HTTP_403) }
        val error = runCatching { ApiClient(scenarios).execute { "payload" } }.exceptionOrNull()

        assertTrue(error is RequestFailureException)
        assertEquals(RequestFailure.Http(403), (error as RequestFailureException).failure)
    }

    @Test
    fun `real HTTP failure captures backend code request id and retry-after`() = runTest {
        val http = HttpClient(MockEngine) {
            expectSuccess = true
            engine {
                addHandler {
                    respond(
                        content = """{"error_code":"LIMITED"}""",
                        status = HttpStatusCode.TooManyRequests,
                        headers = headersOf(
                            HttpHeaders.ContentType to listOf("application/json"),
                            HttpHeaders.RetryAfter to listOf("7"),
                            "X-Request-ID" to listOf("request-1"),
                        ),
                    )
                }
            }
        }
        val client = ApiClient(ScenarioHolder())

        val error = runCatching {
            client.executeHttp { http.get("https://example.test").bodyAsText() }
        }.exceptionOrNull() as RequestFailureException

        assertEquals(
            RequestFailure.Http(
                statusCode = 429,
                backendCode = "LIMITED",
                retryAfter = 7.seconds,
                requestId = "request-1",
            ),
            error.failure,
        )
        http.close()
    }
}
