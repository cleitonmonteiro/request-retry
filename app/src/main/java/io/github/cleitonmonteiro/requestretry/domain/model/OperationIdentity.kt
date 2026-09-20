package io.github.cleitonmonteiro.requestretry.domain.model

import java.util.UUID

@JvmInline
value class OperationId(val value: String) {
    init {
        require(value.isNotBlank()) { "operationId must not be blank" }
    }

    companion object {
        fun random(): OperationId = OperationId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class IdempotencyKey(val value: String) {
    init {
        require(value.isNotBlank()) { "idempotencyKey must not be blank" }
    }

    companion object {
        fun random(): IdempotencyKey = IdempotencyKey(UUID.randomUUID().toString())
    }
}
