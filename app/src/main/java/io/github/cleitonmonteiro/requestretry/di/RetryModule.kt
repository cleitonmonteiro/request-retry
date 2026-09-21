package io.github.cleitonmonteiro.requestretry.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.cleitonmonteiro.requestretry.retry.DefaultRetryDecider
import io.github.cleitonmonteiro.requestretry.retry.ExponentialBackoffPolicy
import io.github.cleitonmonteiro.requestretry.retry.RetryDecider
import io.github.cleitonmonteiro.requestretry.retry.RetryObserver
import io.github.cleitonmonteiro.requestretry.retry.RetryPolicy
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RetryModule {

    @Provides
    @Singleton
    fun provideRetryPolicy(): RetryPolicy = ExponentialBackoffPolicy()

    @Provides
    @Singleton
    fun provideRetryDecider(): RetryDecider = DefaultRetryDecider

    @Provides
    @Singleton
    fun provideRetryObserver(): RetryObserver = RetryObserver.NoOp
}
