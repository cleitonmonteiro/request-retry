package io.github.cleitonmonteiro.requestretry.retry

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** What every screen's request is: a read (safe to repeat freely) or a mutating command. */
enum class OperationKind { READ, COMMAND }

/**
 * A stable identity for a request, used only for observability (see [RetryObserver]) — never
 * free text, so a dashboard/log filter never has to deal with unbounded label cardinality.
 */
data class OperationName(val value: String) {
    companion object {
        val PROFILE = OperationName("profile")
        val ORDERS = OperationName("orders")
        val PICKER_ITEMS = OperationName("picker_items")
        val PICKER_SEND = OperationName("picker_send")
        val CREATE_ORDER = OperationName("create_order")

        /** Used by `retry/`'s own unit tests and Compose previews — not tied to a real screen. */
        val DEMO = OperationName("demo")
    }
}

/** How a new [RetryController.load] interacts with an already-running attempt. */
sealed interface ConcurrencyPolicy {
    /** Cancelling the in-flight attempt is safe — the default for reads. */
    data object CancelPrevious : ConcurrencyPolicy

    /** The in-flight attempt keeps running; the new command is dropped — the default for commands. */
    data object DropWhileRunning : ConcurrencyPolicy
}

/**
 * Everything a [RetryController] needs to know about *one kind of request* before it can run:
 * limits and concurrency. See `planos/plano-basico-evolucao-retry.md` §8.
 */
data class OperationSpec(
    val name: OperationName,
    val kind: OperationKind,
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    val perAttemptTimeout: Duration = DEFAULT_PER_ATTEMPT_TIMEOUT,
    val concurrency: ConcurrencyPolicy =
        if (kind == OperationKind.READ) ConcurrencyPolicy.CancelPrevious else ConcurrencyPolicy.DropWhileRunning,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be positive" }
        require(perAttemptTimeout > Duration.ZERO) { "perAttemptTimeout must be positive" }
        require(kind == OperationKind.READ || concurrency != ConcurrencyPolicy.CancelPrevious) {
            "a COMMAND must not use CancelPrevious — cancelling a mutation's observation doesn't undo it"
        }
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3

        /**
         * A study-project default, deliberately generous so the demo never trips a timeout by
         * accident. A production calibration needs real latency percentiles per the plan's
         * §12.1/§10.3 — this constant is the one place that would change.
         */
        val DEFAULT_PER_ATTEMPT_TIMEOUT: Duration = 10.seconds

        fun read(
            name: OperationName,
            maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
            perAttemptTimeout: Duration = DEFAULT_PER_ATTEMPT_TIMEOUT,
        ) = OperationSpec(
            name = name,
            kind = OperationKind.READ,
            maxAttempts = maxAttempts,
            perAttemptTimeout = perAttemptTimeout,
        )

        fun command(
            name: OperationName,
            maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
            perAttemptTimeout: Duration = DEFAULT_PER_ATTEMPT_TIMEOUT,
        ) = OperationSpec(
            name = name,
            kind = OperationKind.COMMAND,
            maxAttempts = maxAttempts,
            perAttemptTimeout = perAttemptTimeout,
        )
    }
}
