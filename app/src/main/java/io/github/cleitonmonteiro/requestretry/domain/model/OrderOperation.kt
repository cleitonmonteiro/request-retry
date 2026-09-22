package io.github.cleitonmonteiro.requestretry.domain.model

sealed interface OrderOperationStatus {
    data object Processing : OrderOperationStatus
    data class Succeeded(val order: Order) : OrderOperationStatus
    data class Rejected(val code: String?) : OrderOperationStatus
    data object Unknown : OrderOperationStatus
}
