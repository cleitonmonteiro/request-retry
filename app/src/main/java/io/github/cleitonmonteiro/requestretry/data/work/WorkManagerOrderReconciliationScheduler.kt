package io.github.cleitonmonteiro.requestretry.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.cleitonmonteiro.requestretry.domain.model.OperationId
import io.github.cleitonmonteiro.requestretry.domain.repository.OrderReconciliationScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class WorkManagerOrderReconciliationScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : OrderReconciliationScheduler {
    override fun schedule(operationId: OperationId) {
        val request = OneTimeWorkRequestBuilder<OrderReconciliationWorker>()
            .setInputData(workDataOf(OrderReconciliationWorker.KEY_OPERATION_ID to operationId.value))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "order-reconciliation-${operationId.value}",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
