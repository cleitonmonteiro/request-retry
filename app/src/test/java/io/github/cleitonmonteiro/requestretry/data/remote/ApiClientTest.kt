package io.github.cleitonmonteiro.requestretry.data.remote

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailureException
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
    fun `execute passes the real call result through unchanged`() = runTest {
        // Act
        val result = ApiClient().execute { "payload" }

        // Assert
        assertEquals("payload", result)
    }

    @Test
    fun `unexpected throwable crosses the boundary as a typed failure`() = runTest {
        // Act
        val error = runCatching { ApiClient().execute { throw IllegalStateException("boom") } }.exceptionOrNull()

        // Assert
        assertTrue(error is RequestFailureException)
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
        val client = ApiClient()

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
