package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.model.IdempotencyKey
import io.github.cleitonmonteiro.requestretry.domain.model.OperationId
import kotlin.time.Duration.Companion.seconds

object OperationProfiles {
    fun foregroundRead(
        name: OperationName,
        operationId: OperationId = OperationId.random(),
        backoff: BackoffStrategy = FullJitterBackoff(),
    ): OperationSpec = OperationSpec(
        operationId = operationId,
        name = name,
        safety = OperationSafety.ReadOnly,
        maxAttempts = 3,
        perAttemptTimeout = 5.seconds,
        overallDeadline = 20.seconds,
        backoff = backoff,
        concurrency = ConcurrencyPolicy.CancelPrevious,
        offlineBehavior = OfflineBehavior.MANUAL,
        automaticRetry = AutomaticRetryPolicy.ENABLED,
    )

    fun foregroundIdempotentCommand(
        name: OperationName,
        operationId: OperationId,
        idempotencyKey: IdempotencyKey,
        backoff: BackoffStrategy = FullJitterBackoff(),
    ): OperationSpec = OperationSpec(
        operationId = operationId,
        name = name,
        safety = OperationSafety.IdempotentCommand(idempotencyKey),
        maxAttempts = 3,
        perAttemptTimeout = 8.seconds,
        overallDeadline = 30.seconds,
        backoff = backoff,
        concurrency = ConcurrencyPolicy.DropWhileRunning,
        offlineBehavior = OfflineBehavior.WAIT_FOR_VALIDATED_NETWORK,
        automaticRetry = AutomaticRetryPolicy.ENABLED,
    )

    fun foregroundUnsafeCommand(
        name: OperationName,
        operationId: OperationId = OperationId.random(),
        statusVerificationAvailable: Boolean = false,
        backoff: BackoffStrategy = FullJitterBackoff(),
    ): OperationSpec = OperationSpec(
        operationId = operationId,
        name = name,
        safety = OperationSafety.NonIdempotentCommand(statusVerificationAvailable),
        maxAttempts = 1,
        perAttemptTimeout = 8.seconds,
        overallDeadline = 10.seconds,
        backoff = backoff,
        concurrency = ConcurrencyPolicy.DropWhileRunning,
        offlineBehavior = OfflineBehavior.MANUAL,
        automaticRetry = AutomaticRetryPolicy.DISABLED,
    )
}
