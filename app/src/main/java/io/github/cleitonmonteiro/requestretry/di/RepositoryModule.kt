package io.github.cleitonmonteiro.requestretry.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.cleitonmonteiro.requestretry.data.repository.ItemsRepositoryImpl
import io.github.cleitonmonteiro.requestretry.data.repository.OrdersRepositoryImpl
import io.github.cleitonmonteiro.requestretry.data.repository.ProfileRepositoryImpl
import io.github.cleitonmonteiro.requestretry.domain.repository.ItemsRepository
import io.github.cleitonmonteiro.requestretry.domain.repository.OrdersRepository
import io.github.cleitonmonteiro.requestretry.domain.repository.ProfileRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    abstract fun bindOrdersRepository(impl: OrdersRepositoryImpl): OrdersRepository

    @Binds
    abstract fun bindItemsRepository(impl: ItemsRepositoryImpl): ItemsRepository
}
