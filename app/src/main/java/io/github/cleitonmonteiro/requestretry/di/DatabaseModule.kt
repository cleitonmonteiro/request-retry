package io.github.cleitonmonteiro.requestretry.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.cleitonmonteiro.requestretry.data.local.OrderOperationDao
import io.github.cleitonmonteiro.requestretry.data.local.RequestRetryDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RequestRetryDatabase =
        Room.databaseBuilder(context, RequestRetryDatabase::class.java, "request-retry.db").build()

    @Provides
    fun provideOrderOperationDao(database: RequestRetryDatabase): OrderOperationDao = database.orderOperationDao()
}
