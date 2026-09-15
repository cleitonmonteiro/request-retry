package io.github.cleitonmonteiro.requestretry.domain.usecase

import io.github.cleitonmonteiro.requestretry.domain.model.Item
import io.github.cleitonmonteiro.requestretry.domain.repository.ItemsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class SendItemUseCase @Inject constructor(
    private val repository: ItemsRepository,
) {
    operator fun invoke(item: Item): Flow<Unit> = repository.sendItem(item)
}
