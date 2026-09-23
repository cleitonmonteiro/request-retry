@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailureException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationControllerTest {
    @Test
    fun `concurrent starts cancel previous executions`() = runTest {
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val controller = controller(
            scope = backgroundScope,
            spec = OperationProfiles.foreground(
                OperationName("create_order"),
                backoff = FixedBackoff(Duration.ZERO),
            ),
        ) { input ->
            calls++
            release.await()
            input
        }

        val starts = List(20) { index -> async { controller.start("payload-$index") } }
        starts.forEach { it.await() }
        runCurrent()
        assertEquals(1, calls)

        release.complete(Unit)
        runCurrent()
        val success = controller.state.value as OperationState.Succeeded
        assertEquals("payload-19", success.data)
    }

    @Test
    fun `session input is an immutable snapshot`() = runTest {
        data class Input(val value: String)
        var observed: Input? = null
        val controller = OperationController(
            scope = backgroundScope,
            specFactory = OperationSpecFactory<Input> {
                OperationProfiles.foreground(
                    OperationName("profile_read"),
                    FixedBackoff(Duration.ZERO),
                ).copy(maxAttempts = 1)
            },
            executor = RetryExecutor(),
            call = OneShotCall { input, _ -> observed = input; input.value },
        )
        val submitted = Input("original")

        controller.start(submitted)
        runCurrent()

        assertEquals(Input("original"), observed)
        assertEquals("original", (controller.state.value as OperationState.Succeeded).data)
    }

    @Test
    fun `old result never replaces a newer cancel-previous session`() = runTest {
        val firstRelease = CompletableDeferred<Unit>()
        val controller = controller(
            scope = backgroundScope,
            spec = OperationProfiles.foreground(
                OperationName("profile_read"),
                backoff = FixedBackoff(Duration.ZERO),
            ),
        ) { input ->
            if (input == "first") firstRelease.await()
            input
        }

        controller.start("first")
        runCurrent()
        controller.start("second")
        runCurrent()
        firstRelease.complete(Unit)
        runCurrent()

        assertEquals("second", (controller.state.value as OperationState.Succeeded).data)
    }

    @Test
    fun `concurrent manual retries spend only one new execution`() = runTest {
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val spec = OperationProfiles.foreground(
            OperationName("profile_read"),
            FixedBackoff(Duration.ZERO),
        ).copy(maxAttempts = 3)
        val controller = controller(backgroundScope, spec) { input ->
            calls++
            if (calls == 1) throw RequestFailureException(RequestFailure.Http(500))
            release.await()
            input
        }
        controller.start("snapshot")
        runCurrent()
        assertTrue(controller.state.value is OperationState.Failed)

        List(20) { async { controller.retry() } }.forEach { it.await() }
        runCurrent()
        assertEquals(2, calls)

        release.complete(Unit)
        runCurrent()
        assertEquals("snapshot", (controller.state.value as OperationState.Succeeded).data)
    }

    @Test
    fun `third transient failure ends the manual retry session`() = runTest {
        val spec = OperationProfiles.foreground(
            OperationName("profile_read"),
            FixedBackoff(Duration.ZERO),
        ).copy(maxAttempts = 3)
        val controller = controller(backgroundScope, spec) {
            throw RequestFailureException(RequestFailure.Http(500))
        }

        controller.start("snapshot")
        runCurrent()
        controller.retry()
        runCurrent()
        controller.retry()
        runCurrent()

        val failed = controller.state.value as OperationState.Failed
        assertEquals(3, failed.attemptsUsed)
        assertEquals(RecoveryAction.Leave, failed.recovery)
    }

    @Test
    fun `retry waits out the cooldown before firing the next call`() = runTest {
        var calls = 0
        val spec = OperationProfiles.foreground(
            OperationName("profile_read"),
            FixedBackoff(3.seconds),
        ).copy(maxAttempts = 3)
        val controller = controller(backgroundScope, spec) {
            calls++
            if (calls == 1) throw RequestFailureException(RequestFailure.Http(500))
            it
        }

        controller.start("snapshot")
        runCurrent()
        assertTrue(controller.state.value is OperationState.Failed)
        assertEquals(1, calls)

        controller.retry()
        runCurrent()
        assertEquals(2, (controller.state.value as OperationState.Running).attempt)
        assertEquals(1, calls)

        advanceTimeBy(3.seconds)
        runCurrent()
        assertEquals(2, calls)
        assertEquals("snapshot", (controller.state.value as OperationState.Succeeded).data)
    }

    @Test
    fun `starting fresh cancels a pending post-retry cooldown`() = runTest {
        var calls = 0
        val spec = OperationProfiles.foreground(
            OperationName("profile_read"),
            FixedBackoff(3.seconds),
        ).copy(maxAttempts = 3)
        val controller = controller(backgroundScope, spec) { input ->
            calls++
            if (calls == 1) throw RequestFailureException(RequestFailure.Http(500))
            input
        }

        controller.start("first")
        runCurrent()
        controller.retry()
        runCurrent()
        assertEquals(2, (controller.state.value as OperationState.Running).attempt)
        assertEquals(1, calls)

        controller.start("second")
        runCurrent()

        assertEquals("second", (controller.state.value as OperationState.Succeeded).data)
        assertEquals(2, calls)
    }

    @Test
    fun `manual retry after exhausted offline failure has no pending cooldown`() = runTest {
        var calls = 0
        val spec = OperationProfiles.foreground(
            OperationName("profile_read"),
            FixedBackoff(3.seconds),
        ).copy(maxAttempts = 2)
        val controller = controller(backgroundScope, spec) { input ->
            calls++
            if (calls == 1) throw RequestFailureException(RequestFailure.Http(500))
            if (calls == 2) throw RequestFailureException(RequestFailure.Offline)
            input
        }

        controller.start("snapshot")
        runCurrent()
        controller.retry()
        advanceTimeBy(3.seconds)
        runCurrent()

        val failed = controller.state.value as OperationState.Failed
        assertEquals(RecoveryAction.Retry, failed.recovery)
        assertEquals(2, failed.attemptsUsed)

        controller.retry()
        runCurrent()

        assertEquals("snapshot", (controller.state.value as OperationState.Succeeded).data)
        assertEquals(3, calls)
    }

    private fun controller(
        scope: CoroutineScope,
        spec: OperationSpec,
        call: suspend (String) -> String,
    ): OperationController<String, String> = OperationController(
        scope = scope,
        specFactory = OperationSpecFactory { spec },
        executor = RetryExecutor(),
        call = OneShotCall { input, _ -> call(input) },
    )
}
