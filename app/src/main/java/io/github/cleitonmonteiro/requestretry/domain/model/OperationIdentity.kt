package io.github.cleitonmonteiro.requestretry.domain.model

import java.util.UUID

@JvmInline
/** Non-empty identifier that ties a logical operation to its status checks. */
value class OperationId(val value: String) {
    init {
        require(value.isNotBlank()) { "operationId must not be blank" }
    }

    companion object {
        fun random(): OperationId = OperationId(UUID.randomUUID().toString())
    }
}

@JvmInline
/** Non-empty key that lets the server recognize equivalent create-order submissions. */
value class IdempotencyKey(val value: String) {
    init {
        require(value.isNotBlank()) { "idempotencyKey must not be blank" }
    }

    companion object {
        fun random(): IdempotencyKey = IdempotencyKey(UUID.randomUUID().toString())
    }
}
