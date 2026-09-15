package io.github.cleitonmonteiro.requestretry.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

/**
 * A real [HttpClient] backed by a [MockEngine]: no socket ever opens, but content negotiation
 * runs for real, so [respondTo] only needs to return each request's raw JSON body by path.
 * The engine's dispatcher is forced to [Dispatchers.Unconfined] so it never hops onto a real
 * thread pool `runTest`'s virtual-time scheduler can't see through.
 */
fun mockHttpClient(respondTo: (path: String) -> String): HttpClient {
    val engineConfig = MockEngineConfig().apply {
        dispatcher = Dispatchers.Unconfined
        addHandler { request ->
            respond(
                content = respondTo(request.url.encodedPath),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
    }
    return HttpClient(MockEngine(engineConfig)) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
        }
    }
}
