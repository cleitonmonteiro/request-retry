package io.github.cleitonmonteiro.requestretry.domain.repository

import io.github.cleitonmonteiro.requestretry.domain.model.Item
import kotlinx.coroutines.flow.Flow

interface ItemsRepository {
    fun getItems(): Flow<List<Item>>
    fun sendItem(item: Item): Flow<Unit>
}
