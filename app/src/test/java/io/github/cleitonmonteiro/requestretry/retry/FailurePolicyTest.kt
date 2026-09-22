package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.model.OperationId
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FailurePolicyTest {
    private val readSpec = OperationProfiles.foregroundRead(
        OperationName("profile_read"),
        OperationId("read"),
        FixedBackoff(1.seconds),
    )

    @Test
    fun `4xx failures are terminal by default`() {
        listOf(400, 401, 403, 404, 409, 422).forEach { status ->
            val classified = DefaultFailureClassifier.classify(
                io.github.cleitonmonteiro.requestretry.domain.error.RequestFailureException(RequestFailure.Http(status)),
            )
            val decision = ConservativeRetryDecider.decide(RetryContext(readSpec, classified, attempt = 1))
            assertTrue("HTTP $status must stop", decision is RetryDecision.Stop)
            assertEquals(RecoveryAction.Leave, (decision as RetryDecision.Stop).recovery)
        }
    }

    @Test
    fun `only explicit transient status codes retry`() {
        listOf(408, 425, 429, 500, 502, 503, 504).forEach { status ->
            val decision = ConservativeRetryDecider.decide(
                RetryContext(readSpec, RequestFailure.Http(status), attempt = 1),
            )
            assertTrue("HTTP $status must retry", decision is RetryDecision.Retry)
        }
        assertTrue(
            ConservativeRetryDecider.decide(
                RetryContext(readSpec, RequestFailure.Http(501), attempt = 1),
            ) is RetryDecision.Stop,
        )
    }

    @Test
    fun `unknown failure fails closed`() {
        val decision = ConservativeRetryDecider.decide(
            RetryContext(readSpec, RequestFailure.Unknown, attempt = 1),
        )
        assertEquals(
            RetryDecision.Stop(PublicFailure.Unknown, RecoveryAction.Leave),
            decision,
        )
    }

    @Test
    fun `ambiguous idempotent command verifies instead of retrying`() {
        val spec = OperationProfiles.foregroundIdempotentCommand(
            name = OperationName("create_order"),
            operationId = OperationId("command"),
            backoff = FixedBackoff(1.seconds),
        ).copy(maxAttempts = 1)
        val failure = RequestFailure.Connection(mayHaveReachedServer = true)

        assertEquals(
            RetryDecision.VerifyStatus,
            ConservativeRetryDecider.decide(RetryContext(spec, failure, attempt = 1)),
        )
    }
}
