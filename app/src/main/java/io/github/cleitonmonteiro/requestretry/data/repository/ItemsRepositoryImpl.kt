package io.github.cleitonmonteiro.requestretry.data.repository

import io.github.cleitonmonteiro.requestretry.data.mapper.toDomain
import io.github.cleitonmonteiro.requestretry.data.remote.ItemsRemoteDataSource
import io.github.cleitonmonteiro.requestretry.domain.model.Item
import io.github.cleitonmonteiro.requestretry.domain.repository.ItemsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ItemsRepositoryImpl @Inject constructor(
    private val remote: ItemsRemoteDataSource,
) : ItemsRepository {

    override fun getItems(): Flow<List<Item>> = flow { emit(remote.fetchItems().map { it.toDomain() }) }

    override fun sendItem(item: Item): Flow<Unit> = flow {
        remote.submitItem(item.id)
        emit(Unit)
    }
}
