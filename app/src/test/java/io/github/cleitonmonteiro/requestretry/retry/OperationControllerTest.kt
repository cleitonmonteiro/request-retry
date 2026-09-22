@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.model.OperationId
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailureException
import kotlin.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
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
            spec = OperationProfiles.foregroundIdempotentCommand(
                OperationName("create_order"),
                OperationId("command"),
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
                OperationProfiles.foregroundRead(
                    OperationName("profile_read"),
                    OperationId("snapshot"),
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
            spec = OperationProfiles.foregroundRead(
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
    fun `ambiguous command exposes verification and never repeats mutation`() = runTest {
        var mutations = 0
        var verifications = 0
        val controller = OperationController(
            scope = backgroundScope,
            specFactory = OperationSpecFactory<String> {
                OperationProfiles.foregroundIdempotentCommand(
                    OperationName("create_order"),
                    OperationId("unknown"),
                    backoff = FixedBackoff(Duration.ZERO),
                ).copy(maxAttempts = 1)
            },
            executor = RetryExecutor(),
            call = OneShotCall { _, _ ->
                mutations++
                throw RequestFailureException(
                    RequestFailure.Connection(mayHaveReachedServer = true),
                )
            },
            verifier = StatusVerifier {
                verifications++
                VerificationResult.Confirmed("confirmed")
            },
        )

        controller.start("snapshot")
        runCurrent()
        assertTrue(controller.state.value is OperationState.OutcomeUnknown)

        controller.verifyStatus()
        runCurrent()
        assertEquals("confirmed", (controller.state.value as OperationState.Succeeded).data)
        assertEquals(1, mutations)
        assertEquals(1, verifications)
    }

    @Test
    fun `concurrent manual retries spend only one new execution`() = runTest {
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val spec = OperationProfiles.foregroundRead(
            OperationName("profile_read"),
            OperationId("manual-retry"),
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
        val spec = OperationProfiles.foregroundRead(
            OperationName("profile_read"),
            OperationId("manual-limit"),
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
