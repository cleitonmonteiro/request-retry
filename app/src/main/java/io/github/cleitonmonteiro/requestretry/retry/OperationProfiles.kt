package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.model.OperationId

object OperationProfiles {
    fun foregroundRead(
        name: OperationName,
        operationId: OperationId = OperationId.random(),
        backoff: BackoffStrategy = FullJitterBackoff(),
    ): OperationSpec = OperationSpec(
        operationId = operationId,
        name = name,
        safety = OperationSafety.READ_ONLY,
        maxAttempts = 3,
        backoff = backoff,
        concurrency = ConcurrencyPolicy.CANCEL_PREVIOUS,
    )

    fun foregroundIdempotentCommand(
        name: OperationName,
        operationId: OperationId,
        backoff: BackoffStrategy = FullJitterBackoff(),
    ): OperationSpec = OperationSpec(
        operationId = operationId,
        name = name,
        safety = OperationSafety.IDEMPOTENT_COMMAND,
        maxAttempts = 3,
        backoff = backoff,
        concurrency = ConcurrencyPolicy.DROP_WHILE_RUNNING,
    )
}
