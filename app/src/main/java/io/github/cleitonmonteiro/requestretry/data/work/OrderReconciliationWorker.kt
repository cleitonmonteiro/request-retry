package io.github.cleitonmonteiro.requestretry.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.cleitonmonteiro.requestretry.domain.model.OperationId
import io.github.cleitonmonteiro.requestretry.domain.model.OrderOperationStatus
import io.github.cleitonmonteiro.requestretry.domain.repository.OrderOperationStore
import io.github.cleitonmonteiro.requestretry.domain.usecase.VerifyOrderOperationUseCase

@HiltWorker
class OrderReconciliationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val operations: OrderOperationStore,
    private val verifyOperation: VerifyOrderOperationUseCase,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val rawId = inputData.getString(KEY_OPERATION_ID) ?: return Result.failure()
        val operationId = runCatching { OperationId(rawId) }.getOrElse { return Result.failure() }
        val stored = operations.get(operationId) ?: return Result.success()
        if (stored.state.isTerminal()) return Result.success()

        return try {
            when (verifyOperation(stored.operationId)) {
                is OrderOperationStatus.Succeeded,
                is OrderOperationStatus.Rejected,
                -> Result.success()
                OrderOperationStatus.Processing,
                OrderOperationStatus.Unknown,
                -> if (runAttemptCount < MAX_RECONCILIATION_ATTEMPTS) Result.retry() else Result.failure()
            }
        } catch (_: Exception) {
            if (runAttemptCount < MAX_RECONCILIATION_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private fun io.github.cleitonmonteiro.requestretry.domain.model.DurableOperationState.isTerminal(): Boolean =
        this == io.github.cleitonmonteiro.requestretry.domain.model.DurableOperationState.SUCCEEDED ||
            this == io.github.cleitonmonteiro.requestretry.domain.model.DurableOperationState.REJECTED ||
            this == io.github.cleitonmonteiro.requestretry.domain.model.DurableOperationState.EXPIRED

    companion object {
        const val KEY_OPERATION_ID = "operation_id"
        const val MAX_RECONCILIATION_ATTEMPTS = 5
    }
}
