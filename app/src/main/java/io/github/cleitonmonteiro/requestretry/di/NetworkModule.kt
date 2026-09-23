package io.github.cleitonmonteiro.requestretry.di

import io.github.cleitonmonteiro.requestretry.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.cleitonmonteiro.requestretry.data.observability.DebugLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import java.util.UUID

@Module
@InstallIn(SingletonComponent::class)
/** Supplies the shared HTTP client with correlation IDs and safe debug logging. */
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(debugLogger: DebugLogger): HttpClient = HttpClient(OkHttp) {
        expectSuccess = true
        engine {
            // RetryExecutor is the only retry owner; OkHttp must never replay a request by itself.
            config { retryOnConnectionFailure(false) }
        }
        defaultRequest {
            headers.append("X-Correlation-ID", UUID.randomUUID().toString())
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    debugLogger.d("ApiClient", message)
                }
            }
            sanitizeHeader { header ->
                header.equals("Authorization", ignoreCase = true) ||
                    header.equals("Cookie", ignoreCase = true) ||
                    header.equals("Set-Cookie", ignoreCase = true) ||
                    header.equals("Idempotency-Key", ignoreCase = true)
            }
            level = if (BuildConfig.DEBUG) LogLevel.HEADERS else LogLevel.NONE
        }
    }
}
