package io.github.cleitonmonteiro.requestretry.data.mapper

import io.github.cleitonmonteiro.requestretry.data.remote.OrderOperationStatusDto
import io.github.cleitonmonteiro.requestretry.domain.model.OrderOperationStatus

fun OrderOperationStatusDto.toDomain(): OrderOperationStatus = when (status) {
    "PROCESSING" -> OrderOperationStatus.Processing
    "SUCCEEDED" -> OrderOperationStatus.Succeeded(requireNotNull(order) { "SUCCEEDED operation requires order" }.toDomain())
    "REJECTED" -> OrderOperationStatus.Rejected(errorCode)
    "UNKNOWN" -> OrderOperationStatus.Unknown
    else -> throw IllegalArgumentException("Unknown operation status")
}
