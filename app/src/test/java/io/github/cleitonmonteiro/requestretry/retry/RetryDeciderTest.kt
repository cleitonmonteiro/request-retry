package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.OutcomeCertainty
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.TimeoutStage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Table-driven coverage of the decision matrix in `planos/plano-basico-evolucao-retry.md` §7 —
 * every case here is deliberately spelled out rather than looped over, so a missing/wrong row is
 * obvious from the test name and the failure message alone.
 */
class RetryDeciderTest {

    private val read = OperationSpec.read(OperationName.DEMO, maxAttempts = 3)
    private val command = OperationSpec.command(OperationName.DEMO, maxAttempts = 3)

    @Test
    fun `offline is always retryable`() {
        val decision = DefaultRetryDecider.decide(RequestFailure.Offline, read, attempt = 1)
        assertEquals(RecoveryAction.Retry, decision.recovery)
        assertEquals(PublicFailure.OFFLINE, decision.publicFailure)
    }

    @Test
    fun `NOT_SENT connection failure is retryable for reads and commands alike`() {
        val failure = RequestFailure.Connection(OutcomeCertainty.NOT_SENT)
        assertEquals(RecoveryAction.Retry, DefaultRetryDecider.decide(failure, read, 1).recovery)
        assertEquals(RecoveryAction.Retry, DefaultRetryDecider.decide(failure, command, 1).recovery)
    }

    @Test
    fun `MAY_HAVE_REACHED_SERVER connection failure is retryable for reads only, ambiguous for commands`() {
        val failure = RequestFailure.Connection(OutcomeCertainty.MAY_HAVE_REACHED_SERVER)
        assertEquals(RecoveryAction.Retry, DefaultRetryDecider.decide(failure, read, 1).recovery)

        val commandDecision = DefaultRetryDecider.decide(failure, command, 1)
        assertEquals(RecoveryAction.ContactSupport, commandDecision.recovery)
        assertEquals(PublicFailure.AMBIGUOUS_MUTATION, commandDecision.publicFailure)
    }

    @Test
    fun `an overall deadline timeout is always terminal, regardless of certainty`() {
        val failure = RequestFailure.Timeout(TimeoutStage.OVERALL, OutcomeCertainty.NOT_SENT)
        val decision = DefaultRetryDecider.decide(failure, read, attempt = 1)
        assertEquals(RecoveryAction.GoBack, decision.recovery)
    }

    @Test
    fun `400, 401, 403, 404, 409 and 422 are always terminal`() {
        listOf(400, 401, 403, 404, 409, 422).forEach { status ->
            val decision = DefaultRetryDecider.decide(RequestFailure.Http(status), read, attempt = 1)
            assertEquals("status $status should be terminal", false, decision.recovery == RecoveryAction.Retry)
        }
    }

    @Test
    fun `401 and 403 map to Authenticate`() {
        assertEquals(RecoveryAction.Authenticate, DefaultRetryDecider.decide(RequestFailure.Http(401), read, 1).recovery)
        assertEquals(RecoveryAction.Authenticate, DefaultRetryDecider.decide(RequestFailure.Http(403), read, 1).recovery)
    }

    @Test
    fun `422 maps to EditInput and VALIDATION`() {
        val decision = DefaultRetryDecider.decide(RequestFailure.Http(422), read, attempt = 1)
        assertEquals(RecoveryAction.EditInput, decision.recovery)
        assertEquals(PublicFailure.VALIDATION, decision.publicFailure)
    }

    @Test
    fun `422's server message is threaded through to the decision`() {
        val failure = RequestFailure.Http(422, message = "quantity must be a positive number")
        val decision = DefaultRetryDecider.decide(failure, read, attempt = 1)
        assertEquals("quantity must be a positive number", decision.serverMessage)
    }

    @Test
    fun `a failure with no server message decides a null serverMessage`() {
        assertEquals(null, DefaultRetryDecider.decide(RequestFailure.Http(422), read, 1).serverMessage)
        assertEquals(null, DefaultRetryDecider.decide(RequestFailure.Offline, read, 1).serverMessage)
    }

    @Test
    fun `408, 425 and 429 are retryable regardless of operation kind`() {
        listOf(408, 425, 429).forEach { status ->
            val decision = DefaultRetryDecider.decide(RequestFailure.Http(status), command, attempt = 1)
            assertEquals("status $status should be retryable", RecoveryAction.Retry, decision.recovery)
        }
    }

    @Test
    fun `5xx is retryable for reads, ambiguous for commands`() {
        val failure = RequestFailure.Http(500)
        assertEquals(RecoveryAction.Retry, DefaultRetryDecider.decide(failure, read, 1).recovery)

        val decision = DefaultRetryDecider.decide(failure, command, 1)
        assertEquals(RecoveryAction.ContactSupport, decision.recovery)
        assertEquals(PublicFailure.AMBIGUOUS_MUTATION, decision.publicFailure)
    }

    @Test
    fun `Protocol and Unknown failures are always terminal`() {
        assertEquals(false, DefaultRetryDecider.decide(RequestFailure.Protocol("bad-json"), read, 1).recovery == RecoveryAction.Retry)
        assertEquals(false, DefaultRetryDecider.decide(RequestFailure.Unknown("?"), read, 1).recovery == RecoveryAction.Retry)
    }

    @Test
    fun `a retryable failure becomes terminal once the attempt budget is spent`() {
        val failure = RequestFailure.Connection(OutcomeCertainty.NOT_SENT)
        val decision = DefaultRetryDecider.decide(failure, read, attempt = read.maxAttempts)
        assertEquals(RecoveryAction.GoBack, decision.recovery)
    }

    @Test
    fun `cancellation is never classified by this decider — it never reaches it`() {
        // No test needed here beyond documentation: RetryExecutor rethrows CancellationException
        // before FailureClassifier or RetryDecider ever see it — see RetryExecutorTest and
        // RetryControllerTest's cancellation-transparency test.
    }
}
