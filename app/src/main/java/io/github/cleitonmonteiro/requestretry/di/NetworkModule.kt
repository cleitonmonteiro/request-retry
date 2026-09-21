package io.github.cleitonmonteiro.requestretry.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.cleitonmonteiro.requestretry.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Study-project defaults, generous enough to never trip by accident — a production
    // calibration needs real latency percentiles (see plan §12.1). RetryExecutor's own
    // per-attempt timeout is the value that actually matters for retry semantics; these just
    // make sure the socket itself doesn't hang past that.
    private const val CONNECT_TIMEOUT_MS = 8_000L
    private const val SOCKET_TIMEOUT_MS = 15_000L
    private const val REQUEST_TIMEOUT_MS = 15_000L

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = true
        engine {
            // RetryExecutor is the single owner of retries (plan §12.2) — the OkHttp engine's
            // own connection-failure retry would otherwise double-count attempts and hide real
            // network flakiness from RetryController's budget.
            config {
                retryOnConnectionFailure(false)
            }
        }
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
        }
        install(ContentNegotiation) {
            // coerceInputValues: an unrecognized enum value (e.g. a future ActionTypeDto the
            // server added) decodes to its default instead of throwing — see ActionDto's doc.
            json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
        }
        // Headers and bodies only ever get logged in a debug build — see plan §12.4. Release
        // logging is fully off rather than sanitized: this demo doesn't carry secrets worth
        // redacting individually, and "off in release" is the simpler, safer default.
        if (BuildConfig.DEBUG) {
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.d("ApiClient", message)
                    }
                }
                level = LogLevel.ALL
            }
        }
    }
}
