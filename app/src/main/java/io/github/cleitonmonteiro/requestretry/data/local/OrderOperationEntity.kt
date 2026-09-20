package io.github.cleitonmonteiro.requestretry.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "order_operations")
data class OrderOperationEntity(
    @PrimaryKey val operationId: String,
    val idempotencyKey: String,
    val payloadFingerprint: String,
    val state: String,
    val attemptsUsed: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val orderId: String? = null,
    val orderItem: String? = null,
    val orderTotal: Double? = null,
    val failureCode: String? = null,
    val schemaVersion: Int = 1,
)
