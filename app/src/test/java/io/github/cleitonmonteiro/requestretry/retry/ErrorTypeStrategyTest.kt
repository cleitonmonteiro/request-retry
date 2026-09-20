package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.RequestException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorTypeStrategyTest {
    @Test
    fun `connection and generic errors expose retry copy`() {
        val connection = ErrorTypeStrategyTestSubject.resolve(RequestException.Connection()).feedback(true)
        val generic = ErrorTypeStrategyTestSubject.resolve(RequestException.Http(500)).feedback(true)

        assertEquals("No connection", connection.title)
        assertEquals("Try again", connection.buttonLabel)
        assertEquals("Something went wrong", generic.title)
        assertEquals("Try again", generic.buttonLabel)
    }

    @Test
    fun `422 is terminal and exhausted retryable errors use go back`() {
        val terminal = ErrorTypeStrategyTestSubject.resolve(RequestException.Http(422)).feedback(false)
        val exhausted = ErrorTypeStrategyTestSubject.resolve(RequestException.Http(404)).feedback(false)

        assertEquals(ErrorType.UNPROCESSABLE_ENTITY, ErrorTypeStrategyTestSubject.resolve(RequestException.Http(422)))
        assertEquals("Go back", terminal.buttonLabel)
        assertEquals("Go back", exhausted.buttonLabel)
        assertTrue(terminal.description.isNotBlank())
        assertFalse(terminal.buttonLabel == "Try again")
    }
}

private object ErrorTypeStrategyTestSubject {
    fun resolve(error: Throwable): ErrorType = DefaultErrorTypeStrategy.classify(error)
}
