package io.github.cleitonmonteiro.requestretry.data

import io.github.cleitonmonteiro.requestretry.retry.ApiCall
import java.io.IOException
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** Which way [FakeApi] should behave, so a screen's demo can be driven from the UI. */
enum class Scenario {
    ALWAYS_SUCCEED,
    ALWAYS_FAIL,
    SUCCEED_ON_THIRD_ATTEMPT,
}

/**
 * A mocked network call standing in for a real request: no networking happens, but it
 * suspends briefly (so Loading is visible) and can be told to fail via [scenario].
 */
class FakeApi<T>(
    private val payload: () -> T,
) : ApiCall<T> {

    var scenario: Scenario = Scenario.ALWAYS_SUCCEED
        set(value) {
            field = value
            attempt = 0
        }
    private var attempt = 0

    override suspend fun invoke(): T {
        attempt++
        delay(600.milliseconds)
        val shouldFail = when (scenario) {
            Scenario.ALWAYS_SUCCEED -> false
            Scenario.ALWAYS_FAIL -> true
            Scenario.SUCCEED_ON_THIRD_ATTEMPT -> attempt < 3
        }
        if (shouldFail) throw IOException("Request failed")
        return payload()
    }
}
