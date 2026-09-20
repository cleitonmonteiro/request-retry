package io.github.cleitonmonteiro.requestretry.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OrderOperationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: OrderOperationEntity): Long

    @Query("SELECT * FROM order_operations WHERE operationId = :operationId")
    suspend fun get(operationId: String): OrderOperationEntity?

    @Query(
        """
        SELECT * FROM order_operations
        WHERE state IN ('CREATED', 'SENDING', 'PENDING_CONFIRMATION', 'OUTCOME_UNKNOWN')
        ORDER BY createdAtEpochMillis ASC
        """,
    )
    suspend fun pending(): List<OrderOperationEntity>

    @Query(
        """
        UPDATE order_operations
        SET state = 'SENDING', attemptsUsed = attemptsUsed + 1, updatedAtEpochMillis = :updatedAt
        WHERE operationId = :operationId AND state NOT IN ('SUCCEEDED', 'REJECTED', 'EXPIRED')
        """,
    )
    suspend fun markSending(operationId: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE order_operations SET state = :state, updatedAtEpochMillis = :updatedAt
        WHERE operationId = :operationId AND state NOT IN ('SUCCEEDED', 'REJECTED', 'EXPIRED')
        """,
    )
    suspend fun markNonTerminal(operationId: String, state: String, updatedAt: Long): Int

    @Query(
        """
        UPDATE order_operations
        SET state = 'SUCCEEDED', orderId = :orderId, orderItem = :orderItem,
            orderTotal = :orderTotal, failureCode = NULL, updatedAtEpochMillis = :updatedAt
        WHERE operationId = :operationId AND state NOT IN ('REJECTED', 'EXPIRED')
        """,
    )
    suspend fun markSucceeded(
        operationId: String,
        orderId: String,
        orderItem: String,
        orderTotal: Double,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE order_operations
        SET state = 'REJECTED', failureCode = :failureCode, updatedAtEpochMillis = :updatedAt
        WHERE operationId = :operationId AND state NOT IN ('SUCCEEDED', 'EXPIRED')
        """,
    )
    suspend fun markRejected(operationId: String, failureCode: String?, updatedAt: Long): Int
}
