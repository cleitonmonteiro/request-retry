package io.github.cleitonmonteiro.requestretry.domain.usecase

import io.github.cleitonmonteiro.requestretry.domain.model.OperationId
import io.github.cleitonmonteiro.requestretry.domain.repository.OrderOperationStore
import io.github.cleitonmonteiro.requestretry.domain.repository.OrderReconciliationScheduler
import javax.inject.Inject

class ScheduleOrderReconciliationUseCase @Inject constructor(
    private val operations: OrderOperationStore,
    private val scheduler: OrderReconciliationScheduler,
) {
    suspend operator fun invoke(operationId: OperationId) {
        operations.markOutcomeUnknown(operationId)
        scheduler.schedule(operationId)
    }
}
