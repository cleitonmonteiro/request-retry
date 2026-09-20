package io.github.cleitonmonteiro.requestretry.data.remote

import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransportFailureMapperTest {
    private val now = Instant.parse("2026-09-20T12:00:00Z")

    @Test
    fun `retry-after accepts seconds and applies local cap`() {
        assertEquals(5.seconds, parseRetryAfter("5", now))
        assertEquals(30.seconds, parseRetryAfter("999", now))
    }

    @Test
    fun `retry-after accepts HTTP date`() {
        assertEquals(
            10.seconds,
            parseRetryAfter("Sun, 20 Sep 2026 12:00:10 GMT", now),
        )
    }

    @Test
    fun `retry-after rejects invalid and negative values`() {
        assertNull(parseRetryAfter("invalid", now))
        assertNull(parseRetryAfter("-1", now))
    }
}
