package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.OutcomeCertainty
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailureException
import java.io.IOException
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Test

class FailureClassifierTest {

    @Test
    fun `unwraps a RequestFailureException back to its RequestFailure`() {
        val failure = RequestFailure.Http(500)
        val classified = DefaultFailureClassifier.classify(RequestFailureException(failure))
        assertEquals(failure, classified)
    }

    @Test
    fun `a SerializationException becomes Protocol`() {
        val classified = DefaultFailureClassifier.classify(SerializationException("bad json"))
        assertEquals(RequestFailure.Protocol("bad json"), classified)
    }

    @Test
    fun `an unrecognized exception becomes Unknown, not Connection`() {
        // This is the fail-closed fix: a plain, unclassified exception used to be treated as a
        // generic retryable failure — it must now be terminal by default.
        val classified = DefaultFailureClassifier.classify(IOException("boom"))
        assertEquals(RequestFailure.Unknown("IOException"), classified)
    }

    @Test
    fun `cause is preserved on the exception but never surfaces in the classified RequestFailure`() {
        val cause = IllegalStateException("wire detail")
        val wrapped = RequestFailureException(RequestFailure.Connection(OutcomeCertainty.NOT_SENT), cause)

        assertEquals(cause, wrapped.cause)
        assertEquals(RequestFailure.Connection(OutcomeCertainty.NOT_SENT), DefaultFailureClassifier.classify(wrapped))
    }
}
