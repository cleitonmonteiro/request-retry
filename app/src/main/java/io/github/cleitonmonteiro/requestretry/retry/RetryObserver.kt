package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure

/** How one operation's execution ended, for [RetryObserver.onFinished]. */
enum class OperationResult { SUCCEEDED, RETRYABLE, TERMINAL }

/**
 * Makes retry behavior auditable without a metrics platform — §14 of the plan. All methods have
 * a no-op default so a test or a screen that doesn't care can ignore this entirely.
 * [OperationName], not free text, keeps whatever eventually consumes this (Logcat here; a real
 * dashboard in production) safe from unbounded cardinality — see the plan's §14 privacy note.
 */
interface RetryObserver {
    fun onAttemptStarted(operation: OperationName, attempt: Int) = Unit
    fun onAttemptFailed(operation: OperationName, attempt: Int, failure: RequestFailure) = Unit
    fun onFinished(operation: OperationName, result: OperationResult) = Unit
    fun onDropped(operation: OperationName) = Unit

    companion object {
        val NoOp: RetryObserver = object : RetryObserver {}
    }
}
