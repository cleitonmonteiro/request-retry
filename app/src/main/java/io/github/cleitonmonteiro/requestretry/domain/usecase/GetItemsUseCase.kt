package io.github.cleitonmonteiro.requestretry.domain.usecase

import io.github.cleitonmonteiro.requestretry.domain.model.Item
import io.github.cleitonmonteiro.requestretry.domain.repository.ItemsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GetItemsUseCase @Inject constructor(
    private val repository: ItemsRepository,
) {
    operator fun invoke(): Flow<List<Item>> = repository.getItems()
}
