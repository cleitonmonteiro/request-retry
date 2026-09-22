package io.github.cleitonmonteiro.requestretry.domain.model

/** Authoritative status returned for a previously submitted order operation. */
sealed interface OrderOperationStatus {
    data object Processing : OrderOperationStatus
    /** Confirms the created [Order]. */
    data class Succeeded(val order: Order) : OrderOperationStatus
    /** Records a terminal backend rejection without exposing backend details to the UI. */
    data class Rejected(val code: String?) : OrderOperationStatus
    data object Unknown : OrderOperationStatus
}
