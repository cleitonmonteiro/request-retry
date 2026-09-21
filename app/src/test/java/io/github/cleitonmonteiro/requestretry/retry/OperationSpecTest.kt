package io.github.cleitonmonteiro.requestretry.retry

import kotlin.time.Duration
import org.junit.Assert.assertThrows
import org.junit.Test

class OperationSpecTest {

    @Test
    fun `a read defaults to CancelPrevious`() {
        val spec = OperationSpec.read(OperationName.DEMO)
        assert(spec.concurrency == ConcurrencyPolicy.CancelPrevious)
    }

    @Test
    fun `a command defaults to DropWhileRunning`() {
        val spec = OperationSpec.command(OperationName.DEMO)
        assert(spec.concurrency == ConcurrencyPolicy.DropWhileRunning)
    }

    @Test
    fun `a command cannot use CancelPrevious`() {
        assertThrows(IllegalArgumentException::class.java) {
            OperationSpec(
                name = OperationName.DEMO,
                kind = OperationKind.COMMAND,
                concurrency = ConcurrencyPolicy.CancelPrevious,
            )
        }
    }

    @Test
    fun `maxAttempts must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            OperationSpec.read(OperationName.DEMO, maxAttempts = 0)
        }
    }

    @Test
    fun `perAttemptTimeout must be positive`() {
        assertThrows(IllegalArgumentException::class.java) {
            OperationSpec.read(OperationName.DEMO, perAttemptTimeout = Duration.ZERO)
        }
    }

    @Test
    fun `two OperationNames with the same value are equal`() {
        assert(OperationName("profile") == OperationName.PROFILE)
    }
}
