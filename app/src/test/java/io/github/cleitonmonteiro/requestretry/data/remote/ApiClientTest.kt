package io.github.cleitonmonteiro.requestretry.data.remote

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailureException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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
    fun `execute carries a server-authored message for a simulated 422`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.HTTP_422) }
        val client = ApiClient(scenarios)

        // Act
        val thrown = runCatching { client.execute { "payload" } }.exceptionOrNull() as RequestFailureException
        val failure = thrown.failure as RequestFailure.Http

        // Assert
        assertEquals(422, failure.statusCode)
        assertTrue("expected a non-blank server message", failure.message?.isNotBlank() == true)
    }

    @Test
    fun `execute reads the real server's error body for a real 422 response`() = runTest {
        // Arrange: a real HttpClient backed by a MockEngine returning the mock server's actual
        // error shape ({"error": "..."}, see server/index.js) — not a scenario simulation.
        val engineConfig = MockEngineConfig().apply {
            dispatcher = Dispatchers.Unconfined
            addHandler {
                respond(
                    content = """{"error":"item_name is required"}""",
                    status = HttpStatusCode.UnprocessableEntity,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val httpClient = HttpClient(MockEngine(engineConfig)) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val scenarios = ScenarioHolder().apply { select(Scenario.ALWAYS_SUCCEED) }
        val client = ApiClient(scenarios)

        // Act
        val thrown = runCatching {
            client.execute { httpClient.get("http://localhost/orders") }
        }.exceptionOrNull() as RequestFailureException
        val failure = thrown.failure as RequestFailure.Http

        // Assert: the message is the real server's text, not a client-side default
        assertEquals(422, failure.statusCode)
        assertEquals("item_name is required", failure.message)
    }
}
