package io.github.cleitonmonteiro.requestretry.retry

/** Builds the validated resilience profile shared by foreground operations. */
object OperationProfiles {
    fun foreground(
        name: OperationName,
        backoff: BackoffStrategy = FullJitterBackoff(),
    ): OperationSpec = OperationSpec(
        name = name,
        maxAttempts = 3,
        backoff = backoff,
    )
}
