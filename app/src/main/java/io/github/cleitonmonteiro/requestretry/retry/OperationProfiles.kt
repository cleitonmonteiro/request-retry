package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.model.OperationId

/** Builds the validated resilience profiles used by foreground operations. */
object OperationProfiles {
    fun foregroundRead(
        name: OperationName,
        operationId: OperationId = OperationId.random(),
        backoff: BackoffStrategy = FullJitterBackoff(),
    ): OperationSpec = OperationSpec(
        operationId = operationId,
        name = name,
        maxAttempts = 3,
        backoff = backoff,
    )

    fun foregroundIdempotentCommand(
        name: OperationName,
        operationId: OperationId,
        backoff: BackoffStrategy = FullJitterBackoff(),
    ): OperationSpec = OperationSpec(
        operationId = operationId,
        name = name,
        maxAttempts = 3,
        backoff = backoff,
    )
}
